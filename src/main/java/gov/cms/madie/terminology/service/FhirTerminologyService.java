package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.models.mapping.CodeSystemEntry;
import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.QdmValueSet;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.util.TerminologyServiceUtil;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class FhirTerminologyService {
  private final FhirContext fhirContext;
  private final FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;
  private final MappingService mappingService;
  private final CodeSystemRepository codeSystemRepository;

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
                if (optionalCodeSystemEntry.isPresent()) {
                  codeSystemOid = optionalCodeSystemEntry.get().getOid();
                }
                return QdmValueSet.Concept.builder()
                    .code(concept.getCode())
                    .displayName(concept.getDisplay())
                    .codeSystemName(concept.getSystem())
                    .codeSystemVersion(concept.getVersion())
                    .codeSystemOid(TerminologyServiceUtil.removeUrnOidSubString(codeSystemOid))
                    .build();
              })
          .toList();
    }
    log.info("No Expansion codes are found for the valueSet oid : [{}]", valueSet.getId());
    return List.of();
  }


  public List<CodeSystem> getAllCodeSystems(){
        return codeSystemRepository.findAll();
  }
  public List<CodeSystem> retrieveAllCodeSystems(UmlsUser umlsUser) {
      List<CodeSystem> allCodeSystems = new ArrayList<>();

      recursiveRetrieveCodeSystems(umlsUser, 0, 50, allCodeSystems);
      // Once we have all codeSystems, update DB using mongo
      updateOrInsertAllCodeSystems(allCodeSystems);
      return allCodeSystems;
  }
    private void recursiveRetrieveCodeSystems(UmlsUser umlsUser, Integer offset, Integer count, List<CodeSystem> allCodeSystems) {
        log.info("requesting page offset: {} count: {}", offset, count);
        Bundle codeSystemBundle = retrieveCodeSystemsPage(umlsUser, offset, count);
        List<CodeSystem> codeSystemsPage = new ArrayList<>(); // build small list
        codeSystemBundle.getEntry().forEach(entry -> {
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
        links.forEach((l) -> {
            if (l.getRelation().equals("next")){
                // if next, call self and continue until fail.
                UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(l.getUrl());
                String newOffset = builder.build().getQueryParams().getFirst("_offset");
                String newCount = builder.build().getQueryParams().getFirst("_count");
                assert newOffset != null;
                assert newCount != null;
                recursiveRetrieveCodeSystems(umlsUser, Integer.parseInt(newOffset), Integer.parseInt(newCount), allCodeSystems);
            }
        });
    }
    // one to call only, one to mutate and build
    private Bundle retrieveCodeSystemsPage(UmlsUser umlsUser, Integer offset, Integer count) {
        IParser parser = fhirContext.newJsonParser();
        String responseString =
                fhirTerminologyServiceWebClient.getCodeSystemsPage(offset, count, umlsUser.getApiKey());
        return parser.parseResource(
                Bundle.class, responseString);
    }

    private void updateOrInsertAllCodeSystems(List<CodeSystem> codeSystemList){
        for (CodeSystem codeSystem : codeSystemList) {
            var id = codeSystem.getTitle() + codeSystem.getVersion();
            Optional<CodeSystem> existingCodeSystemOptional = codeSystemRepository.findById(id);
            if (existingCodeSystemOptional.isEmpty()) {
                // Insert new CodeSystem
                codeSystemRepository.save(codeSystem);
                log.info("New CodeSystem inserted: {}", codeSystem);
            }
        }
    }

}
