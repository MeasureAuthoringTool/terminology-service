package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.QdmValueSet;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class FhirTerminologyService {
  private final FhirContext fhirContext;
  private final FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;

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
              List<QdmValueSet.Concept> concepts = getValueSetConcepts(ValueSetResource);
              return QdmValueSet.builder()
                  .oid(ValueSetResource.getIdPart())
                  .displayName(ValueSetResource.getName())
                  .version(ValueSetResource.getVersion())
                  .concepts(concepts)
                  .build();
            })
        .toList();
  }

  private List<QdmValueSet.Concept> getValueSetConcepts(ValueSet valueSet) {
    if (valueSet.getExpansion() != null && valueSet.getExpansion().getTotal() > 0) {
      return valueSet.getExpansion().getContains().stream()
          .map(
              concept ->
                  QdmValueSet.Concept.builder()
                      .code(concept.getCode())
                      .displayName(concept.getDisplay())
                      .codeSystemName(concept.getSystem())
                      .codeSystemVersion(concept.getVersion())
                      .codeSystemOid(concept.getSystem())
                      .build())
          .toList();
    }
    log.info("No Expansion codes are found for the valueSet oid : [{}]", valueSet.getId());
    return List.of();
  }
}
