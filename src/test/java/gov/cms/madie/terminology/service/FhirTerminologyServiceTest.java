package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.Code;
import gov.cms.madie.terminology.dto.CodeStatus;
import gov.cms.madie.terminology.dto.QdmValueSet;
import gov.cms.madie.terminology.dto.ValueSetForSearch;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.exceptions.CodeSystemNotFoundException;
import gov.cms.madie.terminology.exceptions.DuplicateCodeSystemException;
import gov.cms.madie.terminology.exceptions.VsacParseBatchValueSetExpansionException;
// local resource helpers used instead of the shared TestHelpers to avoid
// sure-fire single-test compilation issues during focused runs
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import org.apache.commons.io.FileUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ValueSet;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.OperationOutcome;

import gov.cms.madie.terminology.dto.ValueSetSearchResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.ArgumentCaptor;

import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FhirTerminologyServiceTest {

  @Mock FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;
  @Mock FhirContext fhirContext;
  @Mock CodeSystemRepository codeSystemRepository;
  @Mock VsacService vsacService;
  @InjectMocks FhirTerminologyService fhirTerminologyService;

  private UmlsUser umlsUser;
  private static final String TEST_HARP_ID = "te$tHarpId";
  private static final String TEST_API_KEY = "te$tKey";
  private final String mockManifestResource =
      """
      {
        "resourceType": "Bundle",
        "id": "library-search",
        "meta": {
          "lastUpdated": "2024-03-14T14:04:52.456-04:00"
        },
        "type": "searchset",
        "total": 25,
        "link": [
          {
            "relation": "self",
            "url": "https://uat-cts.nlm.nih.gov/fhir/Library"
          }
        ],
        "entry": [
          {
            "fullUrl": "http://cts.nlm.nih.gov/fhir/Library/ecqm-update-4q2017-eh",
            "resource": {
              "resourceType": "Library",
              "id": "ecqm-update-4q2017-eh",
              "meta": {
                "profile": [
                  "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/publishable-library-cqfm",
                  "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/quality-program-cqfm"
                ]
              },
              "url": "http://cts.nlm.nih.gov/fhir/Library/ecqm-update-4q2017-eh",
              "version": "2017-09-15",
              "title": "Ecqm Update 4q2017 EH",
              "status": "active"
            }
          },
          {
            "fullUrl": "http://cts.nlm.nih.gov/fhir/Library/mu2-update-2012-10-25",
            "resource": {
              "resourceType": "Library",
              "id": "mu2-update-2012-10-25",
              "meta": {
                "profile": [
                  "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/publishable-library-cqfm",
                  "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/quality-program-cqfm"
                ]
              },
              "url": "http://cts.nlm.nih.gov/fhir/Library/mu2-update-2012-10-25",
              "version": "2012-10-25",
              "status": "active"
            }
          }
        ]
      }
      """;
  private final String mockCodeSystemsResource =
      "{\"resourceType\":\"Bundle\",\"id\":\"codesystem-search\",\"meta\":{\"lastUpdated\":\"2024-03-28T15:04:59.375-04:00\"},\"type\":\"searchset\",\"total\":831,\"link\":[{\"relation\":\"self\",\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=500&_count=2\"},{\"relation\":\"first\",\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=0&_count=2\"},{\"relation\":\"previous\",\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=498&_count=2\"},{\"relation\":\"last\",\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=829&_count=2\"}],\"entry\":[{\"fullUrl\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\",\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"ObservationInterpretation\",\"meta\":{\"versionId\":\"1710382394\",\"lastUpdated\":\"2019-04-25T00:00:00.000-04:00\",\"profile\":[\"http://hl7.org/fhir/StructureDefinition/shareablecodesystem\"]},\"url\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\",\"identifier\":[{\"system\":\"urn:ietf:rfc:3986\",\"value\":\"urn:oid:2.16.840.1.113883.5.83\"}],\"version\":\"2019-03-01\",\"name\":\"ObservationInterpretation\",\"title\":\"ObservationInterpretation\",\"status\":\"active\",\"experimental\":false,\"date\":\"2019-04-15T00:00:00-04:00\",\"_publisher\":{\"extension\":[{\"url\":\"http://hl7.org/fhir/StructureDefinition/data-absent-reason\",\"valueCode\":\"unknown\"}]},\"content\":\"complete\",\"count\":57,\"concept\":[{\"code\":\"<\",\"display\":\"Offscalelow\"},{\"code\":\">\",\"display\":\"Offscalehigh\"},{\"code\":\"A\",\"display\":\"Abnormal\"},{\"code\":\"AA\",\"display\":\"Criticalabnormal\"},{\"code\":\"AC\",\"display\":\"Anti-complementarysubstancespresent\"},{\"code\":\"B\",\"display\":\"Better\"},{\"code\":\"CAR\",\"display\":\"Carrier\"},{\"code\":\"Carrier\",\"display\":\"Carrier\"},{\"code\":\"D\",\"display\":\"Significantchangedown\"},{\"code\":\"DET\",\"display\":\"Detected\"},{\"code\":\"E\",\"display\":\"Equivocal\"},{\"code\":\"EX\",\"display\":\"outsidethreshold\"},{\"code\":\"EXP\",\"display\":\"Expected\"},{\"code\":\"H\",\"display\":\"High\"},{\"code\":\"H>\",\"display\":\"Significantlyhigh\"},{\"code\":\"HH\",\"display\":\"Criticalhigh\"},{\"code\":\"HM\",\"display\":\"HoldforMedicalReview\"},{\"code\":\"HU\",\"display\":\"Significantlyhigh\"},{\"code\":\"HX\",\"display\":\"abovehighthreshold\"},{\"code\":\"I\",\"display\":\"Intermediate\"},{\"code\":\"IE\",\"display\":\"Insufficientevidence\"},{\"code\":\"IND\",\"display\":\"Indeterminate\"},{\"code\":\"L\",\"display\":\"Low\"},{\"code\":\"L<\",\"display\":\"Significantlylow\"},{\"code\":\"LL\",\"display\":\"Criticallow\"},{\"code\":\"LU\",\"display\":\"Significantlylow\"},{\"code\":\"LX\",\"display\":\"belowlowthreshold\"},{\"code\":\"MS\",\"display\":\"moderatelysusceptible\"},{\"code\":\"N\",\"display\":\"Normal\"},{\"code\":\"NCL\",\"display\":\"NoCLSIdefinedbreakpoint\"},{\"code\":\"ND\",\"display\":\"Notdetected\"},{\"code\":\"NEG\",\"display\":\"Negative\"},{\"code\":\"NR\",\"display\":\"Non-reactive\"},{\"code\":\"NS\",\"display\":\"Non-susceptible\"},{\"code\":\"OBX\",\"display\":\"InterpretationqualifiersinseparateOBXsegments\"},{\"code\":\"ObservationInterpretationDetection\",\"display\":\"ObservationInterpretationDetection\"},{\"code\":\"ObservationInterpretationExpectation\",\"display\":\"ObservationInterpretationExpectation\"},{\"code\":\"POS\",\"display\":\"Positive\"},{\"code\":\"QCF\",\"display\":\"Qualitycontrolfailure\"},{\"code\":\"R\",\"display\":\"Resistant\"},{\"code\":\"RR\",\"display\":\"Reactive\"},{\"code\":\"ReactivityObservationInterpretation\",\"display\":\"ReactivityObservationInterpretation\"},{\"code\":\"S\",\"display\":\"Susceptible\"},{\"code\":\"SDD\",\"display\":\"Susceptible-dosedependent\"},{\"code\":\"SYN-R\",\"display\":\"Synergy-resistant\"},{\"code\":\"SYN-S\",\"display\":\"Synergy-susceptible\"},{\"code\":\"TOX\",\"display\":\"Cytotoxicsubstancepresent\"},{\"code\":\"U\",\"display\":\"Significantchangeup\"},{\"code\":\"UNE\",\"display\":\"Unexpected\"},{\"code\":\"VS\",\"display\":\"verysusceptible\"},{\"code\":\"W\",\"display\":\"Worse\"},{\"code\":\"WR\",\"display\":\"Weaklyreactive\"},{\"code\":\"_GeneticObservationInterpretation\",\"display\":\"GeneticObservationInterpretation\"},{\"code\":\"_ObservationInterpretationChange\",\"display\":\"ObservationInterpretationChange\"},{\"code\":\"_ObservationInterpretationExceptions\",\"display\":\"ObservationInterpretationExceptions\"},{\"code\":\"_ObservationInterpretationNormality\",\"display\":\"ObservationInterpretationNormality\"},{\"code\":\"_ObservationInterpretationSusceptibility\",\"display\":\"ObservationInterpretationSusceptibility\"}]}},{\"fullUrl\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\",\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"ObservationInterpretation\",\"meta\":{\"versionId\":\"1305437570\",\"lastUpdated\":\"2020-01-16T00:00:00.000-05:00\",\"profile\":[\"http://hl7.org/fhir/StructureDefinition/shareablecodesystem\"]},\"url\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\",\"identifier\":[{\"system\":\"urn:ietf:rfc:3986\",\"value\":\"urn:oid:2.16.840.1.113883.5.83\"}],\"version\":\"2019-12-01\",\"name\":\"ObservationInterpretation\",\"title\":\"ObservationInterpretation\",\"status\":\"active\",\"experimental\":false,\"date\":\"2019-12-27T00:00:00-05:00\",\"_publisher\":{\"extension\":[{\"url\":\"http://hl7.org/fhir/StructureDefinition/data-absent-reason\",\"valueCode\":\"unknown\"}]},\"content\":\"complete\",\"count\":57,\"concept\":[{\"code\":\"<\",\"display\":\"Offscalelow\"},{\"code\":\">\",\"display\":\"Offscalehigh\"},{\"code\":\"A\",\"display\":\"Abnormal\"},{\"code\":\"AA\",\"display\":\"Criticalabnormal\"},{\"code\":\"AC\",\"display\":\"Anti-complementarysubstancespresent\"},{\"code\":\"B\",\"display\":\"Better\"},{\"code\":\"CAR\",\"display\":\"Carrier\"},{\"code\":\"Carrier\",\"display\":\"Carrier\"},{\"code\":\"D\",\"display\":\"Significantchangedown\"},{\"code\":\"DET\",\"display\":\"Detected\"},{\"code\":\"E\",\"display\":\"Equivocal\"},{\"code\":\"EX\",\"display\":\"outsidethreshold\"},{\"code\":\"EXP\",\"display\":\"Expected\"},{\"code\":\"H\",\"display\":\"High\"},{\"code\":\"H>\",\"display\":\"Significantlyhigh\"},{\"code\":\"HH\",\"display\":\"Criticalhigh\"},{\"code\":\"HM\",\"display\":\"HoldforMedicalReview\"},{\"code\":\"HU\",\"display\":\"Significantlyhigh\"},{\"code\":\"HX\",\"display\":\"abovehighthreshold\"},{\"code\":\"I\",\"display\":\"Intermediate\"},{\"code\":\"IE\",\"display\":\"Insufficientevidence\"},{\"code\":\"IND\",\"display\":\"Indeterminate\"},{\"code\":\"L\",\"display\":\"Low\"},{\"code\":\"L<\",\"display\":\"Significantlylow\"},{\"code\":\"LL\",\"display\":\"Criticallow\"},{\"code\":\"LU\",\"display\":\"Significantlylow\"},{\"code\":\"LX\",\"display\":\"belowlowthreshold\"},{\"code\":\"MS\",\"display\":\"moderatelysusceptible\"},{\"code\":\"N\",\"display\":\"Normal\"},{\"code\":\"NCL\",\"display\":\"NoCLSIdefinedbreakpoint\"},{\"code\":\"ND\",\"display\":\"Notdetected\"},{\"code\":\"NEG\",\"display\":\"Negative\"},{\"code\":\"NR\",\"display\":\"Non-reactive\"},{\"code\":\"NS\",\"display\":\"Non-susceptible\"},{\"code\":\"OBX\",\"display\":\"InterpretationqualifiersinseparateOBXsegments\"},{\"code\":\"ObservationInterpretationDetection\",\"display\":\"ObservationInterpretationDetection\"},{\"code\":\"ObservationInterpretationExpectation\",\"display\":\"ObservationInterpretationExpectation\"},{\"code\":\"POS\",\"display\":\"Positive\"},{\"code\":\"QCF\",\"display\":\"Qualitycontrolfailure\"},{\"code\":\"R\",\"display\":\"Resistant\"},{\"code\":\"RR\",\"display\":\"Reactive\"},{\"code\":\"ReactivityObservationInterpretation\",\"display\":\"ReactivityObservationInterpretation\"},{\"code\":\"S\",\"display\":\"Susceptible\"},{\"code\":\"SDD\",\"display\":\"Susceptible-dosedependent\"},{\"code\":\"SYN-R\",\"display\":\"Synergy-resistant\"},{\"code\":\"SYN-S\",\"display\":\"Synergy-susceptible\"},{\"code\":\"TOX\",\"display\":\"Cytotoxicsubstancepresent\"},{\"code\":\"U\",\"display\":\"Significantchangeup\"},{\"code\":\"UNE\",\"display\":\"Unexpected\"},{\"code\":\"VS\",\"display\":\"verysusceptible\"},{\"code\":\"W\",\"display\":\"Worse\"},{\"code\":\"WR\",\"display\":\"Weaklyreactive\"},{\"code\":\"_GeneticObservationInterpretation\",\"display\":\"GeneticObservationInterpretation\"},{\"code\":\"_ObservationInterpretationChange\",\"display\":\"ObservationInterpretationChange\"},{\"code\":\"_ObservationInterpretationExceptions\",\"display\":\"ObservationInterpretationExceptions\"},{\"code\":\"_ObservationInterpretationNormality\",\"display\":\"ObservationInterpretationNormality\"},{\"code\":\"_ObservationInterpretationSusceptibility\",\"display\":\"ObservationInterpretationSusceptibility\"}]}}]}";

  private String mockValueSetResourceWithCodes;
  private String mockValueSetResourceWithNoCodes;
  private String mockValueSetWithNoResource;
  private gov.cms.madie.terminology.models.CodeSystem codeSystem =
      gov.cms.madie.terminology.models.CodeSystem.builder()
          .oid("urn:oid:2.16.840.1.113883.6.1")
          .name("LOINC")
          .version(
              gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                  .fhirVersion("2.40")
                  .build())
          .build();

  @BeforeEach
  public void setUp() throws IOException {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    File fileWithCodes = getTestResourceFile("/value-sets/value_set_with_expansion_codes.json");
    File fileWithNoCodes = getTestResourceFile("/value-sets/value_set_with_no_expansions.json");
    File fileWithNoResource = getTestResourceFile("/value-sets/value_set_with_no_resource.json");
    mockValueSetResourceWithCodes =
        FileUtils.readFileToString(Objects.requireNonNull(fileWithCodes), Charset.defaultCharset());
    mockValueSetResourceWithNoCodes =
        FileUtils.readFileToString(
            Objects.requireNonNull(fileWithNoCodes), Charset.defaultCharset());
    mockValueSetWithNoResource =
        FileUtils.readFileToString(
            Objects.requireNonNull(fileWithNoResource), Charset.defaultCharset());
  }

  @Test
  void getManifests() {
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(fhirTerminologyServiceWebClient.getManifestBundle(anyString()))
        .thenReturn(mockManifestResource);
    var result = fhirTerminologyService.getManifests(umlsUser);
    assertEquals(2, result.size());
    assertEquals("ecqm-update-4q2017-eh", result.get(0).getId());
    assertEquals(
        "http://cts.nlm.nih.gov/fhir/Library/ecqm-update-4q2017-eh", result.get(0).getFullUrl());
    assertEquals("Ecqm Update 4q2017 EH", result.get(0).getTitle());
    assertEquals("mu2-update-2012-10-25", result.get(1).getId());
    assertEquals(
        "http://cts.nlm.nih.gov/fhir/Library/mu2-update-2012-10-25", result.get(1).getFullUrl());
  }

  @Test
  void getValueSetsExpansionsForQdm_When_ManifestExpansionIsProvided() {
    var valueSetsSearchCriteria =
        ValueSetsSearchCriteria.builder()
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .oid("2.16.840.1.113883.3.464.1003.113.11.1090")
                        .build()))
            .profile("test-profile")
            .includeDraft("false")
            .activeOnly("true")
            .manifestExpansion(
                ManifestExpansion.builder()
                    .fullUrl("https://cts.nlm.nih.gov/fhir/Library/ecqm-update-2022-05-05")
                    .id("ecqm-update-2022-05-05")
                    .build())
            .build();
    when(fhirTerminologyServiceWebClient.getValueSetResources(anyString(), any()))
        .thenReturn(mockValueSetResourceWithCodes);
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(
            Optional.of(
                gov.cms.madie.terminology.models.CodeSystem.builder()
                    .fullUrl("http://hl7.org/fhir/sid/icd-10-cm")
                    .title("ICD10CM")
                    .name("Icd10CM")
                    .version(
                        gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                            .fhirVersion("2022")
                            .vsacVersion("2022-05")
                            .build())
                    .versionId("vid")
                    .oid("urn:oid:2.16.840.1.113883.6.90")
                    .build()));
    List<QdmValueSet> result =
        fhirTerminologyService.getValueSetsExpansionsForQdm(valueSetsSearchCriteria, umlsUser);
    assertEquals(1, result.size());
    assertEquals("2.16.840.1.113883.3.464.1003.113.11.1090", result.get(0).getOid());
    assertEquals("20180310", result.get(0).getVersion());
    assertEquals("AnkylosingSpondylitis", result.get(0).getDisplayName());
    assertEquals(10, result.get(0).getConcepts().size());
    assertEquals("M45.0", result.get(0).getConcepts().get(0).getCode());
    assertEquals("2.16.840.1.113883.6.90", result.get(0).getConcepts().get(0).getCodeSystemOid());
    assertEquals("M45.1", result.get(0).getConcepts().get(1).getCode());
    assertEquals("2.16.840.1.113883.6.90", result.get(0).getConcepts().get(1).getCodeSystemOid());
    assertEquals("2022-05", result.get(0).getConcepts().get(1).getCodeSystemVersion());
  }

  @Test
  void getsValueSetsExpansionsForQdm_withNoCodes_When_ManifestExpansionIsProvided() {
    var valueSetsSearchCriteria =
        ValueSetsSearchCriteria.builder()
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .oid("2.16.840.1.113883.3.464.1003.113.11.1090")
                        .build()))
            .profile("test-profile")
            .includeDraft("false")
            .activeOnly("false")
            .manifestExpansion(
                ManifestExpansion.builder()
                    .fullUrl("https://cts.nlm.nih.gov/fhir/Library/ecqm-update-2022-05-05")
                    .id("ecqm-update-2022-05-05")
                    .build())
            .build();
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(fhirTerminologyServiceWebClient.getValueSetResources(
            anyString(), any(ValueSetsSearchCriteria.class)))
        .thenReturn(mockValueSetResourceWithNoCodes);
    List<QdmValueSet> result =
        fhirTerminologyService.getValueSetsExpansionsForQdm(valueSetsSearchCriteria, umlsUser);
    assertEquals(1, result.size());
    assertEquals("2.16.840.1.113883.3.464.1003.113.11.1090", result.get(0).getOid());
    assertEquals("20180310", result.get(0).getVersion());
    assertEquals("AnkylosingSpondylitis", result.get(0).getDisplayName());
    assertEquals(0, result.get(0).getConcepts().size());
  }

  @Test
  void
      getValueSetsExpansionsForQdmThrowsVsacParseBatchValueSetExpansionExceptionWhenManifestExpansionIsProvided() {
    var valueSetsSearchCriteria =
        ValueSetsSearchCriteria.builder()
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .oid("2.16.840.1.113883.3.464.1003.113.11.1090")
                        .build()))
            .profile("test-profile")
            .includeDraft("false")
            .activeOnly("true")
            .manifestExpansion(
                ManifestExpansion.builder()
                    .fullUrl("https://cts.nlm.nih.gov/fhir/Library/ecqm-update-2022-05-05")
                    .id("ecqm-update-2022-05-05")
                    .build())
            .build();
    when(fhirTerminologyServiceWebClient.getValueSetResources(anyString(), any()))
        .thenReturn(mockValueSetWithNoResource);
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    VsacParseBatchValueSetExpansionException ex =
        assertThrows(
            VsacParseBatchValueSetExpansionException.class,
            () ->
                fhirTerminologyService.getValueSetsExpansionsForQdm(
                    valueSetsSearchCriteria, umlsUser));

    assertEquals(ex.getMessage(), "Failed to fetch VSAC value set expansions");
    assertEquals(
        ex.getOperationOutcome().getIssueFirstRep().getDiagnostics(),
        "Content returned as invalid against the specification. Either the specification contains invalid elements, or the server failed to process due to internal errors.");
    assertEquals(
        ex.getManifestExpansionFullUrl(),
        "https://cts.nlm.nih.gov/fhir/Library/ecqm-update-2022-05-05");
    assertEquals(ex.getOid(), "2.16.840.1.113883.3.464.1003.113.11.1090");
  }

  @Test
  void getsValueSetsExpansionsForQdmIfSearchCriteriaIsEmpty() {
    var valueSetsSearchCriteria = ValueSetsSearchCriteria.builder().build();
    List<QdmValueSet> result =
        fhirTerminologyService.getValueSetsExpansionsForQdm(valueSetsSearchCriteria, umlsUser);
    assertEquals(0, result.size());
  }

  @Test
  void getsValueSetsExpansionsForQdmIfSearchCriteriaIsNull() {
    List<QdmValueSet> result = fhirTerminologyService.getValueSetsExpansionsForQdm(null, umlsUser);
    assertEquals(0, result.size());
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
        new CodeSystem()
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
        new CodeSystem()
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
    when(codeSystemRepository.findByOidAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());

    // call method under test and assert results
    List<gov.cms.madie.terminology.models.CodeSystem> resultList =
        fhirTerminologyService.retrieveAllCodeSystems(umlsUser);
    assertEquals(2, resultList.size());
    assertEquals(resultList.get(1).getFullUrl(), t.getFullUrl());
    verify(codeSystemRepository, times(2))
        .save(any(gov.cms.madie.terminology.models.CodeSystem.class));
  }

  @Test
  void testRetrieveAllCodeSystemsWithInsert() {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    Bundle bundle = new Bundle();
    var c1 = new CodeSystem();
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
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .id("titleversion")
            .title("title")
            .name("name1")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("version")
                    .build())
            .versionId("vid")
            .oid("codeUrl")
            .lastUpdated(Instant.now())
            .lastUpdatedUpstream(new Date())
            .build();

    when(codeSystemRepository.findByOidAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.ofNullable(existingCodeSystem));

    List<gov.cms.madie.terminology.models.CodeSystem> result =
        fhirTerminologyService.retrieveAllCodeSystems(umlsUser);
    verify(codeSystemRepository, times(2))
        .save(any(gov.cms.madie.terminology.models.CodeSystem.class));
  }

  @Test
  void testGetAllCodeSystems() {
    var c1 = new gov.cms.madie.terminology.models.CodeSystem();
    c1.setTitle("t1");
    c1.setOid("fakeoid1");
    c1.setFullUrl("http://example.com/cs1");
    c1.setVersion(
        gov.cms.madie.terminology.models.CodeSystem.Version.builder()
            .fhirVersion("1.0")
            .vsacVersion("1")
            .build());
    var c2 = new gov.cms.madie.terminology.models.CodeSystem();
    c2.setTitle("t2");
    c2.setOid("fakeoid2");
    c2.setFullUrl("http://example.com/cs2");
    c2.setVersion(
        gov.cms.madie.terminology.models.CodeSystem.Version.builder()
            .fhirVersion("2.0")
            .vsacVersion("2")
            .build());
    var c3 = new gov.cms.madie.terminology.models.CodeSystem();
    c3.setTitle("t3");
    c3.setOid("fakeoid3");
    c3.setFullUrl("http://example.com/cs3");
    c3.setVersion(
        gov.cms.madie.terminology.models.CodeSystem.Version.builder().fhirVersion("2024").build());
    var c4 = new gov.cms.madie.terminology.models.CodeSystem();
    c4.setTitle("t4");
    c4.setOid("NOT.IN.VSAC");
    c4.setFullUrl("http://example.com/cs4");
    c4.setVersion(
        gov.cms.madie.terminology.models.CodeSystem.Version.builder()
            .fhirVersion("fhirOnly")
            .build());

    List<gov.cms.madie.terminology.models.CodeSystem> codeSystems = Arrays.asList(c1, c2, c3, c4);
    when(codeSystemRepository.findAll()).thenReturn(codeSystems);
    List<gov.cms.madie.terminology.models.CodeSystem> result =
        fhirTerminologyService.getAllCodeSystems();

    verify(codeSystemRepository).findAll();
    assertEquals(3, result.size());
    assertEquals("t1", result.get(0).getTitle());
    assertEquals("1.0", result.get(0).getVersion().getFhirVersion());
    assertEquals("1", result.get(0).getVersion().getVsacVersion());

    assertEquals("t2", result.get(1).getTitle());
    assertEquals("2.0", result.get(1).getVersion().getFhirVersion());
    assertEquals("2", result.get(1).getVersion().getVsacVersion());

    // Verify FHIR only Code Systems appear in the result set
    assertEquals("t3", result.get(2).getTitle());
    assertEquals("2024", result.get(2).getVersion().getFhirVersion());
    assertNull(result.get(2).getVersion().getVsacVersion());
  }

  @Test
  void testRetrieveCodeWhenCodeIsNull() {
    String codeSystem = "LOINC";
    String version = "2.40";
    assertThat(
        fhirTerminologyService.retrieveCode(null, codeSystem, version, TEST_API_KEY),
        is(equalTo(null)));
  }

  @Test
  void testRetrieveCodeWhenCodeSystemIsNull() {
    String codeName = "1963-8";
    String version = "2.40";
    assertThat(
        fhirTerminologyService.retrieveCode(codeName, null, version, TEST_API_KEY),
        is(equalTo(null)));
  }

  @Test
  void testRetrieveCodeWhenCodeSystemVersionIsNull() {
    String codeName = "1963-8";
    String codeSystem = "LOINC";
    assertThat(
        fhirTerminologyService.retrieveCode(codeName, codeSystem, null, TEST_API_KEY),
        is(equalTo(null)));
  }

  @Test
  void testRetrieveCodeWhenCodeSystemNotFound() {
    String codeName = "1963-8";
    String codeSystem = "LOINC";
    String version = "2.40";
    when(codeSystemRepository.findByNameAndVersionFhirVersion(codeSystem, version))
        .thenReturn(Optional.empty());
    when(codeSystemRepository.findByNameAndVersionVsacVersion(codeSystem, version))
        .thenReturn(Optional.empty());
    assertThat(
        fhirTerminologyService.retrieveCode(codeName, codeSystem, version, TEST_API_KEY),
        is(equalTo(null)));
  }

  @Test
  void testRetrieveCodeSuccessfully() {
    String codeName = "1963-8";
    String codeSystemName = "LOINC";
    String version = "2.40";
    String codeJson =
        "{\n"
            + "  \"resourceType\": \"Parameters\",\n"
            + "  \"parameter\": [ {\n"
            + "    \"name\": \"name\",\n"
            + "    \"valueString\": \"LOINC\"\n"
            + "  }, {\n"
            + "    \"name\": \"version\",\n"
            + "    \"valueString\": \"2.40\"\n"
            + "  }, {\n"
            + "    \"name\": \"display\",\n"
            + "    \"valueString\": \"Bicarbonate [Moles/volume] in Serum\"\n"
            + "  }, {\n"
            + "    \"name\": \"Oid\",\n"
            + "    \"valueString\": \"2.16.840.1.113883.6.1\"\n"
            + "  } ]\n"
            + "}";

    var codeSystem =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .fullUrl("http://loinc.org")
            .title("LOINC")
            .name("LOINC")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .vsacVersion("2.40")
                    .build())
            .versionId("2084800774")
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .build();
    when(codeSystemRepository.findByNameAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.of(codeSystem));
    when(fhirTerminologyServiceWebClient.getCodeResource(codeName, codeSystem, TEST_API_KEY))
        .thenReturn(codeJson);
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(vsacService.getCodeStatus(any(Code.class), anyString())).thenReturn(CodeStatus.ACTIVE);
    Code code =
        fhirTerminologyService.retrieveCode(codeName, codeSystemName, version, TEST_API_KEY);
    assertThat(code.getName(), is(equalTo(codeName)));
    assertThat(code.getDisplay(), is(equalTo("Bicarbonate [Moles/volume] in Serum")));
    assertThat(code.getCodeSystem(), is(equalTo(codeSystemName)));
    assertThat(code.getFhirVersion(), is(equalTo(version)));
    assertThat(code.getStatus(), is(equalTo(CodeStatus.ACTIVE)));
  }

  @Test
  void testRetrieveCodesListSuccessfully() {
    List<Map<String, String>> codeList =
        List.of(
            Map.of(
                "code",
                "1963-8",
                "codeSystem",
                "LOINC",
                "oid",
                "'urn:oid:2.16.840.1.113883.6.1'",
                "versionIncluded",
                "false"));

    String codeJson =
        "{\n"
            + "  \"resourceType\": \"Parameters\",\n"
            + "  \"parameter\": [ {\n"
            + "    \"name\": \"name\",\n"
            + "    \"valueString\": \"LOINC\"\n"
            + "  }, {\n"
            + "    \"name\": \"version\",\n"
            + "    \"valueString\": \"2.40\"\n"
            + "  }, {\n"
            + "    \"name\": \"display\",\n"
            + "    \"valueString\": \"Bicarbonate [Moles/volume] in Serum\"\n"
            + "  }, {\n"
            + "    \"name\": \"Oid\",\n"
            + "    \"valueString\": \"2.16.840.1.113883.6.1\"\n"
            + "  } ]\n"
            + "}";

    gov.cms.madie.terminology.models.CodeSystem codeSystem =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .id("LOINC2.40")
            .fullUrl("http://loinc.org")
            .title("LOINC")
            .name("LOINC")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .vsacVersion("2.40")
                    .build())
            .versionId("404676818")
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .lastUpdated(Instant.parse("2024-04-30T20:18:48.706Z"))
            .lastUpdatedUpstream(new Date("Fri Apr 01 00:00:00 EDT 2022"))
            .isLatestVersion(true)
            .build();

    when(codeSystemRepository.findAllByOid(anyString())).thenReturn(List.of(codeSystem));
    when(fhirTerminologyServiceWebClient.getCodeResource(anyString(), any(), any()))
        .thenReturn(codeJson);
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(vsacService.getCodeStatus(any(), anyString())).thenReturn(CodeStatus.ACTIVE);
    List<Code> code = fhirTerminologyService.retrieveCodesAndCodeSystems(codeList, TEST_API_KEY);
    assertThat(code.get(0).getName(), is(equalTo("1963-8")));
    assertThat(code.get(0).getDisplay(), is(equalTo("Bicarbonate [Moles/volume] in Serum")));
    assertThat(code.get(0).getCodeSystem(), is(equalTo("LOINC")));
    assertThat(code.get(0).getFhirVersion(), is(equalTo("2.40")));
    assertThat(code.get(0).getStatus(), is(equalTo(CodeStatus.ACTIVE)));
    assertThat(code.get(0).isVersionIncluded(), is(equalTo(false)));
  }

  /* Small local helper to avoid depending on the shared TestHelpers import which
   * occasionally causes unresolved-symbol compile issues when running single
   * test methods via Surefire. Mirrors the behaviour used elsewhere in tests.
   */

  private static File getTestResourceFile(String resourcePath) {
    if (resourcePath == null || resourcePath.isEmpty()) {
      return null;
    }
    return new File(
        Objects.requireNonNull(FhirTerminologyServiceTest.class.getResource(resourcePath))
            .getFile());
  }

  /* this test covers requestAllValueSetsExpansions(): when existingValueSet!=null
   * which will also cover most of the private boolean containsEntry() method
   */
  @Test
  void requestAllValueSetsExpansionsDedupeAndRecursiveCalls() {
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    IParser parser = FhirContext.forR4().newJsonParser();

    // existingValueSet with one contains entry A
    ValueSet existing = new ValueSet();
    existing.setId("vs1");
    existing.setExpansion(new ValueSet.ValueSetExpansionComponent());
    ValueSet.ValueSetExpansionContainsComponent a =
        new ValueSet.ValueSetExpansionContainsComponent();
    a.setCode("A");
    a.setSystem("S");
    a.setVersion("v1");
    existing.getExpansion().addContains(a);

    List<ValueSet> allValueSets = new ArrayList<>();
    allValueSets.add(existing);

    // First bundle: contains A (duplicate) and B; offset=0, total=3 -> will trigger recursion
    ValueSet vsFromFirst = new ValueSet();
    vsFromFirst.setId("vs1");
    vsFromFirst.addIdentifier(new Identifier().setValue("urn:oid:1"));
    ValueSet.ValueSetExpansionComponent exp1 = new ValueSet.ValueSetExpansionComponent();
    exp1.setOffset(0);
    exp1.setTotal(3);
    ValueSet.ValueSetExpansionContainsComponent b =
        new ValueSet.ValueSetExpansionContainsComponent();
    b.setCode("B");
    b.setSystem("S");
    b.setVersion("v1");
    exp1.addContains(a);
    exp1.addContains(b);
    vsFromFirst.setExpansion(exp1);

    Bundle bundle1 = new Bundle();
    bundle1.addEntry(new Bundle.BundleEntryComponent().setResource(vsFromFirst));

    // Second bundle (recursion): contains C; offset=2, total=3 -> no further recursion
    ValueSet vsFromSecond = new ValueSet();
    vsFromSecond.setId("vs1");
    vsFromSecond.addIdentifier(new Identifier().setValue("urn:oid:1"));
    ValueSet.ValueSetExpansionComponent exp2 = new ValueSet.ValueSetExpansionComponent();
    exp2.setOffset(2);
    exp2.setTotal(3);
    ValueSet.ValueSetExpansionContainsComponent c =
        new ValueSet.ValueSetExpansionContainsComponent();
    c.setCode("C");
    c.setSystem("S");
    c.setVersion("v1");
    exp2.addContains(c);
    vsFromSecond.setExpansion(exp2);

    Bundle bundle2 = new Bundle();
    bundle2.addEntry(new Bundle.BundleEntryComponent().setResource(vsFromSecond));

    when(fhirTerminologyServiceWebClient.getValueSetResources(
            anyString(), any(ValueSetsSearchCriteria.class)))
        .thenReturn(parser.encodeResourceToString(bundle1), parser.encodeResourceToString(bundle2));

    ValueSetsSearchCriteria search =
        ValueSetsSearchCriteria.builder()
            .valueSetParams(
                List.of(ValueSetsSearchCriteria.ValueSetParams.builder().oid("1").build()))
            .build();

    fhirTerminologyService.requestAllValueSetsExpansions(allValueSets, TEST_API_KEY, search);

    // After dedupe and recursion, existing expansion should have A, B, C -> size 3
    assertEquals(3, existing.getExpansion().getContains().size());

    // ensure web client was invoked at least twice (initial + recursive)
    verify(fhirTerminologyServiceWebClient, atLeast(2))
        .getValueSetResources(anyString(), any(ValueSetsSearchCriteria.class));
  }

  /* this test is for branch testing on requestAllValueSetsExpansions() method when
   * it throw news VsacParseBatchValueSetExpansionException AND
   * valueSetsSearchCriteria.getManifestExpansion() == null
   */
  @Test
  void requestAllValueSetsExpansionsHandlesNullManifestExpansion() {
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    IParser parser = FhirContext.forR4().newJsonParser();

    // Bundle with one entry where resource is null and response contains OperationOutcome
    Bundle bundle = new Bundle();
    Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
    entry.setResource(null); // This will make valueSetResource null
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue().setDiagnostics("Simulated error");
    entry.setResponse(new Bundle.BundleEntryResponseComponent().setOutcome(outcome));
    bundle.addEntry(entry);
    String bundleJson = parser.encodeResourceToString(bundle);

    when(fhirTerminologyServiceWebClient.getValueSetResources(
            anyString(), any(ValueSetsSearchCriteria.class)))
        .thenReturn(bundleJson);

    List<ValueSet> allValueSets = new ArrayList<>();
    ValueSetsSearchCriteria search =
        ValueSetsSearchCriteria.builder()
            .valueSetParams(
                List.of(ValueSetsSearchCriteria.ValueSetParams.builder().oid("1").build()))
            .manifestExpansion(null)
            .build();

    // Should throw VsacParseBatchValueSetExpansionException and cover the null branch
    VsacParseBatchValueSetExpansionException ex =
        assertThrows(
            VsacParseBatchValueSetExpansionException.class,
            () ->
                fhirTerminologyService.requestAllValueSetsExpansions(
                    allValueSets, TEST_API_KEY, search));
    assertEquals("Failed to fetch VSAC value set expansions", ex.getMessage());
    // Should be null due to manifestExpansion == null
    assertNull(ex.getManifestExpansionFullUrl());
  }

  /* this is a branch coverage for searchValueSets() method
   * when !l.getRelation().equals("next")
   */
  @Test
  void searchValueSetsWithoutNextLinkDoesNotCallFetch() {
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    IParser parser = FhirContext.forR4().newJsonParser();

    ValueSet vs1 = new ValueSet();
    vs1.setId("v1-no-next");
    vs1.setTitle("TitleNoNext");
    vs1.addIdentifier(new Identifier().setValue("urn:oid:1"));
    Meta m = new Meta();
    m.setLastUpdated(new Date());
    vs1.setMeta(m);

    Bundle bundle1 = new Bundle();
    bundle1.addEntry(new Bundle.BundleEntryComponent().setResource(vs1));
    bundle1.addLink(
        new Bundle.BundleLinkComponent()
            .setRelation("notnext")
            .setUrl("http://example.com/next?page=2"));

    String json1 = parser.encodeResourceToString(bundle1);

    when(fhirTerminologyServiceWebClient.searchValueSets(anyString(), anyMap())).thenReturn(json1);

    ValueSetSearchResult result = fhirTerminologyService.searchValueSets(TEST_API_KEY, Map.of());

    assertEquals(1, result.getValueSets().size());
    // ensure fetchResourceFromVsac was not called since there is no 'next' link
    verify(fhirTerminologyServiceWebClient, times(0))
        .fetchResourceFromVsac(anyString(), anyString(), anyString());
  }

  /* this test covers searchValueSets() method
   * when l.getRelation().equals("next"), it triggers
   * recursiveRequestValueSets(valueSetList, apiKey, l.getUrl());
   */
  @Test
  void searchValueSetsRecursiveFetchHandlesMultipleNextLinks() {
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    IParser parser = FhirContext.forR4().newJsonParser();

    // First page with a next link to page=2
    ValueSet p1 = new ValueSet();
    p1.setId("p1");
    p1.setTitle("P1");
    p1.addIdentifier(new Identifier().setValue("urn:oid:1"));
    Meta m1 = new Meta();
    m1.setLastUpdated(new Date());
    p1.setMeta(m1);
    Bundle bundle1 = new Bundle();
    bundle1.addEntry(new Bundle.BundleEntryComponent().setResource(p1));
    bundle1.addLink(
        new Bundle.BundleLinkComponent()
            .setRelation("next")
            .setUrl("http://example.com/next?page=2"));

    // Second page with its own next link to page=3
    ValueSet p2 = new ValueSet();
    p2.setId("p2");
    p2.setTitle("P2");
    p2.addIdentifier(new Identifier().setValue("urn:oid:2"));
    Meta m2 = new Meta();
    m2.setLastUpdated(new Date());
    p2.setMeta(m2);
    Bundle bundle2 = new Bundle();
    bundle2.addEntry(new Bundle.BundleEntryComponent().setResource(p2));
    bundle2.addLink(
        new Bundle.BundleLinkComponent()
            .setRelation("next")
            .setUrl("http://example.com/next?page=3"));

    // Third (final) page with no next link
    ValueSet p3 = new ValueSet();
    p3.setId("p3");
    p3.setTitle("P3");
    p3.addIdentifier(new Identifier().setValue("urn:oid:3"));
    Meta m3 = new Meta();
    m3.setLastUpdated(new Date());
    p3.setMeta(m3);
    Bundle bundle3 = new Bundle();
    bundle3.addEntry(new Bundle.BundleEntryComponent().setResource(p3));
    // include a non-next link so the false branch in recursiveRequestValueSets is exercised
    bundle3.addLink(
        new Bundle.BundleLinkComponent().setRelation("self").setUrl("http://example.com/self"));

    String json1 = parser.encodeResourceToString(bundle1);
    String json2 = parser.encodeResourceToString(bundle2);
    String json3 = parser.encodeResourceToString(bundle3);

    when(fhirTerminologyServiceWebClient.searchValueSets(anyString(), anyMap())).thenReturn(json1);
    when(fhirTerminologyServiceWebClient.fetchResourceFromVsac(
            eq("https://example.com/next?page=2"), eq(TEST_API_KEY), eq("bundle")))
        .thenReturn(json2);
    when(fhirTerminologyServiceWebClient.fetchResourceFromVsac(
            eq("https://example.com/next?page=3"), eq(TEST_API_KEY), eq("bundle")))
        .thenReturn(json3);

    ValueSetSearchResult result = fhirTerminologyService.searchValueSets(TEST_API_KEY, Map.of());

    // should aggregate three entries
    assertEquals(3, result.getValueSets().size());
    // Ensure fetchResourceFromVsac was called for page2 and page3
    verify(fhirTerminologyServiceWebClient, times(1))
        .fetchResourceFromVsac(
            eq("https://example.com/next?page=2"), eq(TEST_API_KEY), eq("bundle"));
    verify(fhirTerminologyServiceWebClient, times(1))
        .fetchResourceFromVsac(
            eq("https://example.com/next?page=3"), eq(TEST_API_KEY), eq("bundle"));
  }

  /* this test covers private void recursiveRetrieveCodeSystems()
   * when l.getRelation().equals("next")
   * NOTE: retrieveAllCodeSystems() calls recursiveRetrieveCodeSystems()
   */
  @Test
  void retrieveAllCodeSystemsParsesOffsetAndCountFromNextLink_andRecurses() {
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
    var result = fhirTerminologyService.retrieveAllCodeSystems(umlsUser);

    // should collect both code systems from initial + recursive
    assertEquals(2, result.size());
    // calls getCodeSystemsPage(offset,count,apiKey) when retrieving pages
    verify(fhirTerminologyServiceWebClient, atLeast(1))
        .getCodeSystemsPage(anyInt(), anyInt(), eq(TEST_API_KEY));
  }

  /* this branch coverage is for retrieveCodesAndCodeSystems() method, line 487
   * when codeSystemVersion.isEmpty(), it should: return null;
   */
  @Test
  void retrieveCodesAndCodeSystemsReturnsNullWhenCodeIsEmpty() {
    List<Map<String, String>> codeList =
        List.of(
            Map.of(
                "code", "test",
                "codeSystem", "LOINC",
                "oid", "'urn:oid:2.16.840.1.113883.6.1'",
                "versionIncluded", "false"));
    when(codeSystemRepository.findAllByOid(anyString())).thenReturn(List.of(codeSystem));
    List<Code> result = fhirTerminologyService.retrieveCodesAndCodeSystems(codeList, TEST_API_KEY);
    assertNull(result.get(0));
  }

  /* this is branch coverage for retrieveCodesAndCodeSystems() method, line 488
   * when || StringUtils.isEmpty(codeName)
   */
  @Test
  void retrieveCodesAndCodeSystemsReturnsNullWhenCodeNameNull() {
    List<Map<String, String>> codeList =
        List.of(
            Map.of(
                "code", "", // codeName is empty
                "codeSystem", "LOINC",
                "oid", "urn:oid:2.16.840.1.113883.6.1",
                "versionIncluded", "false",
                "version", "2.40"));

    when(codeSystemRepository.findAllByOid(anyString())).thenReturn(List.of(codeSystem));
    List<gov.cms.madie.terminology.models.CodeSystem> repoResult =
        codeSystemRepository.findAllByOid("urn:oid:2.16.840.1.113883.6.1");
    assertFalse(repoResult.isEmpty());
    List<Code> result = fhirTerminologyService.retrieveCodesAndCodeSystems(codeList, TEST_API_KEY);
    assertNull(result.get(0));
  }

  /* this test is branch coverage for retrieveCodesAndCodeSystems() method, line 489
   * when StringUtils.isEmpty(codeSystemName)
   */
  @Test
  void retrieveCodesAndCodeSystemsReturnsNullWhenCodeSystemNameNull() {
    List<Map<String, String>> codeList =
        List.of(
            Map.of(
                "code", "test",
                "codeSystem", "", // codeSystemName is empty
                "oid", "urn:oid:2.16.840.1.113883.6.1",
                "versionIncluded", "false",
                "version", "2.40"));

    when(codeSystemRepository.findAllByOid(anyString())).thenReturn(List.of(codeSystem));
    List<Code> result = fhirTerminologyService.retrieveCodesAndCodeSystems(codeList, TEST_API_KEY);
    assertNull(result.get(0));
  }

  /* this test is branch coverage for retrieveCodesAndCodeSystems() method, line 490
   * when !codeSystemVersion.get().isFhir()
   */
  @Test
  void retrieveCodesAndCodeSystemsReturnsNullWhenFhirVersionNull() {
    List<Map<String, String>> codeList =
        List.of(
            Map.of(
                "code", "test",
                "codeSystem", "LOINC",
                "oid", "'urn:oid:2.16.840.1.113883.6.1'",
                "versionIncluded", "false",
                "version", "")); // !codeSystemVersion.get().isFhir()

    codeSystem.setVersion(
        gov.cms.madie.terminology.models.CodeSystem.Version.builder()
            .fhirVersion("")
            .vsacVersion("")
            .build());
    when(codeSystemRepository.findAllByOid(anyString())).thenReturn(List.of(codeSystem));
    List<Code> result = fhirTerminologyService.retrieveCodesAndCodeSystems(codeList, TEST_API_KEY);
    assertNull(result.get(0));
  }

  /* this test is for private String parseOidFromIdentifier(),line 424
   * when StringUtils.equalsIgnoreCase(StringUtils.deleteWhitespace(identifier.getValue())
   * it should: return "urn:oid:2.16.840.1.113883.6.285";
   */
  @Test
  void parseOidFromIdentifierReturnsCustomOidForHCPCS() throws Exception {
    Identifier id = new Identifier();
    id.setValue("urn:oid:2.16.840.1.113883.6.14,2.16.840.1.113883.6.285");
    Method method =
        FhirTerminologyService.class.getDeclaredMethod("parseOidFromIdentifier", List.class);
    method.setAccessible(true);
    String result = (String) method.invoke(fhirTerminologyService, List.of(id));
    assertEquals("urn:oid:2.16.840.1.113883.6.285", result);
  }

  /* this test is for parseOidFromIdentifier() method, line 429
   * covering last line: return "";
   */
  @Test
  void parseOidFromIdentifierReturnsEmptyStringWhenValueEmpty() throws Exception {
    Identifier id = new Identifier();
    id.setValue("");
    Method method =
        FhirTerminologyService.class.getDeclaredMethod("parseOidFromIdentifier", List.class);
    method.setAccessible(true);
    String result = (String) method.invoke(fhirTerminologyService, List.of(id));
    assertEquals("", result);
  }

  /* this test covers private Optional<CodeSystemEntry.Version> getCodeSystemVersion(),
   * line 504:
   * if (oid == null), it should: return Optional.empty();
   */
  @Test
  void getCodeSystemVersionReturnsEmptyWhenOidIsNull() throws Exception {
    Method method =
        FhirTerminologyService.class.getDeclaredMethod(
            "getCodeSystemVersion", String.class, String.class, List.class);
    method.setAccessible(true);

    Optional<?> result =
        (Optional<?>) method.invoke(fhirTerminologyService, "version", null, List.of(codeSystem));
    assertTrue(result.isEmpty());
  }

  /* this test covers private Optional<CodeSystemEntry.Version> getCodeSystemVersion(),
   * line 508:
   * if (CollectionUtils.isEmpty(codeSystems)), it should: return Optional.empty();
   */
  @Test
  void getCodeSystemVersionReturnsEmptyWhenCodeSystemsNull() throws Exception {
    Method method =
        FhirTerminologyService.class.getDeclaredMethod(
            "getCodeSystemVersion", String.class, String.class, List.class);
    method.setAccessible(true);

    Optional<?> result =
        (Optional<?>)
            method.invoke(fhirTerminologyService, "version", "oid", Collections.emptyList());
    assertTrue(result.isEmpty());
  }

  /* branch coverage for private boolean containsEntry() method
   * when !Objects.equals(existing.getSystem(), newEntry.getSystem())
   * line 135
   */
  @Test
  void containsEntryNotEqualsSystem() {
    ValueSet.ValueSetExpansionContainsComponent existing =
        new ValueSet.ValueSetExpansionContainsComponent();
    existing.setCode("A");
    existing.setSystem("S1");
    existing.setVersion("v1");
    ValueSet.ValueSetExpansionContainsComponent newEntry =
        new ValueSet.ValueSetExpansionContainsComponent();
    newEntry.setCode("A");
    newEntry.setSystem("S2"); // different system
    newEntry.setVersion("v1");
    List<ValueSet.ValueSetExpansionContainsComponent> existingEntries = List.of(existing);
    boolean result = invokeContainsEntry(existingEntries, newEntry);
    assertFalse(result);
  }

  /* branch coverage for private boolean containsEntry() method
   * when !Objects.equals(existing.getVersion(), newEntry.getVersion())
   * line 136
   */
  @Test
  void containsEntry_branchCoverage_notEqualsVersion() {
    ValueSet.ValueSetExpansionContainsComponent existing =
        new ValueSet.ValueSetExpansionContainsComponent();
    existing.setCode("A");
    existing.setSystem("S");
    existing.setVersion("v1");
    ValueSet.ValueSetExpansionContainsComponent newEntry =
        new ValueSet.ValueSetExpansionContainsComponent();
    newEntry.setCode("A");
    newEntry.setSystem("S");
    newEntry.setVersion("v2"); // different version
    List<ValueSet.ValueSetExpansionContainsComponent> existingEntries = List.of(existing);
    boolean result = invokeContainsEntry(existingEntries, newEntry);
    assertFalse(result);
  }

  // Helper to invoke private containsEntry
  private boolean invokeContainsEntry(
      List<ValueSet.ValueSetExpansionContainsComponent> existingEntries,
      ValueSet.ValueSetExpansionContainsComponent newEntry) {
    try {
      java.lang.reflect.Method method =
          FhirTerminologyService.class.getDeclaredMethod(
              "containsEntry", List.class, ValueSet.ValueSetExpansionContainsComponent.class);
      method.setAccessible(true);
      return (boolean) method.invoke(fhirTerminologyService, existingEntries, newEntry);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /* branch test getValueSetConcepts(), line 204
   * when valueSet.getExpansion() == null, it should: return List.of();
   */
  @Test
  void getValueSetConceptsReturnsEmptyListWhenExpansionNull()
      throws NoSuchFieldException, SecurityException {
    org.hl7.fhir.r4.model.ValueSet vs = new org.hl7.fhir.r4.model.ValueSet();
    vs.setExpansion(null);

    try {
      java.lang.reflect.Method method =
          FhirTerminologyService.class.getDeclaredMethod(
              "getValueSetConcepts", org.hl7.fhir.r4.model.ValueSet.class);
      method.setAccessible(true);
      @SuppressWarnings("unchecked")
      List<QdmValueSet.Concept> result =
          (List<QdmValueSet.Concept>) method.invoke(fhirTerminologyService, vs);
      assertTrue(result.isEmpty());
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  /* branch test getValueSetConcepts(), line 204
   * when valueSet.valueSet.getExpansion().getTotal() == 0, it should: return List.of();
   */
  @Test
  void getValueSetConceptsReturnsEmptyListWhenExpansionTotalIsLessThan0() {
    org.hl7.fhir.r4.model.ValueSet vs = new org.hl7.fhir.r4.model.ValueSet();
    ValueSet.ValueSetExpansionComponent exp1 = new ValueSet.ValueSetExpansionComponent();
    exp1.setOffset(0);
    exp1.setTotal(0);
    vs.setExpansion(exp1);
    try {
      java.lang.reflect.Method method =
          FhirTerminologyService.class.getDeclaredMethod(
              "getValueSetConcepts", org.hl7.fhir.r4.model.ValueSet.class);
      method.setAccessible(true);
      @SuppressWarnings("unchecked")
      List<QdmValueSet.Concept> result =
          (List<QdmValueSet.Concept>) method.invoke(fhirTerminologyService, vs);
      assertTrue(result.isEmpty());
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  /* branch test getValueSetConcepts(), line 204
   * when valueSet.valueSet.getExpansion().getTotal() > 0, it should: return List<QdmValueSet.Concept>;
   */
  @Test
  void getValueSetConceptsReturnsConceptsWhenExpansionTotalPositive() {
    org.hl7.fhir.r4.model.ValueSet vs = new org.hl7.fhir.r4.model.ValueSet();
    ValueSet.ValueSetExpansionComponent exp1 = new ValueSet.ValueSetExpansionComponent();
    exp1.setOffset(0);
    exp1.setTotal(1);
    ValueSet.ValueSetExpansionContainsComponent concept =
        new ValueSet.ValueSetExpansionContainsComponent();
    concept.setCode("test-code");
    concept.setDisplay("Test Display");
    concept.setSystem("http://test-system");
    concept.setVersion("v1");
    exp1.addContains(concept);
    vs.setExpansion(exp1);
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(java.util.Optional.empty());
    try {
      java.lang.reflect.Method method =
          FhirTerminologyService.class.getDeclaredMethod(
              "getValueSetConcepts", org.hl7.fhir.r4.model.ValueSet.class);
      method.setAccessible(true);
      @SuppressWarnings("unchecked")
      List<QdmValueSet.Concept> result =
          (List<QdmValueSet.Concept>) method.invoke(fhirTerminologyService, vs);
      assertEquals(1, result.size());
      assertEquals("test-code", result.get(0).getCode());
      assertEquals("http://test-system", result.get(0).getCodeSystemOid());
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  /* branch coverage for getValueSetConcepts() method line 211
   * when !codeSystemOptional.isPresent()
   */
  @Test
  void getValueSetConceptsOptionalCodeSystemEntryNotPresent() {
    // Create a ValueSet with an expansion containing a concept whose system is not in
    // codeSystemEntries
    org.hl7.fhir.r4.model.ValueSet valueSet = new org.hl7.fhir.r4.model.ValueSet();
    org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionComponent expansion =
        new org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionComponent();
    expansion.setTotal(1);
    org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionContainsComponent concept =
        new org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionContainsComponent();
    concept.setCode("test-code");
    concept.setSystem("http://unmapped-system-url"); // Not in codeSystemEntries
    concept.setVersion("v1");
    expansion.addContains(concept);
    valueSet.setExpansion(expansion);

    try {
      java.lang.reflect.Method method =
          FhirTerminologyService.class.getDeclaredMethod(
              "getValueSetConcepts", org.hl7.fhir.r4.model.ValueSet.class);
      method.setAccessible(true);
      @SuppressWarnings("unchecked")
      List<QdmValueSet.Concept> result =
          (List<QdmValueSet.Concept>) method.invoke(fhirTerminologyService, valueSet);
      assertEquals(1, result.size());
      // codeSystemOid should be the original system URL
      assertEquals("http://unmapped-system-url", result.get(0).getCodeSystemOid());
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  /* branch coverage for traverseValueSet() method line 297
   * when identifier.getValue() == null
   */
  @Test
  void traverseValueSetIdentifierValueNull() throws Exception {
    // Prepare a ValueSet with identifier value null
    ValueSet vs = new ValueSet();
    vs.setId("vs-null");
    Identifier id = new Identifier();
    id.setValue(null); // null value
    vs.setIdentifier(List.of(id));
    // Set Meta with non-null lastUpdated
    Meta meta = new Meta();
    meta.setLastUpdated(new Date());
    vs.setMeta(meta);
    Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
    entry.setResource(vs);
    List<ValueSetForSearch> valueSetList = new ArrayList<>();

    java.lang.reflect.Method method =
        FhirTerminologyService.class.getDeclaredMethod(
            "traverseValueSet", Bundle.BundleEntryComponent.class, List.class);
    method.setAccessible(true);
    method.invoke(fhirTerminologyService, entry, valueSetList);

    assertEquals(1, valueSetList.size());
    assertEquals("", valueSetList.get(0).getOid());
  }

  /* branch coverage for traverseValueSet() method line 297
   * when identifier.getValue().isEmpty()
   */
  @Test
  void traverseValueSetIdentifierValueEmpty() throws Exception {
    // Prepare a ValueSet with identifier value empty string
    ValueSet vs = new ValueSet();
    vs.setId("vs-empty");
    Identifier id = new Identifier();
    id.setValue(""); // empty value
    vs.setIdentifier(List.of(id));
    // Set Meta with non-null lastUpdated
    Meta meta = new Meta();
    meta.setLastUpdated(new Date());
    vs.setMeta(meta);
    Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
    entry.setResource(vs);
    List<ValueSetForSearch> valueSetList = new ArrayList<>();

    java.lang.reflect.Method method =
        FhirTerminologyService.class.getDeclaredMethod(
            "traverseValueSet", Bundle.BundleEntryComponent.class, List.class);
    method.setAccessible(true);
    method.invoke(fhirTerminologyService, entry, valueSetList);

    assertEquals(1, valueSetList.size());
    assertEquals("", valueSetList.get(0).getOid());
  }

  /* branch coverage for traverseValueSet(), line 308
   * map(extension -> String.valueOf(extension.getValue())
   */
  @Test
  void traverseValueSetExtensionValuePresent() throws Exception {
    ValueSet vs = new ValueSet();
    vs.setId("vs-ext-present-311");
    Identifier id = new Identifier();
    id.setValue("urn:oid:1");
    vs.setIdentifier(List.of(id));
    Meta meta = new Meta();
    meta.setLastUpdated(new Date());
    vs.setMeta(meta);
    // Add extension for line 308
    vs.addExtension(
        new org.hl7.fhir.r4.model.Extension(
            "http://hl7.org/fhir/StructureDefinition/valueset-author",
            new org.hl7.fhir.r4.model.StringType("author-name")));
    Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
    entry.setResource(vs);
    List<ValueSetForSearch> valueSetList = new ArrayList<>();
    java.lang.reflect.Method method =
        FhirTerminologyService.class.getDeclaredMethod(
            "traverseValueSet", Bundle.BundleEntryComponent.class, List.class);
    method.setAccessible(true);
    method.invoke(fhirTerminologyService, entry, valueSetList);
    assertEquals(1, valueSetList.size());
    assertEquals("author-name", valueSetList.get(0).getAuthor());
  }

  /* branch coverage for traverseValueSet() method, line 313
   * .map(x -> x.getSystem())
   */
  @Test
  void traverseValueSetComposedOfSystem() throws Exception {
    ValueSet vs = new ValueSet();
    vs.setId("vs-composed-of");
    Identifier id = new Identifier();
    id.setValue("urn:oid:1");
    vs.setIdentifier(List.of(id));
    Meta meta = new Meta();
    meta.setLastUpdated(new Date());
    vs.setMeta(meta);
    // Compose with include having system
    ValueSet.ValueSetComposeComponent compose = new ValueSet.ValueSetComposeComponent();
    ValueSet.ConceptSetComponent include1 = new ValueSet.ConceptSetComponent();
    include1.setSystem("http://loinc.org");
    compose.setInclude(List.of(include1));
    vs.setCompose(compose);
    Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
    entry.setResource(vs);
    List<ValueSetForSearch> valueSetList = new ArrayList<>();
    java.lang.reflect.Method method =
        FhirTerminologyService.class.getDeclaredMethod(
            "traverseValueSet", Bundle.BundleEntryComponent.class, List.class);
    method.setAccessible(true);
    method.invoke(fhirTerminologyService, entry, valueSetList);
    assertEquals(1, valueSetList.size());
    assertEquals("http://loinc.org", valueSetList.get(0).getComposedOf());
  }

  /* branch coverage for traverseValueSet() method line 319
   * .map(extension -> String.valueOf(extension.getValue()))
   */
  @Test
  void traverseValueSetEffectiveDateExtensionValue() throws Exception {
    ValueSet vs = new ValueSet();
    vs.setId("vs-effective-date");
    Identifier id = new Identifier();
    id.setValue("urn:oid:1");
    vs.setIdentifier(List.of(id));
    Meta meta = new Meta();
    meta.setLastUpdated(new Date());
    vs.setMeta(meta);
    // Compose with include having system
    ValueSet.ValueSetComposeComponent compose = new ValueSet.ValueSetComposeComponent();
    ValueSet.ConceptSetComponent include1 = new ValueSet.ConceptSetComponent();
    include1.setSystem("http://loinc.org");
    compose.setInclude(List.of(include1));
    vs.setCompose(compose);
    // Add effectiveDate extension for line 319
    vs.addExtension(
        new org.hl7.fhir.r4.model.Extension(
            "http://hl7.org/fhir/StructureDefinition/valueset-effectiveDate",
            new org.hl7.fhir.r4.model.StringType("2026-03-15")));
    Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
    entry.setResource(vs);
    List<ValueSetForSearch> valueSetList = new ArrayList<>();
    java.lang.reflect.Method method =
        FhirTerminologyService.class.getDeclaredMethod(
            "traverseValueSet", Bundle.BundleEntryComponent.class, List.class);
    method.setAccessible(true);
    method.invoke(fhirTerminologyService, entry, valueSetList);
    assertEquals(1, valueSetList.size());
    assertEquals("2026-03-15", valueSetList.get(0).getEffectiveDate());
  }

  /* coverage for DataAccessException, lines 463-467
   * when doing updateOrInsertAllCodeSystems(),
   */
  @Test
  void retrieveAllCodeSystemsHandlesDataAccessException() {
    UmlsUser umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    gov.cms.madie.terminology.models.CodeSystem codeSystem =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .name("LOINC")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .build())
            .build();
    // Mock repository to throw DataAccessException when save is called
    doThrow(new DataAccessException("Simulated DB error") {})
        .when(codeSystemRepository)
        .save(any());
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(
            "{\"resourceType\":\"Bundle\",\"entry\":[{\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"cs1\",\"name\":\"LOINC\",\"version\":\"2.40\",\"title\":\"LOINC\",\"identifier\":[{\"value\":\"urn:oid:2.16.840.1.113883.6.1\"}]}}]}");
    // Call the method under test
    List<gov.cms.madie.terminology.models.CodeSystem> result =
        fhirTerminologyService.retrieveAllCodeSystems(umlsUser);
    // Assert that the result is not empty and the error branch was triggered
    assertFalse(result.isEmpty());
    assertEquals("LOINC", result.get(0).getName());
  }

  /* branch coverage for retrieveCode(), line 367:
   * codeSystem != null
   */
  @Test
  void retrieveCodeCodeSystemNull() {
    String codeName = "test-code";
    String codeSystemName = "LOINC";
    String version = "2.40";
    // Mock repository to return empty (codeSystem == null)
    when(codeSystemRepository.findByNameAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());
    Code result =
        fhirTerminologyService.retrieveCode(codeName, codeSystemName, version, TEST_API_KEY);
    assertNull(result);
  }

  /* branch coverage for retrieveCode(), line 367:
   * !codeSystem.isVsacSearchable()
   */
  @Test
  void retrieveCodeCodeSystemNotVsacSearchable() {
    String codeName = "test-code";
    String codeSystemName = "LOINC";
    String version = "2.40";
    // Mock codeSystem with isVsacSearchable() == false
    gov.cms.madie.terminology.models.CodeSystem codeSystem =
        mock(gov.cms.madie.terminology.models.CodeSystem.class);
    when(codeSystem.isVsacSearchable()).thenReturn(false);
    when(codeSystemRepository.findByNameAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.of(codeSystem));
    Code result =
        fhirTerminologyService.retrieveCode(codeName, codeSystemName, version, TEST_API_KEY);
    assertNull(result);
  }

  /* branch coverage for retrieveCode(), line 367:
   * codeSystem.isVsacSearchable()
   */
  @Test
  void retrieveCodeCodeSystemVsacSearchableTrue() {
    String codeName = "test-code";
    String codeSystemName = "LOINC";
    String version = "2.40";
    // Mock codeSystem with isVsacSearchable() == true
    gov.cms.madie.terminology.models.CodeSystem codeSystem =
        mock(gov.cms.madie.terminology.models.CodeSystem.class);
    when(codeSystem.isVsacSearchable()).thenReturn(true);
    // Mock getVersion() to return a valid Version object
    gov.cms.madie.terminology.models.CodeSystem.Version versionObj =
        gov.cms.madie.terminology.models.CodeSystem.Version.builder()
            .fhirVersion("2.40")
            .vsacVersion("2.40")
            .build();
    when(codeSystem.getVersion()).thenReturn(versionObj);
    when(codeSystemRepository.findByNameAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.of(codeSystem));
    // Mock fhirContext.newJsonParser() to return a valid parser
    when(fhirContext.newJsonParser())
        .thenReturn(ca.uhn.fhir.context.FhirContext.forR4().newJsonParser());
    // Mock web client response with all expected parameters
    String codeJson =
        "{\"resourceType\":\"Parameters\",\"parameter\":["
            + "{\"name\":\"name\",\"valueString\":\"LOINC\"},"
            + "{\"name\":\"version\",\"valueString\":\"2.40\"},"
            + "{\"name\":\"display\",\"valueString\":\"Test Display\"},"
            + "{\"name\":\"Oid\",\"valueString\":\"2.16.840.1.113883.6.1\"}"
            + "]}";
    when(fhirTerminologyServiceWebClient.getCodeResource(anyString(), any(), anyString()))
        .thenReturn(codeJson);
    Code result =
        fhirTerminologyService.retrieveCode(codeName, codeSystemName, version, TEST_API_KEY);
    assertNotNull(result);
  }

  /* branch coverage for lines 406:
   * assert newOffset != null;
   */
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
        AssertionError.class, () -> fhirTerminologyService.retrieveAllCodeSystems(umlsUser));
  }

  /* branch coverage for line 407:
   * assert count != null;
   */
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
        AssertionError.class, () -> fhirTerminologyService.retrieveAllCodeSystems(umlsUser));
  }

  @Test
  void testCreateCodeSystem() {
    gov.cms.madie.terminology.models.CodeSystem codeSystem =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .title("LOINC")
            .name("LOINC")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .build();
    gov.cms.madie.terminology.models.CodeSystem saved =
        codeSystem.toBuilder().id("LOINCversion2.40").build();

    when(codeSystemRepository.findByOidAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(codeSystemRepository.save(any(gov.cms.madie.terminology.models.CodeSystem.class)))
        .thenReturn(saved);

    ArgumentCaptor<gov.cms.madie.terminology.models.CodeSystem> captor =
        ArgumentCaptor.forClass(gov.cms.madie.terminology.models.CodeSystem.class);

    gov.cms.madie.terminology.models.CodeSystem result =
        fhirTerminologyService.createCodeSystem(codeSystem);

    assertNotNull(result);
    assertEquals("LOINCversion2.40", result.getId());
    assertEquals("LOINC", result.getName());
    assertEquals("urn:oid:2.16.840.1.113883.6.1", result.getOid());
    assertEquals("https://loinc.org", result.getFullUrl());
    assertEquals("2.40", result.getVersion().getFhirVersion());
    verify(codeSystemRepository, times(1)).save(captor.capture());
    assertNotNull(captor.getValue().getLastUpdated());
    verify(codeSystemRepository, never()).findAllByOid(anyString());
  }

  @Test
  void testCreateCodeSystemDemotesExistingLatestVersionsWhenMarkedAsLatest() {
    gov.cms.madie.terminology.models.CodeSystem incoming =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .name("LOINC")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(true)
            .build();

    gov.cms.madie.terminology.models.CodeSystem existingLatest =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .id("LOINCversion2.39")
            .name("LOINC")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.39")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(true)
            .build();

    gov.cms.madie.terminology.models.CodeSystem existingNonLatest =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .id("LOINCversion2.38")
            .name("LOINC")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.38")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(false)
            .build();

    when(codeSystemRepository.findByOidAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(codeSystemRepository.findAllByOid(anyString()))
        .thenReturn(List.of(existingLatest, existingNonLatest));
    when(codeSystemRepository.save(any(gov.cms.madie.terminology.models.CodeSystem.class)))
        .thenReturn(incoming.toBuilder().id("LOINCversion2.40").build());

    ArgumentCaptor<gov.cms.madie.terminology.models.CodeSystem> captor =
        ArgumentCaptor.forClass(gov.cms.madie.terminology.models.CodeSystem.class);

    fhirTerminologyService.createCodeSystem(incoming);

    verify(codeSystemRepository, times(1)).saveAll(anyList());
    verify(codeSystemRepository, times(1)).save(captor.capture());

    assertTrue(captor.getValue().isLatestVersion());
    assertFalse(existingLatest.isLatestVersion());
    assertFalse(existingNonLatest.isLatestVersion());
  }

  @Test
  void testCreateCodeSystemThrowsDuplicateException() {
    gov.cms.madie.terminology.models.CodeSystem codeSystem =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .title("LOINC")
            .name("LOINC")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .build();

    when(codeSystemRepository.findByOidAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.of(codeSystem));

    assertThrows(
        DuplicateCodeSystemException.class,
        () -> fhirTerminologyService.createCodeSystem(codeSystem));
    verify(codeSystemRepository, never())
        .save(any(gov.cms.madie.terminology.models.CodeSystem.class));
  }

  @Test
  void testUpdateCodeSystem() {
    String id = "LOINCversion2.40";
    Date existingLastUpdatedUpstream = new Date(0);
    Date newLastUpdatedUpstream = new Date(existingLastUpdatedUpstream.getTime() + 1);

    gov.cms.madie.terminology.models.CodeSystem existing =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .id(id)
            .title("Old Title")
            .name("Old Name")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.39")
                    .vsacVersion("2.39")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .versionId("1")
            .lastUpdatedUpstream(existingLastUpdatedUpstream)
            .build();

    gov.cms.madie.terminology.models.CodeSystem incoming =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .title("New Title")
            .name("LOINC")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .vsacVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .versionId("2")
            .lastUpdatedUpstream(newLastUpdatedUpstream)
            .build();

    when(codeSystemRepository.findById(id)).thenReturn(Optional.of(existing));
    when(codeSystemRepository.save(any(gov.cms.madie.terminology.models.CodeSystem.class)))
        .thenReturn(existing);

    ArgumentCaptor<gov.cms.madie.terminology.models.CodeSystem> captor =
        ArgumentCaptor.forClass(gov.cms.madie.terminology.models.CodeSystem.class);

    fhirTerminologyService.updateCodeSystem(id, incoming);

    verify(codeSystemRepository, times(1)).save(captor.capture());
    verify(codeSystemRepository, never()).findAllByOid(anyString());
    gov.cms.madie.terminology.models.CodeSystem saved = captor.getValue();

    assertNotNull(saved.getLastUpdated());
    assertEquals("New Title", saved.getTitle());
    assertEquals("LOINC", saved.getName());
    assertEquals("urn:oid:2.16.840.1.113883.6.1", saved.getOid());
    assertEquals("https://loinc.org", saved.getFullUrl());
    assertEquals("2.40", saved.getVersion().getFhirVersion());
    assertEquals("2.40", saved.getVersion().getVsacVersion());
    assertEquals("2", saved.getVersionId());
    assertEquals(newLastUpdatedUpstream, saved.getLastUpdatedUpstream());
  }

  @Test
  void testUpdateCodeSystemDemotesExistingLatestVersionsWhenMarkedAsLatest() {
    String id = "LOINCversion2.40";

    gov.cms.madie.terminology.models.CodeSystem existing =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .id(id)
            .name("LOINC")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(false)
            .build();

    gov.cms.madie.terminology.models.CodeSystem incoming =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .name("LOINC")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(true)
            .build();

    gov.cms.madie.terminology.models.CodeSystem existingLatest =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .id("LOINCversion2.39")
            .name("LOINC")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.39")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(true)
            .build();

    gov.cms.madie.terminology.models.CodeSystem existingNonLatest =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .id("LOINCversion2.38")
            .name("LOINC")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.38")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(false)
            .build();

    when(codeSystemRepository.findById(id)).thenReturn(Optional.of(existing));
    when(codeSystemRepository.findAllByOid(anyString()))
        .thenReturn(List.of(existingLatest, existingNonLatest, existing));
    when(codeSystemRepository.save(any(gov.cms.madie.terminology.models.CodeSystem.class)))
        .thenReturn(existing);

    ArgumentCaptor<gov.cms.madie.terminology.models.CodeSystem> captor =
        ArgumentCaptor.forClass(gov.cms.madie.terminology.models.CodeSystem.class);

    fhirTerminologyService.updateCodeSystem(id, incoming);

    verify(codeSystemRepository, times(1)).saveAll(anyList());
    verify(codeSystemRepository, times(1)).save(captor.capture());

    assertTrue(captor.getValue().isLatestVersion());
    assertFalse(existingLatest.isLatestVersion());
    assertFalse(existingNonLatest.isLatestVersion());
  }

  @Test
  void testUpdateCodeSystemSetsLatestVersionFalseWhenNotMarkedAsLatest() {
    String id = "LOINCversion2.40";

    gov.cms.madie.terminology.models.CodeSystem existing =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .id(id)
            .name("LOINC")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(true)
            .build();

    gov.cms.madie.terminology.models.CodeSystem incoming =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .name("LOINC")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(false)
            .build();

    when(codeSystemRepository.findById(id)).thenReturn(Optional.of(existing));
    when(codeSystemRepository.save(any(gov.cms.madie.terminology.models.CodeSystem.class)))
        .thenReturn(existing);

    ArgumentCaptor<gov.cms.madie.terminology.models.CodeSystem> captor =
        ArgumentCaptor.forClass(gov.cms.madie.terminology.models.CodeSystem.class);

    fhirTerminologyService.updateCodeSystem(id, incoming);

    verify(codeSystemRepository, never()).findAllByOid(anyString());
    verify(codeSystemRepository, times(1)).save(captor.capture());
    assertFalse(captor.getValue().isLatestVersion());
  }

  @Test
  void testUpdateCodeSystemPreservesOptionalFieldsWhenNotProvided() {
    String id = "LOINCversion2.40";
    Date existingLastUpdatedUpstream = new Date();
    gov.cms.madie.terminology.models.CodeSystem existing =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .id(id)
            .title("Old Title")
            .name("LOINC")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.39")
                    .vsacVersion("2.39")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .versionId("1")
            .lastUpdatedUpstream(existingLastUpdatedUpstream)
            .build();

    // title, vsacVersion, versionId, lastUpdatedUpstream omitted — existing values should be
    // preserved
    gov.cms.madie.terminology.models.CodeSystem incoming =
        gov.cms.madie.terminology.models.CodeSystem.builder()
            .name("LOINC")
            .version(
                gov.cms.madie.terminology.models.CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .build();

    when(codeSystemRepository.findById(id)).thenReturn(Optional.of(existing));
    when(codeSystemRepository.save(any(gov.cms.madie.terminology.models.CodeSystem.class)))
        .thenReturn(existing);

    ArgumentCaptor<gov.cms.madie.terminology.models.CodeSystem> captor =
        ArgumentCaptor.forClass(gov.cms.madie.terminology.models.CodeSystem.class);

    fhirTerminologyService.updateCodeSystem(id, incoming);

    verify(codeSystemRepository, times(1)).save(captor.capture());
    gov.cms.madie.terminology.models.CodeSystem saved = captor.getValue();
    assertEquals("Old Title", saved.getTitle());
    assertEquals("2.39", saved.getVersion().getVsacVersion());
    assertEquals("1", saved.getVersionId());
    assertEquals(existingLastUpdatedUpstream, saved.getLastUpdatedUpstream());
  }

  @Test
  void testUpdateCodeSystemThrowsNotFoundException() {
    String id = "nonexistent";
    gov.cms.madie.terminology.models.CodeSystem codeSystem =
        gov.cms.madie.terminology.models.CodeSystem.builder().build();

    when(codeSystemRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(
        CodeSystemNotFoundException.class,
        () -> fhirTerminologyService.updateCodeSystem(id, codeSystem));
    verify(codeSystemRepository, never())
        .save(any(gov.cms.madie.terminology.models.CodeSystem.class));
  }

  @Test
  void testDeleteCodeSystem() {
    String id = "LOINCversion2.40";
    when(codeSystemRepository.existsById(id)).thenReturn(true);

    fhirTerminologyService.deleteCodeSystem(id);

    verify(codeSystemRepository, times(1)).deleteById(id);
  }

  @Test
  void testDeleteCodeSystemThrowsNotFoundException() {
    String id = "nonexistent";
    when(codeSystemRepository.existsById(id)).thenReturn(false);

    assertThrows(
        CodeSystemNotFoundException.class, () -> fhirTerminologyService.deleteCodeSystem(id));
    verify(codeSystemRepository, never()).deleteById(anyString());
  }
}
