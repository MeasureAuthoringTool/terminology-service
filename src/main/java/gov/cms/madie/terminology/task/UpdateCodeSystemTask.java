package gov.cms.madie.terminology.task;

import java.util.concurrent.atomic.AtomicBoolean;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateCodeSystemTask {

  private final FhirTerminologyService fhirTerminologyService;

  @Value("${code-system-refresh-task.terminology-key}")
  private String apiKey;

  private final AtomicBoolean running = new AtomicBoolean(false);

  @Async
  public void runJobAsync() {
    try {
      log.info("Starting async code system update.");

      UmlsUser user = new UmlsUser();
      user.setApiKey(apiKey);

      fhirTerminologyService.retrieveAllCodeSystems(user);

      log.info("Code system update completed.");
    } catch (Exception ex) {
      log.error("Error during code system update", ex);
    } finally {
      running.set(false);
    }
  }

  @Scheduled(cron = "${code-system-refresh-task.code-system-cron-date-time}")
  public void updateCodeSystems() {
    if (!running.compareAndSet(false, true)) {
      log.warn("Code system update already running. Skipping scheduled execution.");
      return;
    }

    runJobAsync();
  }

  public boolean isRunning() {
    return running.get();
  }
}
