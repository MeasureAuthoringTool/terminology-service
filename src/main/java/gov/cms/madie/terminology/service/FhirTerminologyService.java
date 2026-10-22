package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
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
import org.apache.commons.lang3.Strings;
import org.hl7.fhir.r4.model.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FhirTerminologyService {
  private final FhirContext fhirContext;
  private final FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;
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
    List<ValueSet> fhirValueSets = getValueSetsExpansion(valueSetsSearchCriteria, umlsUser);

    return fhirValueSets.stream()
        .map(
            fhirValueSet -> {
              List<QdmValueSet.Concept> concepts = getValueSetConcepts(fhirValueSet);
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
   * @return a List of QdmValueSet.Concept if the valueSet has expansions. Also, valueSet resource
   *     only has CodeSystem URL info for its expansions, so we use codeSystem entries to find its
   *     appropriate OID. If associated OID is not found, we return the original FHIR URL of the
   *     code system.
   */
  private List<QdmValueSet.Concept> getValueSetConcepts(ValueSet valueSet) {
    if (valueSet.getExpansion() != null && valueSet.getExpansion().getTotal() > 0) {
      return valueSet.getExpansion().getContains().stream()
          .map(
              concept -> {
                Optional<CodeSystem> codeSystemOptional =
                    codeSystemRepository.findByFullUrlAndVersionFhirVersion(
                        concept.getSystem(), concept.getVersion());
                if (codeSystemOptional.isPresent()) {
                  CodeSystem codeSystem = codeSystemOptional.get();
                  return QdmValueSet.Concept.builder()
                      .code(concept.getCode())
                      .displayName(concept.getDisplay())
                      .codeSystemName(codeSystem.getName())
                      .codeSystemVersion(codeSystem.getVersion().getVsacVersion())
                      .codeSystemOid(
                          TerminologyServiceUtil.removeUrnOidSubString(codeSystem.getOid()))
                      .build();
                }
                return QdmValueSet.Concept.builder()
                    .code(concept.getCode())
                    .displayName(concept.getDisplay())
                    .codeSystemName(concept.getSystem())
                    .codeSystemVersion(concept.getVersion())
                    .codeSystemOid(
                        TerminologyServiceUtil.removeUrnOidSubString(concept.getSystem()))
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

    Comparator<ValueSetForSearch> byVersionId =
        Comparator.comparing(
            ValueSetForSearch::getVersionId,
            Comparator.nullsFirst(Comparator.comparing(BigInteger::new)));
    Map<String, ValueSetForSearch> latestValueSets =
        valueSetList.stream()
            .collect(
                Collectors.toMap(
                    ValueSetForSearch::getOid,
                    valueSet -> valueSet,
                    (existing, candidate) ->
                        byVersionId.compare(existing, candidate) >= 0 ? existing : candidate,
                    LinkedHashMap::new));

    return ValueSetSearchResult.builder()
        .valueSets(new ArrayList<>(latestValueSets.values()))
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
              .versionId(vs.getMeta().getVersionId())
              .status(vs.getStatus())
              .publisher(vs.getPublisher())
              .purpose(vs.getPurpose())
              .steward(vs.getPublisher())
              .oid(oid)
              .build();
      valueSetList.add(valueSet);
    }
  }

  public Code retrieveCode(String codeName, String codeSystemName, String version, String apiKey) {
    if (StringUtils.isEmpty(codeName)
        || StringUtils.isEmpty(codeSystemName)
        || StringUtils.isEmpty(version)) {
      return null;
    }

    CodeSystem codeSystem =
        codeSystemRepository
            .findByNameAndVersionFhirVersion(codeSystemName, version)
            .orElse(
                codeSystemRepository
                    .findByNameAndVersionVsacVersion(codeSystemName, version)
                    .orElse(null));
    if (codeSystem == null || !codeSystem.isInVsac()) {
      return null;
    }
    return retrieveCodes(codeName, codeSystemName, codeSystem, apiKey);
  }

  public List<Code> retrieveCodesAndCodeSystems(List<Map<String, String>> codeList, String apiKey) {
    return codeList.stream()
        .map(
            codeDetails -> {
              String codeName = codeDetails.get("code");
              String codeSystemName = codeDetails.get("codeSystem");
              String oid =
                  codeDetails.get("oid") != null
                      ? codeDetails.get("oid").replaceAll("'|'", "")
                      : null;

              List<CodeSystem> codeSystems = codeSystemRepository.findAllByOid(oid);
              Optional<CodeSystem> codeSystemVersion =
                  getCodeSystemVersion(codeDetails.get("version"), oid, codeSystems);

              if (codeSystemVersion.isEmpty()
                  || StringUtils.isEmpty(codeName)
                  || StringUtils.isEmpty(codeSystemName)) {
                return null;
              }

              Code code = retrieveCodes(codeName, codeSystemName, codeSystemVersion.get(), apiKey);
              code.setVersionIncluded("true".equals(codeDetails.get("versionIncluded")));
              return code;
            })
        .collect(Collectors.toList());
  }

  private Optional<CodeSystem> getCodeSystemVersion(
      String version, String oid, List<CodeSystem> codeSystems) {
    if (oid == null) {
      return Optional.empty();
    }

    if (CollectionUtils.isEmpty(codeSystems)) {
      return Optional.empty();
    }
    Optional<CodeSystem> result;
    if (version == null) {
      result =
          codeSystems.stream()
              .filter(
                  codeSystemEntry ->
                      Strings.CS.equals(codeSystemEntry.getOid(), oid)
                          && codeSystemEntry.isLatestVersion())
              .findFirst();
    } else {
      result =
          codeSystems.stream()
              .filter(codeSystemEntry -> Strings.CS.equals(codeSystemEntry.getOid(), oid))
              .filter(
                  codeSystemVersion ->
                      Strings.CS.equals(codeSystemVersion.getVersion().getVsacVersion(), version)
                          || Strings.CS.equals(
                              codeSystemVersion.getVersion().getFhirVersion(), version))
              .findFirst();
    }

    return result;
  }

  private Code retrieveCodes(
      String codeName, String codeSystemName, CodeSystem codeSystem, String apiKey) {
    String codeJson = fhirTerminologyServiceWebClient.getCodeResource(codeName, codeSystem, apiKey);

    Parameters parameters = fhirContext.newJsonParser().parseResource(Parameters.class, codeJson);
    Code code =
        Code.builder()
            .name(codeName)
            .codeSystem(codeSystemName)
            .fhirVersion(codeSystem.getVersion().getFhirVersion())
            .svsVersion(codeSystem.getVersion().getVsacVersion())
            .codeSystemUrl(codeSystem.getFullUrl())
            .display(parameters.getParameter("display").getValue().toString())
            .codeSystemOid(parameters.getParameter("Oid").getValue().toString())
            .build();

    CodeStatus status = vsacService.getCodeStatus(code, apiKey);
    code.setStatus(status);
    return code;
  }
}
