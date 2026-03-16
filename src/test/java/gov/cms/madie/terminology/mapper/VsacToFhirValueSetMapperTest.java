package gov.cms.madie.terminology.mapper;

import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse.DescribedValueSet;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse.DescribedValueSet.ConceptList;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse.DescribedValueSet.ConceptList.Concept;
import gov.cms.madie.models.mapping.CodeSystemEntry;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r4.model.ValueSet.ValueSetComposeComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class VsacToFhirValueSetMapperTest {

  @InjectMocks private VsacToFhirValueSetMapper mapper;

  @Mock CodeSystemRepository codeSystemRepository;

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

  List<CodeSystemEntry> codeSystemEntries;
  private static final String TEST_OID = "2.16.840.1.113883.3.464.1003.101.12.1001";
  private static final String TEST_URL = "http://test.com";

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

    codeSystemEntries = new ArrayList<>();
    CodeSystemEntry entry =
        CodeSystemEntry.builder().name(TEST).oid(TEST_OID).url(TEST_URL).build();
    codeSystemEntries.add(entry);
  }

  @Test
  public void testSetFhirMainAttributes() {
    ValueSet vs = new ValueSet();

    vs = mapper.setFhirMainAttributes(vs, describedValueSet, TEST);

    assertEquals(TEST_ID, vs.getId());
    assertEquals(TEST, vs.getName());
    assertEquals(TEST, vs.getTitle());
    assertEquals(TEST, vs.getVersion());
    assertEquals("Active", vs.getStatus().getDisplay());
    assertEquals(TEST, vs.getPublisher());
    assertEquals(TEST, vs.getDescription());
    assertEquals(TEST, vs.getPurpose());
    assertEquals(today, vs.getDate());
  }

  @Test
  public void testMapMainAttributesUnknownStatus() {
    ValueSet vs = new ValueSet();
    describedValueSet.setStatus("Not Maintained");
    vs = mapper.setFhirMainAttributes(vs, describedValueSet, TEST);
    assertEquals(Enumerations.PublicationStatus.UNKNOWN.getDisplay(), vs.getStatus().getDisplay());
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
    assertEquals(3, result.size());
    result = mapper.getVsacConceptListByCode("2.16.840.1.113883.6.12", vsacConceptList);
    assertEquals(2, result.size());
    result = mapper.getVsacConceptListByCode(TEST, vsacConceptList);
    assertEquals(0, result.size());
  }

  @Test
  public void testGetVsacVersionMap() {
    Map<String, String> vsacVersionMap = mapper.getVsacVersionMap(vsacConceptList);
    assertEquals(2, vsacVersionMap.size());
  }

  @Test
  public void testGetVsacConceptListByCodeAndVersion() {
    List<Concept> conceptListByCodeAndVersion =
        mapper.getVsacConceptListByCodeAndVersion(
            "2.16.840.1.113883.6.96", "2022-03", vsacConceptList);
    assertEquals(3, conceptListByCodeAndVersion.size());

    conceptListByCodeAndVersion =
        mapper.getVsacConceptListByCodeAndVersion(
            "2.16.840.1.113883.6.12", "2021", vsacConceptList);
    assertEquals(2, conceptListByCodeAndVersion.size());

    conceptListByCodeAndVersion =
        mapper.getVsacConceptListByCodeAndVersion(
            "2.16.840.1.113883.6.12", "2022-03", vsacConceptList);
    assertEquals(0, conceptListByCodeAndVersion.size());
  }

  @Test
  public void testGetConceptMapByCodeAndVersion() {
    Map<String, List<Concept>> conceptListMap =
        mapper.getConceptMapByCodeAndVersion(vsacConceptList);
    assertEquals(2, conceptListMap.size());
  }

  @Test
  public void testCreateFhirConceptSetComponent() {
    ConceptReferenceComponent result = mapper.createFhirConceptSetComponent(vsacConcept1);
    assertEquals("185460008", result.getCode());
    assertEquals("Home visit request by patient (procedure)", result.getDisplay());
  }

  @Test
  public void testCreateFhirConceptReferenceComponents() {
    List<ConceptReferenceComponent> result =
        mapper.createFhirConceptReferenceComponents(vsacConceptList);
    assertEquals(5, result.size());
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

    assertEquals(2, fhirValueSet.getCompose().getInclude().size());
  }

  @Test
  public void testAddFhirValueSetComposeComponent() {
    ValueSet fhirValueSet = new ValueSet();

    mapper.addFhirValueSetComposeComponent(vsacConceptList, fhirValueSet);

    assertEquals(2, fhirValueSet.getCompose().getInclude().size());
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

    ValueSet fhirValueSet = mapper.convertToFHIRValueSet(vsacValuesetResponse);

    assertEquals(TEST_ID, fhirValueSet.getId());
    assertEquals(TEST, fhirValueSet.getName());
    assertEquals(TEST, fhirValueSet.getTitle());
    assertEquals(TEST, fhirValueSet.getVersion());
    assertEquals("Active", fhirValueSet.getStatus().getDisplay());
    assertEquals(TEST, fhirValueSet.getPublisher());
    assertEquals(TEST, fhirValueSet.getDescription());
    assertEquals(TEST, fhirValueSet.getPurpose());
    assertEquals(fhirValueSet.getDate(), today);

    assertEquals(2, fhirValueSet.getCompose().getInclude().size());
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

  @Test
  void getUrlByOidReturnsFullUrlWhenCodeSystemVersionIsNotBlank() {
    String oid = "testOid";
    String fullUrl = "http://example.com/fhir/CodeSystem/testOid";
    gov.cms.madie.terminology.models.CodeSystem codeSystem =
        gov.cms.madie.terminology.models.CodeSystem.builder().oid(oid).fullUrl(fullUrl).build();
    List<gov.cms.madie.terminology.models.CodeSystem> codeSystemVersions = List.of(codeSystem);
    org.mockito.Mockito.when(codeSystemRepository.findAllByOid(oid)).thenReturn(codeSystemVersions);
    String result = mapper.getUrlByOid(oid);
    assertEquals(fullUrl, result);
  }

  @Test
  void getUrlByOidReturnsOidWhenCodeSystemVersionFullUrlIsBlank() {
    String oid = "testOid";
    gov.cms.madie.terminology.models.CodeSystem codeSystem =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .oid(oid)
            .fullUrl("") // blank fullUrl
            .build();
    List<gov.cms.madie.terminology.models.CodeSystem> codeSystemVersions = List.of(codeSystem);
    org.mockito.Mockito.when(codeSystemRepository.findAllByOid(oid)).thenReturn(codeSystemVersions);
    String result = mapper.getUrlByOid(oid);
    assertEquals(oid, result);
  }

  @Test
  void convertToFHIRValueSetHandlesNullConceptList() {
    RetrieveMultipleValueSetsResponse vsacValueSetResponse =
        new RetrieveMultipleValueSetsResponse();
    DescribedValueSet describedValueSet = new DescribedValueSet();
    describedValueSet.setID("testId");
    describedValueSet.setConceptList(null); // ConceptList is null
    // Set revisionDate to avoid NullPointerException
    GregorianCalendar gregory = new GregorianCalendar();
    gregory.setTime(today);
    try {
      XMLGregorianCalendar todayXMLGregorianCalendar =
          DatatypeFactory.newInstance().newXMLGregorianCalendar(gregory);
      describedValueSet.setRevisionDate(todayXMLGregorianCalendar);
    } catch (DatatypeConfigurationException e) {
      e.printStackTrace();
    }
    vsacValueSetResponse.setDescribedValueSet(describedValueSet);
    ValueSet fhirValueSet = mapper.convertToFHIRValueSet(vsacValueSetResponse);
    assertEquals(
        0, fhirValueSet.getCompose() == null ? 0 : fhirValueSet.getCompose().getInclude().size());
  }

  @Test
  void convertToFHIRValueSetHandlesEmptyConceptList() {
    RetrieveMultipleValueSetsResponse vsacValueSetResponse =
        new RetrieveMultipleValueSetsResponse();
    DescribedValueSet describedValueSet = new DescribedValueSet();
    describedValueSet.setID("testId");
    ConceptList conceptList = new ConceptList();
    conceptList.getConcepts().clear(); // Ensure concepts list is empty
    describedValueSet.setConceptList(conceptList);
    // Set revisionDate to avoid NullPointerException
    GregorianCalendar gregory = new GregorianCalendar();
    gregory.setTime(today);
    try {
      XMLGregorianCalendar todayXMLGregorianCalendar =
          DatatypeFactory.newInstance().newXMLGregorianCalendar(gregory);
      describedValueSet.setRevisionDate(todayXMLGregorianCalendar);
    } catch (DatatypeConfigurationException e) {
      e.printStackTrace();
    }
    vsacValueSetResponse.setDescribedValueSet(describedValueSet);
    ValueSet fhirValueSet = mapper.convertToFHIRValueSet(vsacValueSetResponse);
    assertEquals(
        0, fhirValueSet.getCompose() == null ? 0 : fhirValueSet.getCompose().getInclude().size());
  }
}
