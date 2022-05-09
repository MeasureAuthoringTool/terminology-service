package cms.gov.madie.terminology.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import cms.gov.madie.terminology.dto.MadieConceptReferenceComponent;
import cms.gov.madie.terminology.dto.MadieConceptSetComponent;
import cms.gov.madie.terminology.dto.MadieValueSet;
import cms.gov.madie.terminology.dto.MadieValueSetComposeComponent;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse.DescribedValueSet;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse.DescribedValueSet.ConceptList.Concept;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class VsacToMadieValueSetMapper {

  public MadieValueSet convertToMadieValueSet(
      RetrieveMultipleValueSetsResponse vsacValuesetResponse, String oid) {
    DescribedValueSet vsacDescribedValueSet = vsacValuesetResponse.getDescribedValueSet();
    MadieValueSet madieValueSet = new MadieValueSet();
    madieValueSet = setMainAttributes(madieValueSet, vsacDescribedValueSet, oid);
    if (vsacDescribedValueSet.getConceptList() != null
        && !CollectionUtils.isEmpty(vsacDescribedValueSet.getConceptList().getConcepts())) {
      addMadieValueSetComposeComponent(
          vsacDescribedValueSet.getConceptList().getConcepts(), madieValueSet);
    }

    return madieValueSet;
  }

  protected MadieValueSet setMainAttributes(
      MadieValueSet madieValueSet, DescribedValueSet vsacDescribedValueSet, String oid) {
    madieValueSet.setID(vsacDescribedValueSet.getID());
    madieValueSet.setUrl("http://cts.nlm.nih.gov/fhir/ValueSet/" + vsacDescribedValueSet.getID());

    madieValueSet.setName(vsacDescribedValueSet.getDisplayName());
    madieValueSet.setTitle(vsacDescribedValueSet.getDisplayName());
    madieValueSet.setVersion(vsacDescribedValueSet.getVersion());

    madieValueSet.setStatus(vsacDescribedValueSet.getStatus().toLowerCase());
    madieValueSet.setDate(vsacDescribedValueSet.getRevisionDate().toGregorianCalendar().getTime());
    madieValueSet.setPublisher(vsacDescribedValueSet.getSource());
    // ??
    madieValueSet.setDescription(vsacDescribedValueSet.getDefinition());
    madieValueSet.setPurpose(vsacDescribedValueSet.getPurpose());
    return madieValueSet;
  }

  protected void addMadieValueSetComposeComponent(
      List<Concept> vsacConceptList, MadieValueSet madieValueSet) {
    MadieValueSetComposeComponent madieValueSetComposeComponent =
        new MadieValueSetComposeComponent();
    madieValueSetComposeComponent.setInclude(new ArrayList<>());
    madieValueSet.setCompose(madieValueSetComposeComponent);
    Map<String, List<Concept>> conceptsByCodeAndVersionMap =
        getVsacConceptMapByCodeAndVersion(vsacConceptList);
    createMadieConceptSetComponents(conceptsByCodeAndVersionMap, madieValueSetComposeComponent);
  }

  protected Map<String, List<Concept>> getVsacConceptMapByCodeAndVersion(
      List<Concept> vsacConceptList) {
    Map<String, String> vsacCodeMap = getVsacCodeSystemMap(vsacConceptList);
    Map<String, List<Concept>> conceptsByCodeMap = new HashMap<String, List<Concept>>();
    Map<String, List<Concept>> conceptsByCodeAndVersionMap = new HashMap<String, List<Concept>>();
    vsacCodeMap
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
      Map<String, String> versionMap = getVsacCodeSystemVersionMap(conceptListByCode);

      for (Map.Entry<String, String> ent : versionMap.entrySet()) {
        String version = ent.getKey();
        List<Concept> conceptsByCodeAndVersion =
            getVsacConceptListByCodeAndVersion(code, version, conceptListByCode);
        conceptsByCodeAndVersionMap.put(code, conceptsByCodeAndVersion);
      }
    }

    return conceptsByCodeAndVersionMap;
  }

  protected Map<String, String> getVsacCodeSystemMap(List<Concept> vsacConcepts) {
    Map<String, String> vsacCodeSystemMap = new HashMap<String, String>();
    vsacConcepts.forEach(
        vsacConcept -> {
          vsacCodeSystemMap.put(vsacConcept.getCodeSystem(), vsacConcept.getCodeSystem());
        });
    return vsacCodeSystemMap;
  }

  protected List<Concept> getVsacConceptListByCode(String code, List<Concept> concepts) {
    return concepts.stream()
        .filter(c -> code.equalsIgnoreCase(c.getCodeSystem()))
        .collect(Collectors.toList());
  }

  protected List<Concept> getVsacConceptListByCodeAndVersion(
      String code, String version, List<Concept> vsacConceptList) {
    return vsacConceptList.stream()
        .filter(
            vsacConcept ->
                code.equalsIgnoreCase(vsacConcept.getCodeSystem())
                    && version.equalsIgnoreCase(vsacConcept.getCodeSystemVersion()))
        .collect(Collectors.toList());
  }

  protected Map<String, String> getVsacCodeSystemVersionMap(List<Concept> vsacConcepts) {
    Map<String, String> vsacVersionMap = new HashMap<String, String>();

    vsacConcepts.forEach(
        vsacConcept -> {
          vsacVersionMap.put(
              vsacConcept.getCodeSystemVersion(), vsacConcept.getCodeSystemVersion());
        });
    return vsacVersionMap;
  }

  protected void createMadieConceptSetComponents(
      Map<String, List<Concept>> conceptsByCodeAndVersionMap,
      MadieValueSetComposeComponent madieValueSetComposeComponent) {

    for (Map.Entry<String, List<Concept>> entry : conceptsByCodeAndVersionMap.entrySet()) {
      MadieConceptSetComponent madieComponent = new MadieConceptSetComponent();
      List<Concept> conceptList = entry.getValue();
      madieComponent.setVersion(conceptList.get(0).getCodeSystemVersion());
      madieComponent.setSystem(conceptList.get(0).getCodeSystem());
      madieComponent.setConcept(createMadieConceptReferenceComponents(conceptList));
      madieValueSetComposeComponent.getInclude().add(madieComponent);
    }
  }

  protected List<MadieConceptReferenceComponent> createMadieConceptReferenceComponents(
      List<Concept> vsacConceptList) {
    return vsacConceptList.stream()
        .map(this::createMadieConceptSetComponent)
        .collect(Collectors.toList());
  }

  protected MadieConceptReferenceComponent createMadieConceptSetComponent(Concept vascConcept) {
    MadieConceptReferenceComponent madieConceptReferenceComponent =
        new MadieConceptReferenceComponent();
    madieConceptReferenceComponent.setCode(String.valueOf(vascConcept.getCode()));
    madieConceptReferenceComponent.setDisplay(vascConcept.getDisplayName());
    return madieConceptReferenceComponent;
  }
}
