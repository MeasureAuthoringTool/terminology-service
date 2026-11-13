package gov.cms.madie.terminology.task;

import gov.cms.madie.terminology.models.CodeSystemJobExecutionState;
import gov.cms.madie.terminology.service.CodeSystemJobExecutionService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateCodeSystemTask {

  static final String SCHEDULED_TRIGGERED_BY = "SYSTEM";

  private final CodeSystemAsyncWorker codeSystemAsyncWorker;
  private final CodeSystemJobExecutionService codeSystemJobExecutionService;

  private final AtomicBoolean running = new AtomicBoolean(false);

  public boolean startJob(String triggeredBy) {
    if (!running.compareAndSet(false, true)) {
      log.warn("Code system update already running.");
      return false;
    }

    try {
      if (codeSystemJobExecutionService.isJobRunning()) {
        log.warn("Code system update already running according to the database.");
        running.set(false);
        return false;
      }

      CodeSystemJobExecutionState execution = codeSystemJobExecutionService.start(triggeredBy);
      codeSystemAsyncWorker.runJobAsync(running, execution);
    } catch (RuntimeException e) {
      // Release the flag, or every later run would be rejected
      running.set(false);
      throw e;
    }

    return true;
  }

  @Scheduled(cron = "${code-system-refresh-task.code-system-cron-date-time}")
  public void updateCodeSystems() {
    if (!startJob(SCHEDULED_TRIGGERED_BY)) {
      log.warn("Code system update already running. Skipping scheduled execution.");
    }
  }

  public boolean isRunning() {
    return running.get();
  }
}
