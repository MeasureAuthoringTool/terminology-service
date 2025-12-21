package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.models.mapping.CodeSystemEntry;
import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.*;
import gov.cms.madie.terminology.exceptions.VsacParseBatchValueSetExpansionException;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.util.TerminologyServiceUtil;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FhirTerminologyService {
  private final FhirContext fhirContext;
  private final FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;
  private final MappingService mappingService;
  private final CodeSystemRepository codeSystemRepository;
  private final VsacService vsacService;

  @Cacheable("manifest-list")
  public List<ManifestExpansion> getManifests(UmlsUser umlsUser) {
    IParser parser = fhirContext.newJsonParser();
    String responseString = fhirTerminologyServiceWebClient.getManifestBundle(umlsUser.getApiKey());
    Bundle manifestBundle = parser.parseResource(Bundle.class, responseString);
    var manifestOptions = new ArrayList<ManifestExpansion>();
    manifestBundle
        .getEntry()
        .forEach(
            entry -> {
              Library library = (Library) entry.getResource();
              manifestOptions.add(
                  ManifestExpansion.builder()
                      .id(library.getIdPart())
                      .fullUrl(entry.getFullUrl())
                      .title(library.getTitle())
                      .build());
            });
    return manifestOptions;
  }

  // spin off requests based on values provided in the expansion
  public List<ValueSet> requestAllValueSetsExpansions(
      List<ValueSet> allValueSets, String apiKey, ValueSetsSearchCriteria valueSetsSearchCriteria) {
    IParser parser = fhirContext.newJsonParser();
    String resource =
        fhirTerminologyServiceWebClient.getValueSetResources(apiKey, valueSetsSearchCriteria);

    Bundle bundleResource = parser.parseResource(Bundle.class, resource);
    List<Bundle.BundleEntryComponent> bundleEntryComponents = bundleResource.getEntry();

    for (int i = 0; i < bundleEntryComponents.size(); i++) {
      Bundle.BundleEntryComponent bundleEntryComponent = bundleEntryComponents.get(i);

      ValueSet valueSetResource = (ValueSet) bundleEntryComponent.getResource();

      if (valueSetResource == null) {
        OperationOutcome outcome =
            (OperationOutcome) bundleEntryComponent.getResponse().getOutcome();
        log.debug(
            "VSAC did not return BundleEntryComponent containing ValueSet with ValueSetParams "
                + "[{}] (at index [{}]) after successfully calling batch value set expansion with "
                + "ValueSetsSearchCriteria [{}] ",
            valueSetsSearchCriteria.getValueSetParams().get(i),
            i,
            valueSetsSearchCriteria);

        throw new VsacParseBatchValueSetExpansionException(
            "Failed to fetch VSAC value set expansions",
            outcome,
            valueSetsSearchCriteria.getManifestExpansion() != null
                ? valueSetsSearchCriteria.getManifestExpansion().getFullUrl()
                : null,
            valueSetsSearchCriteria.getValueSetParams().get(i).getOid());
      }
      var expansion = valueSetResource.getExpansion();
      var total = expansion.getTotal(); // total valuesets

      log.info(
          "vs total [{}] count: [{}] offset: [{}], oid: [{}]",
          total,
          expansion.getContains().size(),
          expansion.getOffset(),
          valueSetResource.getId());
      ValueSet existingValueSet =
          allValueSets.stream()
              .filter(vs -> vs.getIdPart().equals(valueSetResource.getIdPart()))
              .findFirst()
              .orElse(null);
      if (existingValueSet == null) {
        allValueSets.add(valueSetResource);
      } else {
        List<ValueSet.ValueSetExpansionContainsComponent> dedupeContains =
            valueSetResource.getExpansion().getContains().stream()
                .filter(
                    newEntry ->
                        !containsEntry(existingValueSet.getExpansion().getContains(), newEntry))
                .collect(Collectors.toList());

        existingValueSet.getExpansion().getContains().addAll(dedupeContains);
      }
      if (expansion.getOffset() + expansion.getContains().size() < total) {
        ValueSetsSearchCriteria newSearch =
            buildValueSetsSearchCriteria(valueSetsSearchCriteria, valueSetResource);

        requestAllValueSetsExpansions(allValueSets, apiKey, newSearch);
      }
    }

    return allValueSets;
  }

  private boolean containsEntry(
      List<ValueSet.ValueSetExpansionContainsComponent> existingEntries,
      ValueSet.ValueSetExpansionContainsComponent newEntry) {
    return existingEntries.stream()
        .anyMatch(
            existing ->
                Objects.equals(existing.getCode(), newEntry.getCode())
                    && Objects.equals(existing.getSystem(), newEntry.getSystem())
                    && Objects.equals(existing.getVersion(), newEntry.getVersion()));
  }

  private ValueSetsSearchCriteria buildValueSetsSearchCriteria(
      ValueSetsSearchCriteria searchCriteria, ValueSet valueSetResource) {
    ValueSetsSearchCriteria newSearch =
        ValueSetsSearchCriteria.builder()
            .includeDraft(searchCriteria.getIncludeDraft())
            .manifestExpansion(searchCriteria.getManifestExpansion())
            .activeOnly(searchCriteria.getActiveOnly())
            .build();

    ValueSetsSearchCriteria.ValueSetParams newParams =
        ValueSetsSearchCriteria.ValueSetParams.builder()
            .count(1000)
            .offset(valueSetResource.getExpansion().getOffset() + 1000)
            .oid(valueSetResource.getIdentifier().get(0).getValue().replace("urn:oid:", ""))
            .build();
    newSearch.setValueSetParams(List.of(newParams));
    return newSearch;
  }

  public List<ValueSet> getValueSetsExpansion(
      ValueSetsSearchCriteria valueSetsSearchCriteria, UmlsUser umlsUser) {
    if (valueSetsSearchCriteria == null
        || CollectionUtils.isEmpty(valueSetsSearchCriteria.getValueSetParams())) {
      return Collections.emptyList();
    }

    valueSetsSearchCriteria.setValueSetParams(
        valueSetsSearchCriteria.getValueSetParams().stream()
            .map(
                vsParam -> {
                  vsParam.setCount(1000);
                  vsParam.setOffset(0);
                  return vsParam;
                })
            .collect(Collectors.toList()));
    return requestAllValueSetsExpansions(
        new ArrayList<>(), umlsUser.getApiKey(), valueSetsSearchCriteria);
  }

  public List<QdmValueSet> getValueSetsExpansionsForQdm(
      ValueSetsSearchCriteria valueSetsSearchCriteria, UmlsUser umlsUser) {
    List<CodeSystemEntry> codeSystemEntries = mappingService.getCodeSystemEntries();
    List<ValueSet> fhirValueSets = getValueSetsExpansion(valueSetsSearchCriteria, umlsUser);

    return fhirValueSets.stream()
        .map(
            fhirValueSet -> {
              List<QdmValueSet.Concept> concepts =
                  getValueSetConcepts(fhirValueSet, codeSystemEntries, "QDM");
              return QdmValueSet.builder()
                  .oid(fhirValueSet.getIdPart())
                  .displayName(fhirValueSet.getName())
                  .version(fhirValueSet.getVersion())
                  .concepts(concepts)
                  .build();
            })
        .toList();
  }

  /**
   * @param valueSet resource from FHIR Terminology Server
   * @param codeSystemEntries Code Systems mapping document
   * @return a List of QdmValueSet.Concept if the valueSet has expansions. Also, valueSet resource
   *     only has CodeSystem URL info for its expansions, so we use codeSystem entries to find its
   *     appropriate OID If associated OID is not found, we return the original FHIR URL of the code
   *     system.
   */
  private List<QdmValueSet.Concept> getValueSetConcepts(
      ValueSet valueSet, List<CodeSystemEntry> codeSystemEntries, String model) {
    if (valueSet.getExpansion() != null && valueSet.getExpansion().getTotal() > 0) {
      return valueSet.getExpansion().getContains().stream()
          .map(
              concept -> {
                Optional<CodeSystemEntry> optionalCodeSystemEntry =
                    TerminologyServiceUtil.getCodeSystemEntry(
                        codeSystemEntries, concept.getSystem(), "FHIR");
                String codeSystemOid = concept.getSystem();
                String codeSystem = concept.getSystem();
                String codeSystemVersion = concept.getVersion();
                if (optionalCodeSystemEntry.isPresent()) {
                  codeSystemOid = optionalCodeSystemEntry.get().getOid();
                  codeSystem = optionalCodeSystemEntry.get().getName();
                  codeSystemVersion =
                      TerminologyServiceUtil.getCodeSystemVersion(
                          optionalCodeSystemEntry.get(), concept.getVersion(), model);
                }
                return QdmValueSet.Concept.builder()
                    .code(concept.getCode())
                    .displayName(concept.getDisplay())
                    .codeSystemName(codeSystem)
                    .codeSystemVersion(codeSystemVersion)
                    .codeSystemOid(TerminologyServiceUtil.removeUrnOidSubString(codeSystemOid))
                    .build();
              })
          .toList();
    }
    log.info("No Expansion codes are found for the valueSet oid : [{}]", valueSet.getId());
    return List.of();
  }

  public ValueSetSearchResult searchValueSets(String apiKey, Map<String, String> queryParams) {
    IParser parser = fhirContext.newJsonParser();
    String responseString = fhirTerminologyServiceWebClient.searchValueSets(apiKey, queryParams);
    Bundle bundle = parser.parseResource(Bundle.class, responseString);
    List<ValueSetForSearch> valueSetList = new ArrayList<>();
    bundle
        .getEntry()
        .forEach(
            entry -> {
              traverseValueSet(entry, valueSetList);
            });
    //  if there's a next link we want to hit it, and append the results until we're out of results
    var links = bundle.getLink();
    links.forEach(
        (l) -> {
          if (l.getRelation().equals("next")) {
            recursiveRequestValueSets(valueSetList, apiKey, l.getUrl());
          }
        });

    return ValueSetSearchResult.builder()
        .valueSets(valueSetList)
        .resultBundle(responseString)
        .build();
  }

  public void recursiveRequestValueSets(
      List<ValueSetForSearch> allValueSets, String apiKey, String uriString) {
    String httpsString = uriString.replaceFirst("http", "https");
    log.info(
        "uri we're going to hit is[{}]",
        httpsString); // vsac gives us http, we want https or it fails
    IParser parser = fhirContext.newJsonParser();
    String responseString =
        fhirTerminologyServiceWebClient.fetchResourceFromVsac(httpsString, apiKey, "bundle");
    Bundle bundle = parser.parseResource(Bundle.class, responseString);
    List<ValueSetForSearch> valueSetListPage = new ArrayList<>();
    bundle
        .getEntry()
        .forEach(
            entry -> {
              traverseValueSet(entry, valueSetListPage);
            });
    allValueSets.addAll(valueSetListPage);
    var links = bundle.getLink();
    links.forEach(
        (l) -> {
          if (l.getRelation().equals("next")) {
            recursiveRequestValueSets(allValueSets, apiKey, l.getUrl());
          }
        });
  }

  private void traverseValueSet(
      Bundle.BundleEntryComponent entry, List<ValueSetForSearch> valueSetList) {
    Resource resource = entry.getResource();
    ValueSet vs = (ValueSet) resource;
    if (resource instanceof ValueSet) {
      String oid = "";
      for (Identifier identifier : ((ValueSet) resource).getIdentifier()) {
        if (identifier.getValue() != null && !identifier.getValue().isEmpty()) {
          oid = identifier.getValue();
        }
      }
      ValueSetForSearch valueSet =
          ValueSetForSearch.builder()
              .title(vs.getTitle())
              .author(
                  Optional.ofNullable(
                          vs.getExtensionByUrl(
                              "http://hl7.org/fhir/StructureDefinition/valueset-author"))
                      .map(extension -> String.valueOf(extension.getValue()))
                      .orElse(""))
              .name(vs.getName())
              .composedOf(
                  vs.getCompose().getInclude().stream()
                      .map(x -> x.getSystem())
                      .collect(Collectors.joining(",")))
              .effectiveDate(
                  Optional.ofNullable(
                          vs.getExtensionByUrl(
                              "http://hl7.org/fhir/StructureDefinition/valueset-effectiveDate"))
                      .map(extension -> String.valueOf(extension.getValue()))
                      .orElse(""))
              .lastReviewDate(
                  Optional.ofNullable(
                          vs.getExtensionByUrl(
                              "http://hl7.org/fhir/StructureDefinition/resource-lastReviewDate"))
                      .map(extension -> String.valueOf(extension.getValue()))
                      .orElse(""))
              .lastUpdated(vs.getMeta().getLastUpdated().toString())
              .url(vs.getUrl())
              .version(vs.getVersion())
              .status(vs.getStatus())
              .publisher(vs.getPublisher())
              .purpose(vs.getPurpose())
              .steward(vs.getPublisher())
              .oid(oid)
              .build();
      valueSetList.add(valueSet);
    }
  }

  public List<CodeSystem> getAllCodeSystems() {
    // remove items that are marked as not present in vsac to cut expense
    List<CodeSystemEntry> codeSystemMappingEntries =
        mappingService.getCodeSystemEntries().stream()
            .filter(codeSystemEntry -> !codeSystemEntry.getOid().contains("NOT.IN.VSAC"))
            .toList();
    List<CodeSystem> codeSystems = codeSystemRepository.findAll();
    codeSystems.forEach(
        codeSystem -> {
          Optional<CodeSystemEntry> matchingEntry =
              codeSystemMappingEntries.stream()
                  .filter(entry -> entry.getOid().equals(codeSystem.getOid()))
                  .findFirst();
          if (matchingEntry.isPresent()) {
            matchingEntry
                .get()
                .getVersions()
                .forEach(
                    version -> {
                      // We use fhir url to interact with VSAC FHIR Term Service.
                      // Goal here is to look for fhir version, then give users
                      // viewing QDM measures a display version that looks like
                      // svs vsac because that's what they expect.
                      if (version.getFhir().equals(codeSystem.getVersion())
                          && version.getVsac() != null) {
                        codeSystem.setQdmDisplayVersion(version.getVsac());
                        log.debug(
                            "CodeSystem title {} , version: {} was found in mapping document",
                            codeSystem.getTitle(),
                            codeSystem.getVersion());
                      }
                    });
          } else {
            // it was not found, we log that it's not located within vsac.
            log.debug(
                "CodeSystem title {} , version: {} was NOT found in mapping document",
                codeSystem.getTitle(),
                codeSystem.getName());
          }
        });
    return codeSystems;
  }

  public List<CodeSystem> retrieveAllCodeSystems(UmlsUser umlsUser) {
    List<CodeSystem> allCodeSystems = new ArrayList<>();

    recursiveRetrieveCodeSystems(umlsUser, 0, 50, allCodeSystems);
    // Once we have all codeSystems, update DB using mongo
    updateOrInsertAllCodeSystems(allCodeSystems);
    return allCodeSystems;
  }

  public Code retrieveCode(String codeName, String codeSystemName, String version, String apiKey) {
    if (StringUtils.isEmpty(codeName)
        || StringUtils.isEmpty(codeSystemName)
        || StringUtils.isEmpty(version)) {
      return null;
    }

    CodeSystem codeSystem =
        codeSystemRepository.findByNameAndVersion(codeSystemName, version).orElse(null);
    if (codeSystem == null) {
      return null;
    }

    List<CodeSystemEntry> codeSystemEntries = mappingService.getCodeSystemEntries();
    Optional<CodeSystemEntry.Version> codeSystemVersion =
        getCodeSystemEntryVersion(version, codeSystem.getOid(), codeSystemEntries);

    if (codeSystemVersion.isPresent()) {
      String vsacVersion = codeSystemVersion.get().getVsac();
      String fhirVersion = codeSystemVersion.get().getFhir();

      return retrieveCodes(codeName, codeSystemName, vsacVersion, fhirVersion, codeSystem, apiKey);
    }
    return null;
  }

  private void recursiveRetrieveCodeSystems(
      UmlsUser umlsUser, Integer offset, Integer count, List<CodeSystem> allCodeSystems) {
    log.info("requesting page offset: {} count: {}", offset, count);
    Bundle codeSystemBundle = retrieveCodeSystemsPage(umlsUser, offset, count);
    List<CodeSystem> codeSystemsPage = new ArrayList<>(); // build small list
    codeSystemBundle
        .getEntry()
        .forEach(
            entry -> {
              var codeSystem = (org.hl7.fhir.r4.model.CodeSystem) entry.getResource();
              codeSystemsPage.add(
                  CodeSystem.builder()
                      .id(codeSystem.getTitle() + codeSystem.getVersion())
                      .fullUrl(codeSystem.getUrl())
                      .title(codeSystem.getTitle())
                      .name(codeSystem.getName())
                      .version(codeSystem.getVersion())
                      .versionId(codeSystem.getMeta().getVersionId())
                      .oid(parseOidFromIdentifier(codeSystem.getIdentifier()))
                      .lastUpdated(Instant.now())
                      .lastUpdatedUpstream(codeSystem.getMeta().getLastUpdated())
                      .build());
            });
    allCodeSystems.addAll(codeSystemsPage); // update big list
    var links = codeSystemBundle.getLink();
    links.forEach(
        (l) -> {
          if (l.getRelation().equals("next")) {
            // if next, call self and continue until fail.
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(l.getUrl());
            String newOffset = builder.build().getQueryParams().getFirst("_offset");
            String newCount = builder.build().getQueryParams().getFirst("_count");
            assert newOffset != null;
            assert newCount != null;
            recursiveRetrieveCodeSystems(
                umlsUser, Integer.parseInt(newOffset), Integer.parseInt(newCount), allCodeSystems);
          }
        });
  }

  private String parseOidFromIdentifier(List<Identifier> identifiers) {
    for (Identifier identifier : identifiers) {
      if (identifier.getValue() != null && !identifier.getValue().isEmpty()) {
        // HCPCS contains two OIDs, which VSAC returns as comma delimited String.
        // MADiE is only concerned with the Level 2 OID ending in .285 as it
        // CMS's custom codes. Level 1 is a duplicate of CPT, and
        // users should be utilizing CPT directly.
        if (StringUtils.equalsIgnoreCase(
            StringUtils.deleteWhitespace(identifier.getValue()),
            "urn:oid:2.16.840.1.113883.6.14,2.16.840.1.113883.6.285")) {
          return "urn:oid:2.16.840.1.113883.6.285";
        }
        return identifier.getValue();
      }
    }
    return "";
  }

  // one to call only, one to mutate and build
  private Bundle retrieveCodeSystemsPage(UmlsUser umlsUser, Integer offset, Integer count) {
    IParser parser = fhirContext.newJsonParser();
    String responseString =
        fhirTerminologyServiceWebClient.getCodeSystemsPage(offset, count, umlsUser.getApiKey());
    return parser.parseResource(Bundle.class, responseString);
  }

  private void updateOrInsertAllCodeSystems(List<CodeSystem> codeSystemList) {
    for (CodeSystem codeSystem : codeSystemList) {
      var id = codeSystem.getTitle() + codeSystem.getVersion();
      Optional<CodeSystem> existingCodeSystemOptional = codeSystemRepository.findById(id);
      if (existingCodeSystemOptional.isEmpty()) {
        // Insert new CodeSystem
        codeSystemRepository.save(codeSystem);
        log.info("New CodeSystem inserted: {}", codeSystem);
      } else {
        CodeSystem existingCodeSystem = existingCodeSystemOptional.get();
        existingCodeSystem.setTitle(codeSystem.getTitle());
        existingCodeSystem.setFullUrl(codeSystem.getFullUrl());
        existingCodeSystem.setName(codeSystem.getName());
        existingCodeSystem.setVersion(codeSystem.getVersion());
        existingCodeSystem.setVersionId(codeSystem.getVersionId());
        existingCodeSystem.setOid(codeSystem.getOid());
        existingCodeSystem.setLastUpdated(codeSystem.getLastUpdated());
        existingCodeSystem.setLastUpdatedUpstream(codeSystem.getLastUpdatedUpstream());
        codeSystemRepository.save(existingCodeSystem);
        log.info("CodeSystem updated: {}", existingCodeSystem);
      }
    }
  }

  public List<Code> retrieveCodesAndCodeSystems(List<Map<String, String>> codeList, String apiKey) {
    return codeList.stream()
        .map(
            codeDetails -> {
              List<CodeSystemEntry> codeSystemEntries = mappingService.getCodeSystemEntries();
              String codeName = codeDetails.get("code");
              String codeSystemName = codeDetails.get("codeSystem");
              String oid =
                  codeDetails.get("oid") != null
                      ? codeDetails.get("oid").replaceAll("'|'", "")
                      : null;

              Optional<CodeSystemEntry.Version> codeSystemVersion =
                  getCodeSystemEntryVersion(codeDetails.get("version"), oid, codeSystemEntries);

              if (codeSystemVersion.isPresent()) {
                String vsacVersion = codeSystemVersion.get().getVsac();
                String fhirVersion = codeSystemVersion.get().getFhir();

                if (StringUtils.isEmpty(codeName)
                    || StringUtils.isEmpty(codeSystemName)
                    || StringUtils.isEmpty(fhirVersion)) {
                  return null;
                }

                CodeSystem codeSystem =
                    codeSystemRepository.findByOidAndVersion(oid, fhirVersion).orElse(null);
                if (codeSystem == null) {
                  return null;
                }

                Code code =
                    retrieveCodes(
                        codeName, codeSystemName, vsacVersion, fhirVersion, codeSystem, apiKey);
                code.setVersionIncluded("true".equals(codeDetails.get("versionIncluded")));
                return code;
              }
              return null;
            })
        .collect(Collectors.toList());
  }

  private Optional<CodeSystemEntry.Version> getCodeSystemEntryVersion(
      String version, String oid, List<CodeSystemEntry> codeSystemEntries) {
    if (oid == null) {
      return Optional.empty();
    }

    Optional<CodeSystemEntry.Version> result;
    if (version == null) {
      result =
          codeSystemEntries.stream()
              .filter(codeSystemEntry -> StringUtils.equals(codeSystemEntry.getOid(), oid))
              .map(codeSystemEntry -> codeSystemEntry.getVersions().get(0))
              .findFirst();
    } else {
      result =
          codeSystemEntries.stream()
              .filter(codeSystemEntry -> StringUtils.equals(codeSystemEntry.getOid(), oid))
              .flatMap(codeSystemEntry -> codeSystemEntry.getVersions().stream())
              // depending on the version type suitable mapping is done
              .filter(
                  codeSystemVersion ->
                      StringUtils.equals(codeSystemVersion.getVsac(), version)
                          || StringUtils.equals(codeSystemVersion.getFhir(), version))
              .findFirst();
    }

    return result;
  }

  private Code retrieveCodes(
      String codeName,
      String codeSystemName,
      String vsacVersion,
      String fhirVersion,
      CodeSystem codeSystem,
      String apiKey) {
    String codeJson = fhirTerminologyServiceWebClient.getCodeResource(codeName, codeSystem, apiKey);

    Parameters parameters = fhirContext.newJsonParser().parseResource(Parameters.class, codeJson);
    Code code =
        Code.builder()
            .name(codeName)
            .codeSystem(codeSystemName)
            .fhirVersion(fhirVersion)
            .svsVersion(vsacVersion)
            .codeSystemUrl(codeSystem.getFullUrl())
            .display(parameters.getParameter("display").getValue().toString())
            .codeSystemOid(parameters.getParameter("Oid").getValue().toString())
            .build();

    CodeStatus status = vsacService.getCodeStatus(code, apiKey);
    code.setStatus(status);
    return code;
  }
}
