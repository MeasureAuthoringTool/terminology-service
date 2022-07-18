package gov.cms.madie.terminology.mapper;

import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse.DescribedValueSet;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse.DescribedValueSet.ConceptList.Concept;
import gov.cms.madie.models.mapping.CodeSystemEntry;
import gov.cms.madie.terminology.service.MappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r4.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetComposeComponent;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class VsacToFhirValueSetMapper {

  private final MappingService mappingService;

  public ValueSet convertToFHIRValueSet(RetrieveMultipleValueSetsResponse vsacValueSetResponse) {
    DescribedValueSet vsacDescribedValueSet = vsacValueSetResponse.getDescribedValueSet();
    ValueSet fhirValueSet = new ValueSet();
    fhirValueSet =
        setFhirMainAttributes(fhirValueSet, vsacDescribedValueSet, vsacDescribedValueSet.getID());
    if (vsacDescribedValueSet.getConceptList() != null
        && !CollectionUtils.isEmpty(vsacDescribedValueSet.getConceptList().getConcepts())) {
      addFhirValueSetComposeComponent(
          vsacDescribedValueSet.getConceptList().getConcepts(), fhirValueSet);
    }
    return fhirValueSet;
  }

  protected ValueSet setFhirMainAttributes(
      ValueSet fhirValueSet, DescribedValueSet vsacDescribedValueSet, String oid) {
    fhirValueSet.setId(vsacDescribedValueSet.getID());
    fhirValueSet.setUrl("http://cts.nlm.nih.gov/fhir/ValueSet/" + vsacDescribedValueSet.getID());
    List<Identifier> ids = new ArrayList<>();
    Identifier id = new Identifier();
    id.setId(oid);
    fhirValueSet.setIdentifier(ids);
    fhirValueSet.setName(vsacDescribedValueSet.getDisplayName());
    fhirValueSet.setTitle(vsacDescribedValueSet.getDisplayName());
    fhirValueSet.setVersion(vsacDescribedValueSet.getVersion());
    fhirValueSet.setStatus(
        PublicationStatus.fromCode(vsacDescribedValueSet.getStatus().toLowerCase()));
    fhirValueSet.setDate(vsacDescribedValueSet.getRevisionDate().toGregorianCalendar().getTime());
    fhirValueSet.setPublisher(vsacDescribedValueSet.getSource());
    // ??
    fhirValueSet.setDescription(vsacDescribedValueSet.getDefinition());
    fhirValueSet.setPurpose(vsacDescribedValueSet.getPurpose());
    return fhirValueSet;
  }

  protected void addFhirValueSetComposeComponent(
      List<Concept> vsacConceptList, ValueSet fhirValueSet) {
    ValueSetComposeComponent fhirValueSetComposeComponent = new ValueSetComposeComponent();
    fhirValueSetComposeComponent.setInclude(new ArrayList<>());
    fhirValueSet.setCompose(fhirValueSetComposeComponent);
    Map<String, List<Concept>> conceptsByCodeAndVersionMap =
        getConceptMapByCodeAndVersion(vsacConceptList);
    createFhirConceptSetComponents(conceptsByCodeAndVersionMap, fhirValueSetComposeComponent);
  }

  protected Map<String, List<Concept>> getConceptMapByCodeAndVersion(
      List<Concept> vsacConceptList) {
    Map<String, String> codeMap = getVsacCodeMap(vsacConceptList);
    Map<String, List<Concept>> conceptsByCodeMap = new HashMap<>();
    Map<String, List<Concept>> conceptsByCodeAndVersionMap = new HashMap<>();
    codeMap
        .entrySet()
        .forEach(
            code -> {
              List<Concept> conceptsListByCode =
                  getVsacConceptListByCode(code.getKey(), vsacConceptList);
              conceptsByCodeMap.put(code.getKey(), conceptsListByCode);
            });
    for (var entry : conceptsByCodeMap.entrySet()) {
      String code = entry.getKey();
      List<Concept> conceptListByCode = entry.getValue();
      Map<String, String> versionMap = getVsacVersionMap(conceptListByCode);

      for (Map.Entry<String, String> ent : versionMap.entrySet()) {
        String version = ent.getKey();
        List<Concept> conceptsByCodeAndVersion =
            getVsacConceptListByCodeAndVersion(code, version, conceptListByCode);
        conceptsByCodeAndVersionMap.put(code, conceptsByCodeAndVersion);
      }
    }

    return conceptsByCodeAndVersionMap;
  }

  protected Map<String, String> getVsacCodeMap(List<Concept> vsacConcepts) {
    Map<String, String> vsacCodeMap = new HashMap<>();
    vsacConcepts.forEach(
        vsacConcept -> vsacCodeMap.put(vsacConcept.getCodeSystem(), vsacConcept.getCodeSystem()));
    return vsacCodeMap;
  }

  protected List<Concept> getVsacConceptListByCode(String code, List<Concept> concepts) {
    return concepts.stream()
        .filter(c -> code.equalsIgnoreCase(c.getCodeSystem()))
        .collect(Collectors.toList());
  }

  protected List<Concept> getVsacConceptListByCodeAndVersion(
      String code, String version, List<Concept> concepts) {
    return concepts.stream()
        .filter(
            vsacConcept ->
                code.equalsIgnoreCase(vsacConcept.getCodeSystem())
                    && version.equalsIgnoreCase(vsacConcept.getCodeSystemVersion()))
        .collect(Collectors.toList());
  }

  protected Map<String, String> getVsacVersionMap(List<Concept> vsacConcepts) {
    Map<String, String> vsacVersionMap = new HashMap<>();

    vsacConcepts.forEach(
        vsacConcept ->
            vsacVersionMap.put(
                vsacConcept.getCodeSystemVersion(), vsacConcept.getCodeSystemVersion()));
    return vsacVersionMap;
  }

  protected void createFhirConceptSetComponents(
      Map<String, List<Concept>> conceptsByCodeAndVersionMap,
      ValueSetComposeComponent valueSetComposeComponent) {

    for (Map.Entry<String, List<Concept>> entry : conceptsByCodeAndVersionMap.entrySet()) {
      ConceptSetComponent fhirComponent = new ConceptSetComponent();
      List<Concept> conceptList = entry.getValue();
      fhirComponent.setVersion(conceptList.get(0).getCodeSystemVersion());

      fhirComponent.setSystem(getUrlByOid(conceptList.get(0).getCodeSystem()));
      fhirComponent.setConcept(createFhirConceptReferenceComponents(conceptList));
      valueSetComposeComponent.getInclude().add(fhirComponent);
    }
  }

  protected List<ConceptReferenceComponent> createFhirConceptReferenceComponents(
      List<Concept> vsacConceptList) {
    return vsacConceptList.stream()
        .map(this::createFhirConceptSetComponent)
        .collect(Collectors.toList());
  }

  protected ConceptReferenceComponent createFhirConceptSetComponent(Concept vascConcept) {
    return new ConceptReferenceComponent()
        .setCode(String.valueOf(vascConcept.getCode()))
        .setDisplay(vascConcept.getDisplayName());
  }

  protected String getUrlByOid(String oid) {
    List<CodeSystemEntry> codeSystemEntries = mappingService.getCodeSystemEntries();

    if (!StringUtils.isBlank(oid)) {
      Optional<CodeSystemEntry> codeSystemEntry =
          codeSystemEntries.stream()
              .filter(cse -> cse.getOid().replace("urn:oid:", "").equals(oid))
              .findFirst();
      if (codeSystemEntry.isPresent()) {
        return codeSystemEntry.get().getUrl();
      }
    }
    return oid;
  }
}
