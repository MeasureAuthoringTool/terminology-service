package gov.cms.madie.terminology.task;

import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateCodeSystemTask {
    private final FhirTerminologyService fhirTerminologyService;


    @Value("${admin-api-key}")
    private String adminApiKey;
    @Scheduled(cron = "@midnight") // every midnight
    public void updateCodeSystems() {
        log.info("Starting scheduled task to update code systems.");

        UmlsUser user = new UmlsUser();
        user.setApiKey(adminApiKey);
        ResponseEntity<List<CodeSystem>> response = ResponseEntity.ok()
                .body(fhirTerminologyService.retrieveAllCodeSystems(user));
        log.info("Successfully retrieved and updated code systems for user: {}", response);
    }
}
