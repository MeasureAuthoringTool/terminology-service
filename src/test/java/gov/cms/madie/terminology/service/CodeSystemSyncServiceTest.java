package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeSystemSyncServiceTest {
  @Mock private FhirContext fhirContext;
  @Mock private FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;
  @Mock private CodeSystemRepository codeSystemRepository;
  @InjectMocks private CodeSystemSyncService codeSystemSyncService;

  private UmlsUser umlsUser;
  private static final String TEST_HARP_ID = "te$tHarpId";
  private static final String TEST_API_KEY = "te$tKey";

  private final String mockCodeSystemsResource =
      "{\"resourceType\":\"Bundle\",\"id\":\"codesystem-search\",\"meta\":{\"lastUpdated\":\"2024-03-28T15:04:59.375-04:00\"},\"type\":\"searchset\",\"total\":831,\"link\":[{\"relation\":\"self\",\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=500&_count=2\"},{\"relation\":\"first\",\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=0&_count=2\"},{\"relation\":\"previous\",\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=498&_count=2\"},{\"relation\":\"last\",\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=829&_count=2\"}],\"entry\":[{\"fullUrl\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\",\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"ObservationInterpretation\",\"meta\":{\"versionId\":\"1710382394\",\"lastUpdated\":\"2019-04-25T00:00:00.000-04:00\",\"profile\":[\"http://hl7.org/fhir/StructureDefinition/shareablecodesystem\"]},\"url\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\",\"identifier\":[{\"system\":\"urn:ietf:rfc:3986\",\"value\":\"urn:oid:2.16.840.1.113883.5.83\"}],\"version\":\"2019-03-01\",\"name\":\"ObservationInterpretation\",\"title\":\"ObservationInterpretation\",\"status\":\"active\",\"experimental\":false,\"date\":\"2019-04-15T00:00:00-04:00\",\"_publisher\":{\"extension\":[{\"url\":\"http://hl7.org/fhir/StructureDefinition/data-absent-reason\",\"valueCode\":\"unknown\"}]},\"content\":\"complete\",\"count\":57,\"concept\":[{\"code\":\"<\",\"display\":\"Offscalelow\"},{\"code\":\">\",\"display\":\"Offscalehigh\"},{\"code\":\"A\",\"display\":\"Abnormal\"},{\"code\":\"AA\",\"display\":\"Criticalabnormal\"},{\"code\":\"AC\",\"display\":\"Anti-complementarysubstancespresent\"},{\"code\":\"B\",\"display\":\"Better\"},{\"code\":\"CAR\",\"display\":\"Carrier\"},{\"code\":\"Carrier\",\"display\":\"Carrier\"},{\"code\":\"D\",\"display\":\"Significantchangedown\"},{\"code\":\"DET\",\"display\":\"Detected\"},{\"code\":\"E\",\"display\":\"Equivocal\"},{\"code\":\"EX\",\"display\":\"outsidethreshold\"},{\"code\":\"EXP\",\"display\":\"Expected\"},{\"code\":\"H\",\"display\":\"High\"},{\"code\":\"H>\",\"display\":\"Significantlyhigh\"},{\"code\":\"HH\",\"display\":\"Criticalhigh\"},{\"code\":\"HM\",\"display\":\"HoldforMedicalReview\"},{\"code\":\"HU\",\"display\":\"Significantlyhigh\"},{\"code\":\"HX\",\"display\":\"abovehighthreshold\"},{\"code\":\"I\",\"display\":\"Intermediate\"},{\"code\":\"IE\",\"display\":\"Insufficientevidence\"},{\"code\":\"IND\",\"display\":\"Indeterminate\"},{\"code\":\"L\",\"display\":\"Low\"},{\"code\":\"L<\",\"display\":\"Significantlylow\"},{\"code\":\"LL\",\"display\":\"Criticallow\"},{\"code\":\"LU\",\"display\":\"Significantlylow\"},{\"code\":\"LX\",\"display\":\"belowlowthreshold\"},{\"code\":\"MS\",\"display\":\"moderatelysusceptible\"},{\"code\":\"N\",\"display\":\"Normal\"},{\"code\":\"NCL\",\"display\":\"NoCLSIdefinedbreakpoint\"},{\"code\":\"ND\",\"display\":\"Notdetected\"},{\"code\":\"NEG\",\"display\":\"Negative\"},{\"code\":\"NR\",\"display\":\"Non-reactive\"},{\"code\":\"NS\",\"display\":\"Non-susceptible\"},{\"code\":\"OBX\",\"display\":\"InterpretationqualifiersinseparateOBXsegments\"},{\"code\":\"ObservationInterpretationDetection\",\"display\":\"ObservationInterpretationDetection\"},{\"code\":\"ObservationInterpretationExpectation\",\"display\":\"ObservationInterpretationExpectation\"},{\"code\":\"POS\",\"display\":\"Positive\"},{\"code\":\"QCF\",\"display\":\"Qualitycontrolfailure\"},{\"code\":\"R\",\"display\":\"Resistant\"},{\"code\":\"RR\",\"display\":\"Reactive\"},{\"code\":\"ReactivityObservationInterpretation\",\"display\":\"ReactivityObservationInterpretation\"},{\"code\":\"S\",\"display\":\"Susceptible\"},{\"code\":\"SDD\",\"display\":\"Susceptible-dosedependent\"},{\"code\":\"SYN-R\",\"display\":\"Synergy-resistant\"},{\"code\":\"SYN-S\",\"display\":\"Synergy-susceptible\"},{\"code\":\"TOX\",\"display\":\"Cytotoxicsubstancepresent\"},{\"code\":\"U\",\"display\":\"Significantchangeup\"},{\"code\":\"UNE\",\"display\":\"Unexpected\"},{\"code\":\"VS\",\"display\":\"verysusceptible\"},{\"code\":\"W\",\"display\":\"Worse\"},{\"code\":\"WR\",\"display\":\"Weaklyreactive\"},{\"code\":\"_GeneticObservationInterpretation\",\"display\":\"GeneticObservationInterpretation\"},{\"code\":\"_ObservationInterpretationChange\",\"display\":\"ObservationInterpretationChange\"},{\"code\":\"_ObservationInterpretationExceptions\",\"display\":\"ObservationInterpretationExceptions\"},{\"code\":\"_ObservationInterpretationNormality\",\"display\":\"ObservationInterpretationNormality\"},{\"code\":\"_ObservationInterpretationSusceptibility\",\"display\":\"ObservationInterpretationSusceptibility\"}]}},{\"fullUrl\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\",\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"ObservationInterpretation\",\"meta\":{\"versionId\":\"1305437570\",\"lastUpdated\":\"2020-01-16T00:00:00.000-05:00\",\"profile\":[\"http://hl7.org/fhir/StructureDefinition/shareablecodesystem\"]},\"url\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\",\"identifier\":[{\"system\":\"urn:ietf:rfc:3986\",\"value\":\"urn:oid:2.16.840.1.113883.5.83\"}],\"version\":\"2019-12-01\",\"name\":\"ObservationInterpretation\",\"title\":\"ObservationInterpretation\",\"status\":\"active\",\"experimental\":false,\"date\":\"2019-12-27T00:00:00-05:00\",\"_publisher\":{\"extension\":[{\"url\":\"http://hl7.org/fhir/StructureDefinition/data-absent-reason\",\"valueCode\":\"unknown\"}]},\"content\":\"complete\",\"count\":57,\"concept\":[{\"code\":\"<\",\"display\":\"Offscalelow\"},{\"code\":\">\",\"display\":\"Offscalehigh\"},{\"code\":\"A\",\"display\":\"Abnormal\"},{\"code\":\"AA\",\"display\":\"Criticalabnormal\"},{\"code\":\"AC\",\"display\":\"Anti-complementarysubstancespresent\"},{\"code\":\"B\",\"display\":\"Better\"},{\"code\":\"CAR\",\"display\":\"Carrier\"},{\"code\":\"Carrier\",\"display\":\"Carrier\"},{\"code\":\"D\",\"display\":\"Significantchangedown\"},{\"code\":\"DET\",\"display\":\"Detected\"},{\"code\":\"E\",\"display\":\"Equivocal\"},{\"code\":\"EX\",\"display\":\"outsidethreshold\"},{\"code\":\"EXP\",\"display\":\"Expected\"},{\"code\":\"H\",\"display\":\"High\"},{\"code\":\"H>\",\"display\":\"Significantlyhigh\"},{\"code\":\"HH\",\"display\":\"Criticalhigh\"},{\"code\":\"HM\",\"display\":\"HoldforMedicalReview\"},{\"code\":\"HU\",\"display\":\"Significantlyhigh\"},{\"code\":\"HX\",\"display\":\"abovehighthreshold\"},{\"code\":\"I\",\"display\":\"Intermediate\"},{\"code\":\"IE\",\"display\":\"Insufficientevidence\"},{\"code\":\"IND\",\"display\":\"Indeterminate\"},{\"code\":\"L\",\"display\":\"Low\"},{\"code\":\"L<\",\"display\":\"Significantlylow\"},{\"code\":\"LL\",\"display\":\"Criticallow\"},{\"code\":\"LU\",\"display\":\"Significantlylow\"},{\"code\":\"LX\",\"display\":\"belowlowthreshold\"},{\"code\":\"MS\",\"display\":\"moderatelysusceptible\"},{\"code\":\"N\",\"display\":\"Normal\"},{\"code\":\"NCL\",\"display\":\"NoCLSIdefinedbreakpoint\"},{\"code\":\"ND\",\"display\":\"Notdetected\"},{\"code\":\"NEG\",\"display\":\"Negative\"},{\"code\":\"NR\",\"display\":\"Non-reactive\"},{\"code\":\"NS\",\"display\":\"Non-susceptible\"},{\"code\":\"OBX\",\"display\":\"InterpretationqualifiersinseparateOBXsegments\"},{\"code\":\"ObservationInterpretationDetection\",\"display\":\"ObservationInterpretationDetection\"},{\"code\":\"ObservationInterpretationExpectation\",\"display\":\"ObservationInterpretationExpectation\"},{\"code\":\"POS\",\"display\":\"Positive\"},{\"code\":\"QCF\",\"display\":\"Qualitycontrolfailure\"},{\"code\":\"R\",\"display\":\"Resistant\"},{\"code\":\"RR\",\"display\":\"Reactive\"},{\"code\":\"ReactivityObservationInterpretation\",\"display\":\"ReactivityObservationInterpretation\"},{\"code\":\"S\",\"display\":\"Susceptible\"},{\"code\":\"SDD\",\"display\":\"Susceptible-dosedependent\"},{\"code\":\"SYN-R\",\"display\":\"Synergy-resistant\"},{\"code\":\"SYN-S\",\"display\":\"Synergy-susceptible\"},{\"code\":\"TOX\",\"display\":\"Cytotoxicsubstancepresent\"},{\"code\":\"U\",\"display\":\"Significantchangeup\"},{\"code\":\"UNE\",\"display\":\"Unexpected\"},{\"code\":\"VS\",\"display\":\"verysusceptible\"},{\"code\":\"W\",\"display\":\"Worse\"},{\"code\":\"WR\",\"display\":\"Weaklyreactive\"},{\"code\":\"_GeneticObservationInterpretation\",\"display\":\"GeneticObservationInterpretation\"},{\"code\":\"_ObservationInterpretationChange\",\"display\":\"ObservationInterpretationChange\"},{\"code\":\"_ObservationInterpretationExceptions\",\"display\":\"ObservationInterpretationExceptions\"},{\"code\":\"_ObservationInterpretationNormality\",\"display\":\"ObservationInterpretationNormality\"},{\"code\":\"_ObservationInterpretationSusceptibility\",\"display\":\"ObservationInterpretationSusceptibility\"}]}}]}";

  @BeforeEach
  public void setUp() throws IOException {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
  }

  @Test
  void testRetrieveAllCodeSystems() {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    Bundle bundle = new Bundle();
    var identifierList = new ArrayList<Identifier>();
    var i1 = new Identifier().setValue("codeUrl");
    identifierList.add(i1);
    var m1 = new Meta();
    m1.setVersionId("vid");
    m1.setLastUpdated(new Date());
    var c1 =
        new org.hl7.fhir.r4.model.CodeSystem()
            .setTitle("title")
            .setName("name1")
            .setVersion("version")
            .setIdentifier(identifierList)
            .setMeta(m1)
            .setId("titleversion");

    var identifierList2 = new ArrayList<Identifier>();
    var i2 = new Identifier().setValue("codeUrl");
    identifierList2.add(i2);
    var m2 = new Meta();
    m2.setVersionId("vid");
    m2.setLastUpdated(new Date());
    var c2 =
        new org.hl7.fhir.r4.model.CodeSystem()
            .setTitle("title")
            .setName("name2")
            .setVersion("version")
            .setIdentifier(identifierList2)
            .setMeta(m2)
            .setId("titleversion");
    bundle.addEntry().setResource(c1);
    Bundle.BundleEntryComponent t =
        new Bundle.BundleEntryComponent()
            .setFullUrl("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation")
            .setResource(c2);
    bundle.addEntry(t);

    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(mockCodeSystemsResource);

    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());

    // call method under test and assert results
    List<CodeSystem> resultList =
        codeSystemSyncService.retrieveAllCodeSystems(umlsUser);
    assertEquals(2, resultList.size());
    assertEquals(resultList.get(1).getFullUrl(), t.getFullUrl());
    verify(codeSystemRepository, times(2))
        .save(any(CodeSystem.class));
  }

  @Test
  void testRetrieveAllCodeSystemsWithInsert() {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    Bundle bundle = new Bundle();
    var c1 = new org.hl7.fhir.r4.model.CodeSystem();
    var identifierList = new ArrayList<Identifier>();
    var i1 = new Identifier().setValue("codeUrl");
    identifierList.add(i1);
    var m1 = new Meta();
    m1.setVersionId("vid");
    m1.setLastUpdated(new Date());
    c1.setId("titleversion");
    c1.setTitle("title");
    c1.setName("name1");
    c1.setVersion("version");
    c1.setMeta(m1);
    c1.setIdentifier(identifierList);
    bundle.addEntry().setResource(c1);

    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(mockCodeSystemsResource);
    var existingCodeSystem =
        CodeSystem.builder()
            .id("titleversion")
            .title("title")
            .name("name1")
            .version(
                CodeSystem.Version.builder()
                    .fhirVersion("version")
                    .build())
            .versionId("vid")
            .oid("codeUrl")
            .lastUpdated(Instant.now())
            .lastUpdatedUpstream(new Date())
            .isInVsac(false)
            .build();

    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.ofNullable(existingCodeSystem));

    codeSystemSyncService.retrieveAllCodeSystems(umlsUser);

    verify(codeSystemRepository, times(2))
        .save(any(CodeSystem.class));
  }

  @Test
  void testRetrieveAllCodeSystemsSkipsNullStringFhirVersion() {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    // Bundle with a single entry whose version is the literal string "null"
    String nullVersionBundle =
        "{\"resourceType\":\"Bundle\",\"id\":\"cs-search\",\"meta\":{\"lastUpdated\":\"2024-03-28T15:04:59.375-04:00\"},"
            + "\"type\":\"searchset\",\"total\":1,\"link\":[{\"relation\":\"self\","
            + "\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=0&_count=50\"}],"
            + "\"entry\":[{\"fullUrl\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\","
            + "\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"ObservationInterpretation\","
            + "\"meta\":{\"versionId\":\"1710382394\",\"lastUpdated\":\"2019-04-25T00:00:00.000-04:00\"},"
            + "\"url\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\","
            + "\"identifier\":[{\"system\":\"urn:ietf:rfc:3986\",\"value\":\"urn:oid:2.16.840.1.113883.5.83\"}],"
            + "\"version\":\"null\",\"name\":\"ObservationInterpretation\","
            + "\"title\":\"ObservationInterpretation\",\"status\":\"active\"}}]}";

    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(nullVersionBundle);

    List<CodeSystem> result =
        codeSystemSyncService.retrieveAllCodeSystems(umlsUser);

    assertEquals(1, result.size());
    assertEquals(
        "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
        result.get(0).getFullUrl());
    assertEquals("null", result.get(0).getVersion().getFhirVersion());
    verify(codeSystemRepository, never())
        .save(any(CodeSystem.class));
    verify(codeSystemRepository, never())
        .findByFullUrlAndVersionFhirVersion(anyString(), anyString());
  }

  @Test
  void testRetrieveAllCodeSystemsSkipsBlankFhirVersion() {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    // Bundle with a single entry whose version is missing.
    String blankVersionBundle =
        "{\"resourceType\":\"Bundle\",\"id\":\"cs-search\",\"meta\":{\"lastUpdated\":\"2024-03-28T15:04:59.375-04:00\"},"
            + "\"type\":\"searchset\",\"total\":1,\"link\":[{\"relation\":\"self\","
            + "\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=0&_count=50\"}],"
            + "\"entry\":[{\"fullUrl\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\","
            + "\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"ObservationInterpretation\","
            + "\"meta\":{\"versionId\":\"1710382394\",\"lastUpdated\":\"2019-04-25T00:00:00.000-04:00\"},"
            + "\"url\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\","
            + "\"identifier\":[{\"system\":\"urn:ietf:rfc:3986\",\"value\":\"urn:oid:2.16.840.1.113883.5.83\"}],"
            // missing version field.
            + " \"name\":\"ObservationInterpretation\","
            + "\"title\":\"ObservationInterpretation\",\"status\":\"active\"}}]}";

    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(blankVersionBundle);

    List<CodeSystem> result =
        codeSystemSyncService.retrieveAllCodeSystems(umlsUser);

    assertEquals(1, result.size());
    assertEquals(
        "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
        result.get(0).getFullUrl());
    assertNull(result.get(0).getVersion().getFhirVersion());
    verify(codeSystemRepository, never())
        .save(any(CodeSystem.class));
    verify(codeSystemRepository, never())
        .findByFullUrlAndVersionFhirVersion(anyString(), anyString());
  }

  @Test
  void testRetrieveAllCodeSystemsSkipsNullFullUrl() {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    // Bundle with a single entry whose resource has no url (fullUrl will be null)
    String nullStringUrlBundle =
        "{\"resourceType\":\"Bundle\",\"id\":\"cs-search\",\"meta\":{\"lastUpdated\":\"2024-03-28T15:04:59.375-04:00\"},"
            + "\"type\":\"searchset\",\"total\":1,\"link\":[{\"relation\":\"self\","
            + "\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=0&_count=50\"}],"
            + "\"entry\":[{\"fullUrl\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\","
            + "\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"ObservationInterpretation\","
            + "\"meta\":{\"versionId\":\"1710382394\",\"lastUpdated\":\"2019-04-25T00:00:00.000-04:00\"},"
            + "\"url\":\"null\","
            + "\"identifier\":[{\"system\":\"urn:ietf:rfc:3986\",\"value\":\"urn:oid:2.16.840.1.113883.5.83\"}],"
            + "\"version\":\"2019-03-01\",\"name\":\"ObservationInterpretation\","
            + "\"title\":\"ObservationInterpretation\",\"status\":\"active\"}}]}";

    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(nullStringUrlBundle);

    List<CodeSystem> result =
        codeSystemSyncService.retrieveAllCodeSystems(umlsUser);

    assertEquals(1, result.size());
    assertEquals("null", result.get(0).getFullUrl());
    verify(codeSystemRepository, never())
        .save(any(CodeSystem.class));
    verify(codeSystemRepository, never())
        .findByFullUrlAndVersionFhirVersion(anyString(), anyString());
  }

  @Test
  void testRetrieveAllCodeSystemsSkipsBlankFullUrl() {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    // Bundle with a single entry whose resource has no url (fullUrl will be null)
    String noUrlBundle =
        "{\"resourceType\":\"Bundle\",\"id\":\"cs-search\",\"meta\":{\"lastUpdated\":\"2024-03-28T15:04:59.375-04:00\"},"
            + "\"type\":\"searchset\",\"total\":1,\"link\":[{\"relation\":\"self\","
            + "\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=0&_count=50\"}],"
            + "\"entry\":[{\"fullUrl\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\","
            + "\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"ObservationInterpretation\","
            + "\"meta\":{\"versionId\":\"1710382394\",\"lastUpdated\":\"2019-04-25T00:00:00.000-04:00\"},"
            + "\"identifier\":[{\"system\":\"urn:ietf:rfc:3986\",\"value\":\"urn:oid:2.16.840.1.113883.5.83\"}],"
            + "\"version\":\"2019-03-01\",\"name\":\"ObservationInterpretation\","
            + "\"title\":\"ObservationInterpretation\",\"status\":\"active\"}}]}";

    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(noUrlBundle);

    List<CodeSystem> result =
        codeSystemSyncService.retrieveAllCodeSystems(umlsUser);

    assertEquals(1, result.size());
    assertNull(result.get(0).getFullUrl());
    verify(codeSystemRepository, never())
        .save(any(CodeSystem.class));
    verify(codeSystemRepository, never())
        .findByFullUrlAndVersionFhirVersion(anyString(), anyString());
  }

  @Test
  void retrieveAllCodeSystemsParsesOffsetAndCountFromNextLinkAndRecurses() {
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    // build first bundle with one CodeSystem and a next link containing _offset and _count
    org.hl7.fhir.r4.model.CodeSystem cs1 = new org.hl7.fhir.r4.model.CodeSystem();
    cs1.setTitle("title1");
    cs1.setName("name1");
    cs1.setVersion("v1");
    cs1.setId("title1v1");
    cs1.setUrl("http://example.com/cs1");
    var id1 = new ArrayList<Identifier>();
    id1.add(new Identifier().setValue("codeUrl1"));
    Meta m1 = new Meta();
    m1.setVersionId("vid1");
    m1.setLastUpdated(new Date());
    cs1.setMeta(m1);
    cs1.setIdentifier(id1);

    Bundle bundle1 = new Bundle();
    bundle1.addEntry(new Bundle.BundleEntryComponent().setResource(cs1));
    bundle1.addLink(
        new Bundle.BundleLinkComponent()
            .setRelation("next")
            .setUrl("http://example.com/res/CodeSystem?_offset=50&_count=50"));

    // second bundle (recursive result) with another CodeSystem
    org.hl7.fhir.r4.model.CodeSystem cs2 = new org.hl7.fhir.r4.model.CodeSystem();
    cs2.setTitle("title2");
    cs2.setName("name2");
    cs2.setVersion("v2");
    cs2.setId("title2v2");
    cs2.setUrl("http://example.com/cs2");
    var id2 = new ArrayList<Identifier>();
    id2.add(new Identifier().setValue("codeUrl2"));
    Meta m2 = new Meta();
    m2.setVersionId("vid2");
    m2.setLastUpdated(new Date());
    cs2.setMeta(m2);
    cs2.setIdentifier(id2);

    Bundle bundle2 = new Bundle();
    bundle2.addEntry(new Bundle.BundleEntryComponent().setResource(cs2));

    IParser parser = FhirContext.forR4().newJsonParser();
    String json1 = parser.encodeResourceToString(bundle1);
    String json2 = parser.encodeResourceToString(bundle2);

    // initial page invoked by retrieveAllCodeSystems -> return page for offset=0,count=50
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(eq(0), eq(50), anyString()))
        .thenReturn(json1);
    // return page for the recursive offset=50,count=50
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(eq(50), eq(50), anyString()))
        .thenReturn(json2);

    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    var result = codeSystemSyncService.retrieveAllCodeSystems(umlsUser);

    // should collect both code systems from initial + recursive
    assertEquals(2, result.size());
    // calls getCodeSystemsPage(offset,count,apiKey) when retrieving pages
    verify(fhirTerminologyServiceWebClient, atLeast(1))
        .getCodeSystemsPage(anyInt(), anyInt(), eq(TEST_API_KEY));
  }

  @Test
  void retrieveAllCodeSystemsHandlesDataAccessException() {
    UmlsUser umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    // Mock repository to throw DataAccessException when save is called
    doThrow(new DataAccessException("Simulated DB error") {})
        .when(codeSystemRepository)
        .save(any());
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(
            "{\"resourceType\":\"Bundle\",\"entry\":[{\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"cs1\",\"url\":\"http://lonic.org\",\"name\":\"LOINC\",\"version\":\"2.40\",\"title\":\"LOINC\",\"identifier\":[{\"value\":\"urn:oid:2.16.840.1.113883.6.1\"}]}}]}");
    // Call the method under test
    List<CodeSystem> result =
        codeSystemSyncService.retrieveAllCodeSystems(umlsUser);
    // Assert that the result is not empty and the error branch was triggered
    assertFalse(result.isEmpty());
    assertEquals("LOINC", result.get(0).getName());
  }

  @Test
  void retrieveAllCodeSystemsOffsetNullFromNextLinkAssertsNotNull() {
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    // Build a bundle with a next link containing only _count
    org.hl7.fhir.r4.model.CodeSystem cs1 = new org.hl7.fhir.r4.model.CodeSystem();
    cs1.setTitle("title1");
    cs1.setName("name1");
    cs1.setVersion("v1");
    cs1.setId("title1v1");
    cs1.setUrl("http://example.com/cs1");
    var id1 = new ArrayList<Identifier>();
    id1.add(new Identifier().setValue("codeUrl1"));
    Meta m1 = new Meta();
    m1.setVersionId("vid1");
    m1.setLastUpdated(new Date());
    cs1.setMeta(m1);
    cs1.setIdentifier(id1);

    Bundle bundle1 = new Bundle();
    bundle1.addEntry(new Bundle.BundleEntryComponent().setResource(cs1));
    bundle1.addLink(
        new Bundle.BundleLinkComponent()
            .setRelation("next")
            .setUrl("http://example.com/res/CodeSystem?_count=50"));

    // Second bundle (recursive result) with another CodeSystem
    org.hl7.fhir.r4.model.CodeSystem cs2 = new org.hl7.fhir.r4.model.CodeSystem();
    cs2.setTitle("title2");
    cs2.setName("name2");
    cs2.setVersion("v2");
    cs2.setId("title2v2");
    cs2.setUrl("http://example.com/cs2");
    var id2 = new ArrayList<Identifier>();
    id2.add(new Identifier().setValue("codeUrl2"));
    Meta m2 = new Meta();
    m2.setVersionId("vid2");
    m2.setLastUpdated(new Date());
    cs2.setMeta(m2);
    cs2.setIdentifier(id2);

    Bundle bundle2 = new Bundle();
    bundle2.addEntry(new Bundle.BundleEntryComponent().setResource(cs2));

    IParser parser = FhirContext.forR4().newJsonParser();
    String json1 = parser.encodeResourceToString(bundle1);

    // initial page invoked by retrieveAllCodeSystems -> return page for offset=0,count=50
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(eq(0), eq(50), anyString()))
        .thenReturn(json1);

    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();

    assertThrows(
        AssertionError.class, () -> codeSystemSyncService.retrieveAllCodeSystems(umlsUser));
  }

  @Test
  void retrieveAllCodeSystemsCountNullFromNextLinkAssertsNotNull() {
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    // Build a bundle with a next link containing only _offset
    org.hl7.fhir.r4.model.CodeSystem cs1 = new org.hl7.fhir.r4.model.CodeSystem();
    cs1.setTitle("title1");
    cs1.setName("name1");
    cs1.setVersion("v1");
    cs1.setId("title1v1");
    cs1.setUrl("http://example.com/cs1");
    var id1 = new ArrayList<Identifier>();
    id1.add(new Identifier().setValue("codeUrl1"));
    Meta m1 = new Meta();
    m1.setVersionId("vid1");
    m1.setLastUpdated(new Date());
    cs1.setMeta(m1);
    cs1.setIdentifier(id1);

    Bundle bundle1 = new Bundle();
    bundle1.addEntry(new Bundle.BundleEntryComponent().setResource(cs1));
    bundle1.addLink(
        new Bundle.BundleLinkComponent()
            .setRelation("next")
            .setUrl("http://example.com/res/CodeSystem?_offset=50"));

    // Second bundle (recursive result) with another CodeSystem
    org.hl7.fhir.r4.model.CodeSystem cs2 = new org.hl7.fhir.r4.model.CodeSystem();
    cs2.setTitle("title2");
    cs2.setName("name2");
    cs2.setVersion("v2");
    cs2.setId("title2v2");
    cs2.setUrl("http://example.com/cs2");
    var id2 = new ArrayList<Identifier>();
    id2.add(new Identifier().setValue("codeUrl2"));
    Meta m2 = new Meta();
    m2.setVersionId("vid2");
    m2.setLastUpdated(new Date());
    cs2.setMeta(m2);
    cs2.setIdentifier(id2);

    Bundle bundle2 = new Bundle();
    bundle2.addEntry(new Bundle.BundleEntryComponent().setResource(cs2));

    IParser parser = FhirContext.forR4().newJsonParser();
    String json1 = parser.encodeResourceToString(bundle1);

    // initial page invoked by retrieveAllCodeSystems -> return page for offset=0,count=50
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(eq(0), eq(50), anyString()))
        .thenReturn(json1);

    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();

    assertThrows(
        AssertionError.class, () -> codeSystemSyncService.retrieveAllCodeSystems(umlsUser));
  }
}
