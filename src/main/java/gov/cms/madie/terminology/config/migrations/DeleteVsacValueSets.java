package gov.cms.madie.terminology.config.migrations;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.models.MadieValueSet;
import gov.cms.madie.terminology.repositories.ValueSetExpansionRepository;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ChangeUnit(id = "delete-vsac-madie-value-sets", order = "2", author = "madie-dev")
@Slf4j
public class DeleteVsacValueSets {

  private static final String VSAC_URL_PREFIX = "http://cts.nlm.nih.gov/fhir/";

  private final List<MadieValueSet> deletedValueSets = new ArrayList<>();

  @Execution
  public void apply(
      ValueSetExpansionRepository valueSetExpansionRepository,
      FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient,
      FhirContext fhirContext,
      Environment environment) {

    String terminologyApiKey = environment.getProperty("code-system-refresh-task.terminology-key");

    List<MadieValueSet> valueSetsToCheck =
        valueSetExpansionRepository.findByUrlStartingWith(VSAC_URL_PREFIX);

    if (CollectionUtils.isEmpty(valueSetsToCheck)) {
      log.info("No MadieValueSets found with a VSAC HTTP FHIR URL prefix.");
      return;
    }

    log.info("Found {} MadieValueSets to verify against VSAC.", valueSetsToCheck.size());

    for (MadieValueSet madieValueSet : valueSetsToCheck) {
      if (StringUtils.isBlank(madieValueSet.getUrl())) {
        continue;
      }

      if (existsInVsac(
          madieValueSet.getUrl(),
          fhirTerminologyServiceWebClient,
          fhirContext,
          terminologyApiKey)) {
        log.info(
            "Deleting MadieValueSet with URL {} because it exists in VSAC.",
            madieValueSet.getUrl());
        deletedValueSets.add(madieValueSet);
        valueSetExpansionRepository.delete(madieValueSet);
      }
    }

    log.info("Deleted {} MadieValueSets that were present in VSAC.", deletedValueSets.size());
  }

  @RollbackExecution
  public void rollback(ValueSetExpansionRepository valueSetExpansionRepository) {
    if (CollectionUtils.isNotEmpty(deletedValueSets)) {
      valueSetExpansionRepository.saveAll(deletedValueSets);
    }
  }

  private boolean existsInVsac(
      String valueSetUrl,
      FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient,
      FhirContext fhirContext,
      String terminologyApiKey) {
    try {
      Map<String, String> queryParams = new java.util.HashMap<>();
      queryParams.put("url", valueSetUrl);
      queryParams.put("_count", "1");

      String response =
          fhirTerminologyServiceWebClient.searchValueSets(terminologyApiKey, queryParams);
      if (StringUtils.isBlank(response)) {
        return false;
      }

      Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, response);
      return bundle.getEntry() != null && !bundle.getEntry().isEmpty();
    } catch (Exception e) {
      log.warn("Unable to verify ValueSet {} against VSAC.", valueSetUrl, e);
      return false;
    }
  }
}
