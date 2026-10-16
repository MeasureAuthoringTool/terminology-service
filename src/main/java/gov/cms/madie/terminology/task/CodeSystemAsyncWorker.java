package gov.cms.madie.terminology.task;

import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.CodeSystemSyncService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeSystemAsyncWorker {

  private final CodeSystemSyncService codeSystemSyncService;

  @Value("${code-system-refresh-task.terminology-key}")
  private String apiKey;

  @Async
  public void runJobAsync(AtomicBoolean running) {
    try {
      log.info("Starting async code system update.");

      UmlsUser user = new UmlsUser();
      user.setApiKey(apiKey);

      codeSystemSyncService.retrieveAllCodeSystems(user);

      log.info("Code system update completed.");
    } catch (Exception ex) {
      log.error("Error during code system update", ex);
    } finally {
      running.set(false);
    }
  }
}
