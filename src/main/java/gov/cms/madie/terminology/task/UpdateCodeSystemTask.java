package gov.cms.madie.terminology.task;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateCodeSystemTask {

  private final CodeSystemAsyncWorker codeSystemAsyncWorker;

  private final AtomicBoolean running = new AtomicBoolean(false);

  public boolean startJob() {
    if (!running.compareAndSet(false, true)) {
      log.warn("Code system update already running.");
      return false;
    }

    codeSystemAsyncWorker.runJobAsync(running);

    return true;
  }

  @Scheduled(cron = "${code-system-refresh-task.code-system-cron-date-time}")
  public void updateCodeSystems() {
    startJob();
  }

  public boolean isRunning() {
    return running.get();
  }
}
