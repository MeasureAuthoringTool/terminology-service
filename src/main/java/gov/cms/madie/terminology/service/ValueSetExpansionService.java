package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.exceptions.ResourceNotFoundException;
import gov.cms.madie.terminology.exceptions.ValueSetExpansionException;
import gov.cms.madie.terminology.models.MadieValueSet;
import gov.cms.madie.terminology.repositories.ValueSetExpansionRepository;
import gov.cms.madie.terminology.util.ImplementationGuideManager;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import gov.cms.madie.terminology.webclient.TxTerminologyServiceWebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** VSES */
@Service
@Slf4j
@RequiredArgsConstructor
public class ValueSetExpansionService {

  private final ImplementationGuideManager implementationGuideManager;
  private final ValueSetExpansionRepository vseRepo;
  private final TxTerminologyServiceWebClient txTerminologyServiceWebClient;
  private final FhirContext fhirContext;
  private final FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient; // VSAC client

  @Value("${code-system-refresh-task.terminology-key}")
  String systemVsacApiKey;

  public List<String> getImplementationGuides() {
    try {
      return implementationGuideManager.getImplementationGuides();
    } catch (Exception e) {
      log.error("Unable to retrieve loaded implementation guides.", e);
    }
    return Collections.emptyList();
  }

  public List<String> getValueSetDependencies(String igName, String igVersion) {
    return implementationGuideManager.getValueSetDependencies(igName, igVersion).stream()
        .map(
            vs ->
                vs.getUrl()
                    + (StringUtils.isNotBlank(vs.getVersion()) ? "|" + vs.getVersion() : ""))
        .collect(Collectors.toList());
  }

  public List<String> getValueSetDependencies() {
    return implementationGuideManager.getValueSetDependencies().stream()
        .map(
            vs ->
                vs.getUrl()
                    + (StringUtils.isNotBlank(vs.getVersion()) ? "|" + vs.getVersion() : ""))
        .collect(Collectors.toList());
  }

  @Async
  public void updateIgValueSetDependencies(String igName, String version) {
    List<MadieValueSet> madieValueSets =
        implementationGuideManager.getValueSetDependencies(igName, version);
    log.info(
        "Found {} Value Set dependencies for IG {}, version {}.",
        madieValueSets.size(),
        igName,
        version);

    Instant now = Instant.now();
    expandValueSets(madieValueSets);
    List<MadieValueSet> expandedValueSets =
        madieValueSets.stream().filter(vs -> vs.getValueSet() != null).toList();

    log.info(
        "{} Value Set expansions retrieved for IG {}, version {}.",
        expandedValueSets.size(),
        igName,
        version);

    saveValueSetExpansions(expandedValueSets);
    log.info(
        "Update of {} Value Set expansions completed for IG {}, version {} in {} milliseconds.",
      expandedValueSets.size(),
      igName,
        version,
        Duration.between(now, Instant.now()).toMillis());
  }

  @Async
  public void updateValueSetDependencies() {
    List<MadieValueSet> madieValueSets = implementationGuideManager.getValueSetDependencies();
    log.info("Found {} Value Set dependencies.", madieValueSets.size());

    Instant now = Instant.now();
    expandValueSets(madieValueSets);
    List<MadieValueSet> expandedValueSets =
        madieValueSets.stream().filter(vs -> vs.getValueSet() != null).toList();

    log.info("{} Value Set expansions retrieved.", expandedValueSets.size());

    saveValueSetExpansions(expandedValueSets);
    log.info(
        "Update of all Value set expansions completed in {} milliseconds.",
        Duration.between(now, Instant.now()).toMillis());
  }

  private void saveValueSetExpansions(List<MadieValueSet> madieValueSets) {
    for (MadieValueSet madieValueSet : madieValueSets) {
      Optional<MadieValueSet> existingValueSet =
          vseRepo.findByUrlAndVersion(madieValueSet.getUrl(), madieValueSet.getVersion());

      if (existingValueSet.isEmpty()) {
        vseRepo.save(madieValueSet);
      } else {
        existingValueSet.get().setValueSet(madieValueSet.getValueSet());
        vseRepo.save(existingValueSet.get());
      }
    }
    log.info("Saved {} value set expansions.", madieValueSets.size());
  }

  private void expandValueSets(List<MadieValueSet> madieValueSets) {
    int failedExpansions = 0;
    log.debug("Expanding {} value sets.", madieValueSets.size());
    for (MadieValueSet madieValueSet : madieValueSets) {
      try {
        ValueSet valueSet = expandValueSet(madieValueSet);
        if (valueSet != null) {
          madieValueSet.setValueSet(fhirContext.newJsonParser().encodeResourceToString(valueSet));
          madieValueSet.setLastUpdated(Instant.now());
        } else {
          log.warn(
              "Failed to expand ValueSet {} version {}.",
              madieValueSet.getUrl(),
              madieValueSet.getVersion());
          failedExpansions++;
        }
      } catch (ValueSetExpansionException e) {
        failedExpansions++;
      }
    }
    if (failedExpansions > 0) {
      log.warn("{} expansions could not be retrieved", failedExpansions);
    }
  }

  private ValueSet expandValueSet(MadieValueSet madieValueSet) {
    Optional<ValueSet> expansion = fetchExpansionFromTxFhir(madieValueSet);
    return expansion.orElseGet(() -> fetchExpansionFromVsac(madieValueSet).orElse(null));
  }

  private Optional<ValueSet> fetchExpansionFromTxFhir(MadieValueSet madieValueSet) {
    log.debug(
        "Attempting to expand ValueSet {} version {} using TxFHIR service.",
        madieValueSet.getUrl(),
        madieValueSet.getVersion());
    try {
      String txFhirResult =
          txTerminologyServiceWebClient.getValueSetExpansion(
              madieValueSet.getUrl(), madieValueSet.getVersion());
      return parseExpansionResponse(txFhirResult);
    } catch (ResourceNotFoundException e) {
      // no-op. If not found in TxFHIR, fallback to VSAC.
      log.debug(
          "Value Set not found in TxFHIR, ValueSet {} version {}",
          madieValueSet.getUrl(),
          madieValueSet.getVersion());
    }
    return Optional.empty();
  }

  private Optional<ValueSet> fetchExpansionFromVsac(MadieValueSet madieValueSet) {
    log.debug(
        "Attempting to expand ValueSet {} version {} using VSAC FHIR Terminology Service.",
        madieValueSet.getUrl(),
        madieValueSet.getVersion());
    // TODO MAT-10003: FHIR Value Set info retrieved from Structure Definitions provide URLs, but
    // not their OIDs. This necessitates using the URL query-param when requesting
    // expansions from VSAC, which differs from our current flow.

    return Optional.empty();
  }

  private Optional<ValueSet> parseExpansionResponse(String rawResponse) {
    if (StringUtils.isNotBlank(rawResponse)) {
      try {
        return Optional.of(fhirContext.newJsonParser().parseResource(ValueSet.class, rawResponse));
      } catch (Exception e) {
        log.error("Failed to parse ValueSetExpansion response", e);
      }
    }
    return Optional.empty();
  }
}
