package gov.cms.madie.terminology.config.migrations;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.webclient.TxTerminologyServiceWebClient;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.*;

@ChangeUnit(id = "replace-not-in-vsac-with-oid", order = "1", author = "madie-dev")
@Slf4j
public class ReplacePlaceholderWithOid {

  private final List<CodeSystem> originalCodeSystems = new ArrayList<>();

  @Execution
  public void apply(
      MongoTemplate mongoTemplate,
      TxTerminologyServiceWebClient txTerminologyServiceWebClient,
      FhirContext fhirContext,
      CodeSystemRepository codeSystemRepository) {

    List<CodeSystem> codeSystems = codeSystemRepository.findAll();

    // Store a copy of the original code system for potential rollback
    codeSystems.forEach(
        codeSystem -> {
          originalCodeSystems.add(
              codeSystem.toBuilder()
                  .version(
                      CodeSystem.Version.builder()
                          .fhirVersion(codeSystem.getVersion().getFhirVersion())
                          .vsacVersion(codeSystem.getVersion().getVsacVersion())
                          .build())
                  .build());
        });

    Map<String, String> urlToOid = new HashMap<>();
    Set<String> processedUrls = new HashSet<>();

    for (CodeSystem codeSystem : codeSystems) {
      String codeSystemUrl = codeSystem.getFullUrl();
      if (StringUtils.isNotBlank(codeSystemUrl)
          && codeSystem.getOid().contains("NOT.IN.VSAC")
          && !urlToOid.containsKey(codeSystemUrl)
          && !processedUrls.contains(codeSystemUrl)) {
        String oid =
            retrieveOidForCodeSystem(txTerminologyServiceWebClient, fhirContext, codeSystemUrl);
        if (StringUtils.isNotBlank(oid)) {
          if (oid.contains(",")) {
            log.error("Multiple OIDs found for CodeSystem with URL: {}, {}.", codeSystemUrl, oid);
            processedUrls.add(codeSystemUrl);
            continue;
          }
          urlToOid.put(codeSystemUrl, oid);
        } else {
          processedUrls.add(codeSystemUrl);
          log.warn("No OID found for CodeSystem with URL: {}", codeSystemUrl);
        }
      } else {
        log.warn(
            "Code System {} skipped. fullUrl: {}, OID: {}, already processed: {}",
            codeSystem.getId(),
            codeSystemUrl,
            codeSystem.getOid(),
            urlToOid.containsKey(codeSystemUrl) || processedUrls.contains(codeSystemUrl));
      }
    }

    // Replace "NOT.IN.VSAC" OIDs with the relevant retrieved OID.
    // Match on the fullUrl of the CodeSystem, and set inVsac to false.
    // For Code Systems that already had a valid OID, set inVsac to true.
    // For Code Systems that had "NOT.IN.VSAC##", but no OID was found, set OID to "NOT.IN.VSAC".
    for (CodeSystem codeSystem : codeSystems) {
      String codeSystemUrl = codeSystem.getFullUrl();
      if (codeSystem.getOid().contains("NOT.IN.VSAC")) {
        codeSystem.setInVsac(false);
        codeSystem.setOid("NOT.IN.VSAC");
        if (urlToOid.containsKey(codeSystemUrl)) {
          String newOid = urlToOid.get(codeSystemUrl);
          codeSystem.setOid(newOid);
          log.info(
              "Updated CodeSystem with ID {}, Full URL: {}. OID changed to {}",
              codeSystem.getId(),
              codeSystemUrl,
              newOid);
        }
      } else {
        codeSystem.setInVsac(true);
      }
    }
    codeSystemRepository.saveAll(codeSystems);
  }

  @RollbackExecution
  public void rollback(MongoTemplate mongoTemplate, CodeSystemRepository codeSystemRepository) {
    if (CollectionUtils.isEmpty(originalCodeSystems)) {
      log.warn("No original code systems found for rollback. Skipping rollback.");
      return;
    }
    codeSystemRepository.saveAll(originalCodeSystems);
  }

  private String retrieveOidForCodeSystem(
      TxTerminologyServiceWebClient txTerminologyServiceWebClient,
      FhirContext fhirContext,
      String codeSystemUrl) {
    String rawCodeSystemBundle =
        txTerminologyServiceWebClient.getCodeSystemBundle(codeSystemUrl, null);
    Optional<org.hl7.fhir.r4.model.CodeSystem> codeSystem =
        parseTxFhirCodeSystemResponse(fhirContext, rawCodeSystemBundle);
    if (codeSystem.isPresent()) {
      if (CollectionUtils.isEmpty(codeSystem.get().getIdentifier())) {
        log.warn("CodeSystem {} has no identifiers, skipping OID replacement.", codeSystemUrl);
        return null;
      }
      if (!codeSystem.get().getIdentifier().get(0).getValue().startsWith("urn:oid:")) {
        log.warn(
            "CodeSystem {} has an identifier that does not start with 'urn:oid:', "
                + "skipping OID replacement.",
            codeSystemUrl);
        return null;
      }
      return codeSystem.get().getIdentifier().get(0).getValue();
    }
    return null;
  }

  private Optional<org.hl7.fhir.r4.model.CodeSystem> parseTxFhirCodeSystemResponse(
      FhirContext context, String rawResponse) {
    if (StringUtils.isNotBlank(rawResponse)) {
      try {
        Optional<Bundle> bundle =
            Optional.of(context.newJsonParser().parseResource(Bundle.class, rawResponse));
        if (CollectionUtils.isEmpty(bundle.get().getEntry())) {
          log.warn("CodeSystem bundle has no entries, cannot extract CodeSystem.");

          return Optional.empty();
        }
        if (!(bundle.get().getEntryFirstRep().getResource()
            instanceof org.hl7.fhir.r4.model.CodeSystem)) {
          log.warn("First entry in CodeSystem bundle is not a CodeSystem resource.");
          return Optional.empty();
        }
        return Optional.of(
            (org.hl7.fhir.r4.model.CodeSystem) bundle.get().getEntryFirstRep().getResource());
      } catch (Exception e) {
        log.error("Failed to parse CodeSystem response.", e);
      }
    }
    return Optional.empty();
  }
}
