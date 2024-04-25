package gov.cms.madie.terminology.task;

import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateCodeSystemTask {
  private final FhirTerminologyService fhirTerminologyService;

  @Value("${code-system-refresh-task.terminology-key}")
  private String apiKey;

  @Scheduled(cron = "${code-system-refresh-task.code-system-cron-date-time}") // every midnight
  public void updateCodeSystems() {
    log.info("Starting scheduled task to update code systems.");

    UmlsUser user = new UmlsUser();
    user.setApiKey(apiKey);
    List<CodeSystem> response = fhirTerminologyService.retrieveAllCodeSystems(user);
    log.info("Successfully retrieved and updated code systems for user: {}", response);
  }
}
