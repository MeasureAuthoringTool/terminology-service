package cms.gov.madie.terminology.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetComposeComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse.DescribedValueSet;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse.DescribedValueSet.ConceptList;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse.DescribedValueSet.ConceptList.Concept;

@ExtendWith(MockitoExtension.class)
public class VsacToFhirValueSetMapperTest {

  @InjectMocks private VsacToFhirValueSetMapper mapper;

  private DescribedValueSet describedValueSet;
  private static final String TEST_ID = "testId";
  private static final String TEST = "test";
  private final Date today = new Date();
  private Concept vsacConcept1;
  private Concept vsacConcept2;
  private Concept vsacConcept3;
  private Concept vsacConcept4;
  private Concept vsacConcept5;

  private List<Concept> vsacConceptList;

  @BeforeEach
  public void setUp() {
    describedValueSet = new DescribedValueSet();
    describedValueSet.setID(TEST_ID);
    describedValueSet.setDisplayName(TEST);
    describedValueSet.setVersion(TEST);
    describedValueSet.setStatus("Active");
    describedValueSet.setPurpose(TEST);
    describedValueSet.setDefinition(TEST);
    describedValueSet.setPurpose(TEST);
    describedValueSet.setSource(TEST);

    GregorianCalendar gregory = new GregorianCalendar();
    gregory.setTime(today);

    try {
      XMLGregorianCalendar todayXMLGregorianCalendar =
          DatatypeFactory.newInstance().newXMLGregorianCalendar(gregory);
      describedValueSet.setRevisionDate(todayXMLGregorianCalendar);
    } catch (DatatypeConfigurationException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    vsacConcept1 =
        getVsacConcept(
            "185460008",
            "2.16.840.1.113883.6.96",
            "SNOMEDCT",
            "2022-03",
            "Home visit request by patient (procedure)");
    vsacConcept2 =
        getVsacConcept(
            "185462000",
            "2.16.840.1.113883.6.96",
            "SNOMEDCT",
            "2022-03",
            "Home visit request by relative (procedure)");
    vsacConcept3 =
        getVsacConcept(
            "185466002",
            "2.16.840.1.113883.6.96",
            "SNOMEDCT",
            "2022-03",
            "Home visit for urgent condition (procedure)");
    vsacConcept4 =
        getVsacConcept(
            "99342",
            "2.16.840.1.113883.6.12",
            "CPT",
            "2021",
            "Home visit for the evaluation and management of a new patient, which requires these 3 key components: An expanded problem focused history; An expanded problem focused examination; and Medical decision making of low complexity. Counseling and/or coordination of care with other physicians, other qualified health care professionals, or agencies are provided consistent with the nature of the problem(s) and the patient's and/or family's needs. Usually, the presenting problem(s) are of moderate severity. Typically, 30 minutes are spent face-to-face with the patient and/or family.");
    vsacConcept5 =
        getVsacConcept(
            "99343",
            "2.16.840.1.113883.6.12",
            "CPT",
            "2021",
            "Home visit for the evaluation and management of a new patient, which requires these 3 key components: A detailed history; A detailed examination; and Medical decision making of moderate complexity. Counseling and/or coordination of care with other physicians, other qualified health care professionals, or agencies are provided consistent with the nature of the problem(s) and the patient's and/or family's needs. Usually, the presenting problem(s) are of moderate to high severity. Typically, 45 minutes are spent face-to-face with the patient and/or family.");

    vsacConceptList = new ArrayList<>();
    vsacConceptList.add(vsacConcept1);
    vsacConceptList.add(vsacConcept2);
    vsacConceptList.add(vsacConcept3);
    vsacConceptList.add(vsacConcept4);
    vsacConceptList.add(vsacConcept5);
  }

  @Test
  public void testSetFhirMainAttributes() {
    ValueSet vs = new ValueSet();

    vs = mapper.setFhirMainAttributes(vs, describedValueSet, TEST);

    assertEquals(vs.getId(), TEST_ID);
    assertEquals(vs.getName(), TEST);
    assertEquals(vs.getTitle(), TEST);
    assertEquals(vs.getVersion(), TEST);
    assertEquals(vs.getStatus().getDisplay(), "Active");
    assertEquals(vs.getPublisher(), TEST);
    assertEquals(vs.getDescription(), TEST);
    assertEquals(vs.getPurpose(), TEST);
    assertEquals(vs.getDate(), today);
  }

  @Test
  public void testGetVsacCodeMap() {

    Map<String, String> vsacCodeMap = mapper.getVsacCodeMap(vsacConceptList);

    assertEquals(vsacCodeMap.size(), 2);
  }

  @Test
  public void testGetVsacConceptListByCode() {
    List<Concept> result =
        mapper.getVsacConceptListByCode("2.16.840.1.113883.6.96", vsacConceptList);
    assertEquals(result.size(), 3);
    result = mapper.getVsacConceptListByCode("2.16.840.1.113883.6.12", vsacConceptList);
    assertEquals(result.size(), 2);
    result = mapper.getVsacConceptListByCode(TEST, vsacConceptList);
    assertEquals(result.size(), 0);
  }

  @Test
  public void testGetVsacVersionMap() {
    Map<String, String> vsacVersionMap = mapper.getVsacVersionMap(vsacConceptList);
    assertEquals(vsacVersionMap.size(), 2);
  }

  @Test
  public void testGetVsacConceptListByCodeAndVersion() {
    List<Concept> conceptListByCodeAndVersion =
        mapper.getVsacConceptListByCodeAndVersion(
            "2.16.840.1.113883.6.96", "2022-03", vsacConceptList);
    assertEquals(conceptListByCodeAndVersion.size(), 3);

    conceptListByCodeAndVersion =
        mapper.getVsacConceptListByCodeAndVersion(
            "2.16.840.1.113883.6.12", "2021", vsacConceptList);
    assertEquals(conceptListByCodeAndVersion.size(), 2);

    conceptListByCodeAndVersion =
        mapper.getVsacConceptListByCodeAndVersion(
            "2.16.840.1.113883.6.12", "2022-03", vsacConceptList);
    assertEquals(conceptListByCodeAndVersion.size(), 0);
  }

  @Test
  public void testGetConceptMapByCodeAndVersion() {
    Map<String, List<Concept>> conceptListMap =
        mapper.getConceptMapByCodeAndVersion(vsacConceptList);
    assertEquals(conceptListMap.size(), 2);
  }

  @Test
  public void testCreateFhirConceptSetComponent() {
    ConceptReferenceComponent result = mapper.createFhirConceptSetComponent(vsacConcept1);
    assertEquals(result.getCode(), "185460008");
    assertEquals(result.getDisplay(), "Home visit request by patient (procedure)");
  }

  @Test
  public void testCreateFhirConceptReferenceComponents() {
    List<ConceptReferenceComponent> result =
        mapper.createFhirConceptReferenceComponents(vsacConceptList);
    assertEquals(result.size(), 5);
  }

  @Test
  public void testCreateFhirConceptSetComponents() {
    Map<String, List<Concept>> conceptListMap =
        mapper.getConceptMapByCodeAndVersion(vsacConceptList);

    ValueSetComposeComponent fhirValueSetComposeComponent = new ValueSetComposeComponent();
    fhirValueSetComposeComponent.setInclude(new ArrayList<>());
    ValueSet fhirValueSet = new ValueSet();
    fhirValueSet.setCompose(fhirValueSetComposeComponent);

    mapper.createFhirConceptSetComponents(conceptListMap, fhirValueSetComposeComponent);

    assertEquals(fhirValueSet.getCompose().getInclude().size(), 2);
  }

  @Test
  public void testAddFhirValueSetComposeComponent() {
    ValueSet fhirValueSet = new ValueSet();

    mapper.addFhirValueSetComposeComponent(vsacConceptList, fhirValueSet);

    assertEquals(fhirValueSet.getCompose().getInclude().size(), 2);
  }

  @Test
  public void testConvertToFHIRValueSet() {
    RetrieveMultipleValueSetsResponse vsacValuesetResponse =
        new RetrieveMultipleValueSetsResponse();
    ConceptList conceptList = new ConceptList();
    conceptList.getConcepts().add(vsacConcept1);
    conceptList.getConcepts().add(vsacConcept2);
    conceptList.getConcepts().add(vsacConcept3);
    conceptList.getConcepts().add(vsacConcept4);
    conceptList.getConcepts().add(vsacConcept5);
    describedValueSet.setConceptList(conceptList);

    vsacValuesetResponse.setDescribedValueSet(describedValueSet);

    ValueSet fhirValueSet = mapper.convertToFHIRValueSet(vsacValuesetResponse, TEST_ID);

    assertEquals(fhirValueSet.getId(), TEST_ID);
    assertEquals(fhirValueSet.getName(), TEST);
    assertEquals(fhirValueSet.getTitle(), TEST);
    assertEquals(fhirValueSet.getVersion(), TEST);
    assertEquals(fhirValueSet.getStatus().getDisplay(), "Active");
    assertEquals(fhirValueSet.getPublisher(), TEST);
    assertEquals(fhirValueSet.getDescription(), TEST);
    assertEquals(fhirValueSet.getPurpose(), TEST);
    assertEquals(fhirValueSet.getDate(), today);

    assertEquals(fhirValueSet.getCompose().getInclude().size(), 2);
  }

  private Concept getVsacConcept(
      String code,
      String codeSystem,
      String codeSystemName,
      String codeSystemVersion,
      String displayName) {
    Concept vsacConcept = new Concept();
    vsacConcept.setCode(code);
    vsacConcept.setCodeSystem(codeSystem);
    vsacConcept.setCodeSystemName(codeSystemName);
    vsacConcept.setCodeSystemVersion(codeSystemVersion);
    vsacConcept.setDisplayName(displayName);
    return vsacConcept;
  }
}
