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

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** VSES */
@Service
@Slf4j
@RequiredArgsConstructor
public class ValueSetExpansionService {

  private static final String VSAC_BULK_EXPAND_URL =
      "https://cts.nlm.nih.gov/fhir/ValueSet/$expand?url=";

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
      } else if (!existingValueSet.get().isManuallyModified()) {
        existingValueSet.get().setValueSet(madieValueSet.getValueSet());
        vseRepo.save(existingValueSet.get());
      }
    }
    log.info("Saved {} value set expansions.", madieValueSets.size());
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
    return fetchExpansionFromVsac(madieValueSet.getUrl(), madieValueSet.getVersion(), 0);
  }

  private Optional<ValueSet> fetchExpansionFromVsac(String url, String version, int offset) {
    log.debug(
        "Attempting to expand ValueSet {} version {} using VSAC FHIR Terminology Service.",
        url,
        version);

    String urlWithVersion =
        StringUtils.isNotBlank(version) ? url + "&valueSetVersion=" + version : url;
    String urlWithOffset = offset > 0 ? urlWithVersion + "&offset=" + offset : urlWithVersion;
    String expandUrl = VSAC_BULK_EXPAND_URL + urlWithOffset;

    String vsacResponse = "";
    try {
      vsacResponse =
          fhirTerminologyServiceWebClient.fetchBatchResourcesFromVsac(
              List.of(expandUrl), systemVsacApiKey, "ValueSet");
    } catch (VsacBatchValueSetExpansionException e) {
      log.warn(
          "Unable to retrieve expansion from VSAC for Value Set {} version {}: ", url, version, e);
    }

    return StringUtils.isNotBlank(vsacResponse)
        ? parseVsacExpansionResponse(vsacResponse)
        : Optional.empty();
  }

  public MadieValueSet upsertValueSet(MadieValueSet valueSet) {
    validateValueSetPayload(valueSet.getUrl(), valueSet.getVersion(), valueSet.getValueSet());

    valueSet.setLastUpdated(Instant.now());
    valueSet.setManuallyModified(true);

    Optional<MadieValueSet> existing =
        vseRepo.findByUrlAndVersion(valueSet.getUrl(), valueSet.getVersion());
    boolean isUpdate = existing.isPresent();

    if (isUpdate) {
      valueSet.setId(existing.get().getId());
      log.info(
          "Updating existing value set with url: [{}] version: [{}]",
          valueSet.getUrl(),
          valueSet.getVersion());
    } else {
      log.info(
          "Creating new value set with url: [{}] version: [{}]",
          valueSet.getUrl(),
          valueSet.getVersion());
    }

    MadieValueSet saved = vseRepo.save(valueSet);
    log.info(
        "Successfully {} value set with id: [{}] url: [{}] version: [{}]",
        isUpdate ? "updated" : "created",
        saved.getId(),
        saved.getUrl(),
        saved.getVersion());
    return saved;
  }

  /**
   * Adds a new, manually-created value set (and its expansion) to the database.
   * Validation: The combination of URL and version is unique in the database
   *
   * The saved value set has {@code manuallyModified} set to {@code true} and {@code lastUpdated}
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
   * <p>Performs the following validations before saving:
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
    if (StringUtils.isBlank(url)) {
      throw new InvalidValueSetException("Value set URL is required.");
    }
    if (StringUtils.isBlank(valueSetJson)) {
      throw new InvalidValueSetException("Value set expansion JSON is required.");
    }

    ValueSet parsedValueSet;
    try {
      IBaseResource resource = fhirContext.newJsonParser().parseResource(valueSetJson);
      if (!(resource instanceof ValueSet)) {
        throw new InvalidValueSetException(
            "The provided expansion is not a valid FHIR ValueSet resource.");
      }
      parsedValueSet = (ValueSet) resource;
    } catch (DataFormatException e) {
      throw new InvalidValueSetException("The provided expansion could not be read as valid JSON. Please check the formatting and try again.");
    }

    if (!url.equals(parsedValueSet.getUrl())
        || (StringUtils.isNotBlank(version) && !version.equals(parsedValueSet.getVersion()))) {
      throw new InvalidValueSetException(
          "Expansion JSON URL and/or version do not match the provided values.");
    }
  }

  public void deleteValueSet(String id) {
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

  private Optional<ValueSet> parseVsacExpansionResponse(String rawResponse) {
    if (StringUtils.isBlank(rawResponse)) {
      return Optional.empty();
    }
    Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, rawResponse);
    ValueSet valueSet = (ValueSet) bundle.getEntry().get(0).getResource();

    if (valueSet != null && valueSet.getExpansion().getTotal() > 1000) {
      fetchRemainingPages(valueSet);
    }
    return Optional.ofNullable(valueSet);
  }

  private void fetchRemainingPages(ValueSet valueSet) {
    int offset = valueSet.getExpansion().getOffset();
    while (offset < valueSet.getExpansion().getTotal()) {
      fetchExpansionFromVsac(valueSet.getUrl(), valueSet.getVersion(), offset += 1000)
          .ifPresent(
              page ->
                  valueSet.getExpansion().getContains().addAll(page.getExpansion().getContains()));
    }
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
