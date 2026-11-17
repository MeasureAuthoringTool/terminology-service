package gov.cms.madie.terminology.service;

import gov.cms.madie.terminology.dto.CodeSystemJobAdminStatus;
import gov.cms.madie.terminology.dto.CodeSystemJobStatus;
import gov.cms.madie.terminology.dto.CodeSystemRefreshResult;
import gov.cms.madie.terminology.models.CodeSystemJobExecutionState;
import gov.cms.madie.terminology.models.CodeSystemJobExecutionState.CodeSystemUpdate;
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
                .failures(List.of())
                .successfulCodeSystems(List.of())
                .skippedCodeSystems(List.of())
                .build());
    log.info(
        "Code system update job [{}] started by [{}]",
        execution.getId(),
        execution.getTriggeredBy());
    return execution;
  }

  public void fail(CodeSystemJobExecutionState execution) {
    finish(execution, Status.FAILED);
  }

  public void fail(CodeSystemJobExecutionState execution, Exception failure) {
    execution.setFailureMessage(failure.toString());
    fail(execution);
  }

  public void finish(CodeSystemJobExecutionState execution, CodeSystemRefreshResult result) {
    execution.setFailures(result.failures());
    execution.setSkippedCodeSystems(result.skipped());
    execution.setSuccessfulCodeSystems(
        result.updatedCodeSystems().stream()
            .map(
                codeSystem ->
                    new CodeSystemUpdate(
                        codeSystem.getId(),
                        codeSystem.getFullUrl(),
                        codeSystem.getVersion().getFhirVersion()))
            .toList());
    execution.setFailureMessage(
        result.failures().isEmpty() && result.skipped().isEmpty()
            ? null
            : result.failures().size()
                + " code system update failure(s)"
                + (result.skipped().isEmpty()
                    ? ""
                    : ", " + result.skipped().size() + " skipped code system entry/entries"));
    finish(execution, statusFor(result));
    log.info(
        "Code system update job [{}]: {} successful versions, {} failures, {} skipped",
        execution.getId(),
        result.updatedCodeSystems().size(),
        result.failures().size(),
        result.skipped().size());
  }

  private static Status statusFor(CodeSystemRefreshResult result) {
    if (result.failures().isEmpty() && result.skipped().isEmpty()) {
      return Status.COMPLETED;
    }
    return result.updatedCodeSystems().isEmpty() ? Status.FAILED : Status.COMPLETED_WITH_FAILURES;
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

  public boolean isJobRunning() {
    return codeSystemJobExecutionRepository.existsByStatus(Status.RUNNING);
  }

  /** RUNNING if any job is running, otherwise the most recently finished job */
  public Optional<CodeSystemJobAdminStatus> getLatestJobStatus() {
    if (isJobRunning()) {
      return Optional.of(CodeSystemJobAdminStatus.running());
    }
    return codeSystemJobExecutionRepository
        .findFirstByStatusInOrderByCompletedAtDesc(
            List.of(Status.COMPLETED, Status.COMPLETED_WITH_FAILURES, Status.FAILED))
        .map(CodeSystemJobAdminStatus::from);
  }

  /** The most recent job that updated code system data, even if some code systems failed */
  public Optional<CodeSystemJobStatus> getLastCompletedJob() {
    return codeSystemJobExecutionRepository
        .findFirstByStatusInOrderByCompletedAtDesc(
            List.of(Status.COMPLETED, Status.COMPLETED_WITH_FAILURES))
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
