package gov.cms.madie.terminology.task;

import gov.cms.madie.terminology.models.CodeSystemJobExecutionState;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.CodeSystemJobExecutionService;
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
  private final CodeSystemJobExecutionService codeSystemJobExecutionService;

  @Value("${code-system-refresh-task.terminology-key}")
  private String apiKey;

  @Async
  public void runJobAsync(AtomicBoolean running, CodeSystemJobExecutionState execution) {
    try {
      log.info("Starting async code system update.");

      UmlsUser user = new UmlsUser();
      user.setApiKey(apiKey);

      var result = codeSystemSyncService.remoteRetrieveAllCodeSystems(user);
      codeSystemJobExecutionService.finish(execution, result);

      log.info(
          "Code system update finished: {} successful versions, {} failures.",
          result.updatedCodeSystems().size(),
          result.failures().size());
    } catch (Exception ex) {
      log.error("Error during code system update", ex);
      codeSystemJobExecutionService.fail(execution, ex);
    } finally {
      running.set(false);
    }
  }
}
