package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.models.mapping.CodeSystemEntry;
import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.Code;
import gov.cms.madie.terminology.dto.CodeStatus;
import gov.cms.madie.terminology.dto.QdmValueSet;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.util.TerminologyServiceUtil;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
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
            entry ->
                manifestOptions.add(
                    ManifestExpansion.builder()
                        .id(entry.getResource().getIdPart())
                        .fullUrl(entry.getFullUrl())
                        .build()));
    return manifestOptions;
  }

  public List<QdmValueSet> getValueSetsExpansionsForQdm(
      ValueSetsSearchCriteria valueSetsSearchCriteria, UmlsUser umlsUser) {
    List<CodeSystemEntry> codeSystemEntries = mappingService.getCodeSystemEntries();
    return valueSetsSearchCriteria.getValueSetParams().stream()
        .map(
            vsParam -> {
              String resource =
                  fhirTerminologyServiceWebClient.getValueSetResource(
                      umlsUser.getApiKey(),
                      vsParam,
                      valueSetsSearchCriteria.getProfile(),
                      valueSetsSearchCriteria.getIncludeDraft(),
                      valueSetsSearchCriteria.getManifestExpansion());
              IParser parser = fhirContext.newJsonParser();
              ValueSet ValueSetResource = parser.parseResource(ValueSet.class, resource);
              // Converting a ValueSet FHIR Resource into QDM Value Set DTO
              List<QdmValueSet.Concept> concepts =
                  getValueSetConcepts(ValueSetResource, codeSystemEntries);
              return QdmValueSet.builder()
                  .oid(ValueSetResource.getIdPart())
                  .displayName(ValueSetResource.getName())
                  .version(ValueSetResource.getVersion())
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
      ValueSet valueSet, List<CodeSystemEntry> codeSystemEntries) {
    if (valueSet.getExpansion() != null && valueSet.getExpansion().getTotal() > 0) {
      return valueSet.getExpansion().getContains().stream()
          .map(
              concept -> {
                Optional<CodeSystemEntry> optionalCodeSystemEntry =
                    TerminologyServiceUtil.getCodeSystemEntry(
                        codeSystemEntries, concept.getSystem(), "FHIR");
                String codeSystemOid = concept.getSystem();
                String codeSystem = concept.getSystem();
                if (optionalCodeSystemEntry.isPresent()) {
                  codeSystemOid = optionalCodeSystemEntry.get().getOid();
                  codeSystem = optionalCodeSystemEntry.get().getName();
                }
                return QdmValueSet.Concept.builder()
                    .code(concept.getCode())
                    .displayName(concept.getDisplay())
                    .codeSystemName(codeSystem)
                    .codeSystemVersion(concept.getVersion())
                    .codeSystemOid(TerminologyServiceUtil.removeUrnOidSubString(codeSystemOid))
                    .build();
              })
          .toList();
    }
    log.info("No Expansion codes are found for the valueSet oid : [{}]", valueSet.getId());
    return List.of();
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
    String codeJson = fhirTerminologyServiceWebClient.getCodeResource(codeName, codeSystem, apiKey);
    Parameters parameters = fhirContext.newJsonParser().parseResource(Parameters.class, codeJson);
    Code code =
        Code.builder()
            .name(codeName)
            .codeSystem(codeSystemName)
            .version(version)
            .display(parameters.getParameter("display").getValue().toString())
            .codeSystemOid(parameters.getParameter("Oid").getValue().toString())
            .build();
    // FHIR terminology API doesn't support code status yet. workaround is to get it from SVS.
    // TODO: remove once it is supported by fhir terminology service
    CodeStatus status = vsacService.getCodeStatus(code, apiKey);
    code.setStatus(status);
    return code;
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
              String codeSystemValue = "";
              for (org.hl7.fhir.r4.model.Identifier identifier : codeSystem.getIdentifier()) {
                if (identifier.getValue() != null && !identifier.getValue().isEmpty()) {
                  codeSystemValue = identifier.getValue();
                  break;
                }
              }
              codeSystemsPage.add(
                  CodeSystem.builder()
                      .id(codeSystem.getTitle() + codeSystem.getVersion())
                      .fullUrl(entry.getFullUrl())
                      .title(codeSystem.getTitle())
                      .name(codeSystem.getName())
                      .version(codeSystem.getVersion())
                      .versionId(codeSystem.getMeta().getVersionId())
                      .oid(codeSystemValue)
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
            code -> {
              List<CodeSystemEntry> codeSystemEntries = mappingService.getCodeSystemEntries();
              String codeName = code.get("code");
              String codeSystemName = code.get("codeSystem");
              String oid = code.get("oid") != null ? code.get("oid").replaceAll("'|'", "") : null;

              Optional<Map.Entry<String, String>> mappedVersion =
                  mapToFhirVersion(code.get("version"), oid, codeSystemEntries);

              if (mappedVersion.isPresent()) {
                String vsacVersion = mappedVersion.get().getKey();
                String fhirVersion = mappedVersion.get().getValue();

                return retrieveCodes(
                    codeName, codeSystemName, vsacVersion, fhirVersion, oid, apiKey);
              }

              return retrieveCodes(codeName, codeSystemName, null, null, oid, apiKey);
            })
        .collect(Collectors.toList());
  }

  private Optional<Map.Entry<String, String>> mapToFhirVersion(
      String version, String oid, List<CodeSystemEntry> codeSystemEntries) {

    if (oid == null) {
      return Optional.empty();
    }

    Optional<Map.Entry<String, String>> result;
    if (version == null) {
      result =
          codeSystemEntries.stream()
              .filter(codeSystemEntry -> codeSystemEntry.getOid().toString().equals(oid))
              .findFirst()
              .map(
                  codeSystemVersion ->
                      Map.entry(
                          codeSystemVersion.getVersions().get(0).getVsac(),
                          codeSystemVersion.getVersions().get(0).getFhir()));
    } else {
      result =
          codeSystemEntries.stream()
              .filter(codeSystemEntry -> codeSystemEntry.getOid().toString().equals(oid))
              .flatMap(codeSystemEntry -> codeSystemEntry.getVersions().stream())
              .filter(codeSystemVersion -> codeSystemVersion.getVsac().equals(version))
              .map(
                  codeSystemVersion ->
                      Map.entry(codeSystemVersion.getVsac(), codeSystemVersion.getFhir()))
              .findFirst();
    }

    return result;
  }

  private Code retrieveCodes(
      String codeName,
      String codeSystemName,
      String vsacVersion,
      String fhirVersion,
      String oid,
      String apiKey) {
    try {
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
      String codeJson =
          fhirTerminologyServiceWebClient.getCodeResource(codeName, codeSystem, apiKey);

      Parameters parameters = fhirContext.newJsonParser().parseResource(Parameters.class, codeJson);
      Code code =
          Code.builder()
              .name(codeName)
              .codeSystem(codeSystemName)
              .version(fhirVersion)
              .svsVersion(vsacVersion)
              .display(parameters.getParameter("display").getValue().toString())
              .codeSystemOid(parameters.getParameter("Oid").getValue().toString())
              .build();

      CodeStatus status = vsacService.getCodeStatus(code, apiKey);
      code.setStatus(status);
      return code;
    } catch (Exception ex) {
      return null;
    }
  }
}
