package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import gov.cms.madie.terminology.dto.ValueSetDisplayForAdmin;
import gov.cms.madie.terminology.exceptions.*;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.MadieValueSet;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.repositories.ValueSetExpansionRepository;
import gov.cms.madie.terminology.util.ImplementationGuideManager;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import gov.cms.madie.terminology.webclient.TxTerminologyServiceWebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** VSES */
@Service
@Slf4j
@RequiredArgsConstructor
public class ValueSetExpansionService {

  private static final String VSAC_URL_PREFIX = "http://cts.nlm.nih.gov/fhir/";

  private final ImplementationGuideManager implementationGuideManager;
  private final ValueSetExpansionRepository vseRepo;
  private final CodeSystemRepository csRepo;
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
    List<MadieValueSet> expandedValueSets = expandValueSets(madieValueSets);

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
  public CompletableFuture<Integer> updateValueSetDependencies() {
    List<MadieValueSet> madieValueSets = implementationGuideManager.getValueSetDependencies();
    log.info("Found {} Value Set dependencies.", madieValueSets.size());

    Instant now = Instant.now();
    List<MadieValueSet> expandedValueSets = expandValueSets(madieValueSets);

    log.info("{} Value Set expansions retrieved.", expandedValueSets.size());

    int savedValueSetCount = saveValueSetExpansions(expandedValueSets);
    log.info(
        "Update of all Value set expansions completed in {} milliseconds.",
        Duration.between(now, Instant.now()).toMillis());
    return CompletableFuture.completedFuture(savedValueSetCount);
  }

  private int saveValueSetExpansions(List<MadieValueSet> madieValueSets) {
    int savedValueSetCount = 0;
    for (MadieValueSet madieValueSet : madieValueSets) {
      Optional<MadieValueSet> existingValueSet =
          vseRepo.findByUrlAndVersion(madieValueSet.getUrl(), madieValueSet.getVersion());

      if (existingValueSet.isEmpty()) {
        vseRepo.save(madieValueSet);
        savedValueSetCount++;
      } else if (!existingValueSet.get().isManuallyModified()) {
        existingValueSet.get().setValueSet(madieValueSet.getValueSet());
        vseRepo.save(existingValueSet.get());
        savedValueSetCount++;
      }
    }
    log.info("Saved {} value set expansions.", savedValueSetCount);
    return savedValueSetCount;
  }

  private ValueSetDisplayForAdmin convertToDisplayDto(MadieValueSet valueSet) {
    return ValueSetDisplayForAdmin.builder()
        .id(valueSet.getId())
        .url(valueSet.getUrl())
        .version(valueSet.getVersion())
        .lastUpdated(valueSet.getLastUpdated())
        .manuallyModified(valueSet.isManuallyModified())
        .valueSet(valueSet.getValueSet())
        .build();
  }

  public Page<ValueSetDisplayForAdmin> getValueSets(Pageable pageable, String searchTerm) {

    Page<MadieValueSet> page;

    if (StringUtils.isBlank(searchTerm)) {
      page = vseRepo.findAll(pageable);
    } else {
      page = vseRepo.findByUrlContainingIgnoreCase(searchTerm, pageable);
    }

    return page.map(this::convertToDisplayDto);
  }

