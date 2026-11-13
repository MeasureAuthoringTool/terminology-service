package gov.cms.madie.terminology.service;

import gov.cms.madie.terminology.dto.CodeSystemJobStatus;
import gov.cms.madie.terminology.models.CodeSystemJobExecutionState;
import gov.cms.madie.terminology.models.CodeSystemJobExecutionState.Status;
import gov.cms.madie.terminology.repositories.CodeSystemJobExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CodeSystemJobExecutionService {

  private final CodeSystemJobExecutionRepository codeSystemJobExecutionRepository;

  // Set before Mongock runs, so a job started by a migration isn't treated as interrupted
  private final Instant serviceStartedAt = Instant.now();

  public CodeSystemJobExecutionState start(String triggeredBy) {
    CodeSystemJobExecutionState execution =
        codeSystemJobExecutionRepository.save(
            CodeSystemJobExecutionState.builder()
                .status(Status.RUNNING)
                .triggeredBy(triggeredBy)
                .startedAt(Instant.now())
                .build());
    log.info(
        "Code system update job [{}] started by [{}]",
        execution.getId(),
        execution.getTriggeredBy());
    return execution;
  }

  public void complete(CodeSystemJobExecutionState execution) {
    finish(execution, Status.COMPLETED);
  }

  public void fail(CodeSystemJobExecutionState execution) {
    finish(execution, Status.FAILED);
  }

  private void finish(CodeSystemJobExecutionState execution, Status status) {
    Instant completedAt = Instant.now();
    execution.setStatus(status);
    execution.setCompletedAt(completedAt);
    execution.setDurationMs(Duration.between(execution.getStartedAt(), completedAt).toMillis());
    codeSystemJobExecutionRepository.save(execution);
    log.info(
        "Code system update job [{}] {} after {} ms",
        execution.getId(),
        status,
        execution.getDurationMs());
  }

  /** RUNNING if any job is running, otherwise the most recently finished job */
  public Optional<CodeSystemJobStatus> getLatestJobStatus() {
    if (codeSystemJobExecutionRepository.existsByStatus(Status.RUNNING)) {
      return Optional.of(new CodeSystemJobStatus(Status.RUNNING, null));
    }
    return codeSystemJobExecutionRepository
        .findFirstByStatusInOrderByCompletedAtDesc(List.of(Status.COMPLETED, Status.FAILED))
        .map(CodeSystemJobStatus::from);
  }

  public Optional<CodeSystemJobStatus> getLastCompletedJob() {
    return codeSystemJobExecutionRepository
        .findFirstByStatusOrderByCompletedAtDesc(Status.COMPLETED)
        .map(CodeSystemJobStatus::from);
  }

  /** Marks jobs left RUNNING by a restart as FAILED. Never throws, so it can't block startup */
  @EventListener(ApplicationReadyEvent.class)
  public void failInterruptedJobs() {
    try {
      List<CodeSystemJobExecutionState> interrupted =
          codeSystemJobExecutionRepository.findByStatusAndStartedAtBefore(
              Status.RUNNING, serviceStartedAt);
      interrupted.forEach(
          execution -> {
            log.warn(
                "Code system update job [{}] started at [{}] was interrupted. Marking it as failed",
                execution.getId(),
                execution.getStartedAt());
            fail(execution);
          });
    } catch (Exception e) {
      log.error("Unable to mark interrupted code system update jobs as failed", e);
    }
  }
}