  /** Removes VSAC-owned VSES records and returns only fresh TX.FHIR expansions. */
  private List<MadieValueSet> expandValueSets(List<MadieValueSet> madieValueSets) {
    List<MadieValueSet> expandedValueSets = new ArrayList<>();
    int failedExpansions = 0;
    log.debug("Expanding {} value sets.", madieValueSets.size());
    for (MadieValueSet madieValueSet : madieValueSets) {
      if (madieValueSet.getUrl().startsWith(VSAC_URL_PREFIX)
          && shouldSkipVsacValueSet(madieValueSet)) {
        continue;
      }
      try {
        ValueSet valueSet = fetchExpansionFromTxFhir(madieValueSet).orElse(null);
        if (valueSet != null) {
          expandedValueSets.add(
              madieValueSet.toBuilder()
                  .valueSet(fhirContext.newJsonParser().encodeResourceToString(valueSet))
                  .lastUpdated(Instant.now())
                  .build());
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
    return expandedValueSets;
  }

  /**
   * Checks VSAC availability by URL and, when nonblank, version for a VSAC-prefixed dependency.
   * VSAC is the source of truth: a confirmed match deletes any VSES record with the dependency's
   * exact URL/version, including manually modified records, and skips TX.FHIR processing.
   *
   * <p>Confirmed absence permits TX.FHIR fallback. Failed or invalid VSAC lookups are logged and
   * skip processing without deleting existing VSES data.
   *
   * @param madieValueSet the VSAC-prefixed dependency to check
   * @return {@code false} only when VSAC confirms absence; {@code true} otherwise
   */
  private boolean shouldSkipVsacValueSet(MadieValueSet madieValueSet) {
    UriComponentsBuilder search =
        UriComponentsBuilder.fromPath("/ValueSet")
            .queryParam("url", "{url}")
            .queryParam("_summary", "count");
    Map<String, String> parameters = new HashMap<>();
    parameters.put("url", madieValueSet.getUrl());
    if (StringUtils.isNotBlank(madieValueSet.getVersion())) {
      search.queryParam("version", "{version}");
      parameters.put("version", madieValueSet.getVersion());
    }
    try {
      String response =
          fhirTerminologyServiceWebClient.fetchResourceFromVsac(
              search.encode().buildAndExpand(parameters).toUriString(),
              systemVsacApiKey,
              "ValueSet");
      if (StringUtils.isBlank(response)) {
        throw new DataFormatException("VSAC existence search returned an empty response.");
      }
      Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, response);
      if (bundle.getType() != Bundle.BundleType.SEARCHSET
          || !bundle.hasTotal()
          || bundle.getTotal() < 0) {
        throw new DataFormatException("VSAC existence search did not return a valid search total.");
      }
      if (bundle.getTotal() == 0) {
        return false;
      }
      vseRepo
          .findByUrlAndVersion(madieValueSet.getUrl(), madieValueSet.getVersion())
          .ifPresent(
              existing -> {
                vseRepo.deleteById(existing.getId());
                log.info(
                    "Deleted VSES ValueSet {} version {} with id {} because it exists in VSAC.",
                    existing.getUrl(),
                    existing.getVersion(),
                    existing.getId());
              });
      log.info(
          "Skipping ValueSet {} version {} because it exists in VSAC.",
          madieValueSet.getUrl(),
          madieValueSet.getVersion());
    } catch (WebClientException | DataFormatException e) {
      log.warn(
          "Skipping ValueSet {} version {}: unable to confirm VSAC existence.",
          madieValueSet.getUrl(),
          madieValueSet.getVersion(),
          e);
    }
    return true;
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
      log.debug(
          "Value Set not found in TxFHIR, ValueSet {} version {}",
          madieValueSet.getUrl(),
          madieValueSet.getVersion());
    }
    return Optional.empty();
  }

  public MadieValueSet upsertValueSet(MadieValueSet valueSet) {
    validateValueSetPayload(valueSet.getUrl(), valueSet.getVersion(), valueSet.getValueSet());

    valueSet.setLastUpdated(Instant.now());
    valueSet.setManuallyModified(true);

    MadieValueSet existingValueSet =
        vseRepo
            .findByUrlAndVersion(valueSet.getUrl(), valueSet.getVersion())
            .orElseThrow(
                () ->
                    new ValueSetNotFoundException(
                        String.format(
                            "ValueSet not found for url: %s version: %s",
                            valueSet.getUrl(), valueSet.getVersion())));

    valueSet.setId(existingValueSet.getId());
    log.info(
        "Updating existing value set with url: [{}] version: [{}]",
        valueSet.getUrl(),
        valueSet.getVersion());

    MadieValueSet saved = vseRepo.save(valueSet);
    log.info(
        "Successfully updated value set with id: [{}] url: [{}] version: [{}]",
        saved.getId(),
        saved.getUrl(),
        saved.getVersion());
    return saved;
  }

  /**
   * Adds a new, manually-created value set (and its expansion) to the database. Validation: The
   * combination of URL and version is unique in the database
   *
   * <p>The saved value set has {@code manuallyModified} set to {@code true} and {@code lastUpdated}
   * set to the current time.
   *
   * @param request the admin-supplied value set details
   * @return the persisted {@link MadieValueSet}
   */
  public MadieValueSet addValueSet(ValueSetDisplayForAdmin request) {
    String url = request.getUrl();
    String version = request.getVersion();
    String valueSetJson = request.getValueSet();

    validateValueSetPayload(url, version, valueSetJson);

    if (vseRepo.findByUrlAndVersion(url, version).isPresent()) {
      throw new DuplicateValueSetException(
          String.format(
              "A value set already exists for url: [%s] version: [%s]",
              url, StringUtils.defaultString(version)));
    }

    MadieValueSet valueSet =
        MadieValueSet.builder()
            .url(url)
            .version(version)
            .valueSet(valueSetJson)
            .manuallyModified(true)
            .lastUpdated(Instant.now())
            .build();

    MadieValueSet saved = vseRepo.save(valueSet);
    log.info(
        "Successfully added new value set with id: [{}] url: [{}] version: [{}]",
        saved.getId(),
        saved.getUrl(),
        saved.getVersion());
    return saved;
  }

  /**
   * Performs the following validations before saving:
   *
   * <ul>
   *   <li>URL and value set expansion JSON are provided
   *   <li>The expansion JSON is syntactically valid JSON
   *   <li>The JSON represents a FHIR ValueSet resource
   *   <li>The URL inside the JSON matches the provided URL
   *   <li>When a version is provided, the version inside the JSON matches it
   * </ul>
   *
   * @throws InvalidValueSetException when any validation fails
   */
  private void validateValueSetPayload(String url, String version, String valueSetJson) {
    ValueSet parsedValueSet;
    try {
      IBaseResource resource = fhirContext.newJsonParser().parseResource(valueSetJson);
      if (!(resource instanceof ValueSet)) {
        throw new InvalidValueSetException(
            "The provided expansion is not a valid FHIR ValueSet resource.");
      }
      parsedValueSet = (ValueSet) resource;
    } catch (DataFormatException e) {
      throw new InvalidValueSetException(
          "The provided expansion could not be read as valid JSON. "
              + "Please check the formatting and try again.");
    }

    if (!url.equals(parsedValueSet.getUrl())
        || StringUtils.isNotBlank(version) && !version.equals(parsedValueSet.getVersion())) {
      throw new InvalidValueSetException(
          "Expansion JSON URL and/or version do not match the provided values.");
    }
  }

  public MadieValueSet deleteValueSet(String id) {
    MadieValueSet existing =
        vseRepo
            .findById(id)
            .orElseThrow(() -> new ValueSetNotFoundException("ValueSet not found for id: " + id));
    log.info(
        "Deleting value set with id: [{}] url: [{}] version: [{}]",
        id,
        existing.getUrl(),
        existing.getVersion());
    vseRepo.deleteById(id);
    log.info("Successfully deleted value set with id: [{}]", id);
    return existing;
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

  public MadieValueSet getValueSet(String url, String version) {
    MadieValueSet valueSet =
        vseRepo
            .findByUrlAndVersionOrNull(url, version)
            .orElseThrow(
                () ->
                    new ValueSetNotFoundException(
                        String.format("ValueSet not found for url: %s version: %s", url, version)));
    log.info("Successfully retrieved ValueSet with url: [{}] version: [{}]", url, version);
    return valueSet;
  }

  public List<CodeSystem> getCodeSystem(String url, Integer count) {
    List<CodeSystem> codeSystems =
        csRepo.findAllByFullUrl(
            url, count == null || count <= 0 ? Limit.unlimited() : Limit.of(count));
    if (codeSystems.isEmpty()) {
      throw new CodeSystemNotFoundException(String.format("CodeSystem not found for url: %s", url));
    }
    return codeSystems;
  }
}
