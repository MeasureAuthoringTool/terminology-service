package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import com.okta.commons.lang.Collections;
import gov.cms.madie.models.mapping.CodeSystemEntry;
import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.Code;
import gov.cms.madie.terminology.dto.CodeStatus;
import gov.cms.madie.terminology.dto.QdmValueSet;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.helpers.TestHelpers;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import org.apache.commons.io.FileUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FhirTerminologyServiceTest {

  @Mock FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;
  @Mock FhirContext fhirContext;
  @Mock MappingService mappingService;
  @Mock CodeSystemRepository codeSystemRepository;
  @Mock VsacService vsacService;
  @InjectMocks FhirTerminologyService fhirTerminologyService;

  List<CodeSystemEntry> codeSystemEntries;
  private UmlsUser umlsUser;
  private static final String TEST_HARP_ID = "te$tHarpId";
  private static final String TEST_API_KEY = "te$tKey";
  private final String mockManifestResource =
      "{\n"
          + "              \"resourceType\": \"Bundle\",\n"
          + "              \"id\": \"library-search\",\n"
          + "              \"meta\":\n"
          + "              {\n"
          + "                \"lastUpdated\": \"2024-03-14T14:04:52.456-04:00\"\n"
          + "              },\n"
          + "              \"type\": \"searchset\",\n"
          + "              \"total\": 25,\n"
          + "              \"link\":\n"
          + "              [\n"
          + "                {\n"
          + "                  \"relation\": \"self\",\n"
          + "                  \"url\": \"https://uat-cts.nlm.nih.gov/fhir/Library\"\n"
          + "                }\n"
          + "              ],\n"
          + "              \"entry\":\n"
          + "              [\n"
          + "                {\n"
          + "                  \"fullUrl\": \"http://cts.nlm.nih.gov/fhir/Library/ecqm-update-4q2017-eh\",\n"
          + "                  \"resource\":\n"
          + "                  {\n"
          + "                    \"resourceType\": \"Library\",\n"
          + "                    \"id\": \"ecqm-update-4q2017-eh\",\n"
          + "                    \"meta\":\n"
          + "                    {\n"
          + "                      \"profile\":\n"
          + "                      [\n"
          + "                        \"http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/publishable-library-cqfm\",\n"
          + "                        \"http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/quality-program-cqfm\"\n"
          + "                      ]\n"
          + "                    },\n"
          + "                    \"url\": \"http://cts.nlm.nih.gov/fhir/Library/ecqm-update-4q2017-eh\",\n"
          + "                    \"version\": \"2017-09-15\",\n"
          + "                    \"status\": \"active\"\n"
          + "                  }\n"
          + "                },\n"
          + "                {\n"
          + "                  \"fullUrl\": \"http://cts.nlm.nih.gov/fhir/Library/mu2-update-2012-10-25\",\n"
          + "                  \"resource\":\n"
          + "                  {\n"
          + "                    \"resourceType\": \"Library\",\n"
          + "                    \"id\": \"mu2-update-2012-10-25\",\n"
          + "                    \"meta\":\n"
          + "                    {\n"
          + "                      \"profile\":\n"
          + "                      [\n"
          + "                        \"http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/publishable-library-cqfm\",\n"
          + "                        \"http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/quality-program-cqfm\"\n"
          + "                      ]\n"
          + "                    },\n"
          + "                    \"url\": \"http://cts.nlm.nih.gov/fhir/Library/mu2-update-2012-10-25\",\n"
          + "                    \"version\": \"2012-10-25\",\n"
          + "                    \"status\": \"active\"\n"
          + "                  }\n"
          + "                }\n"
          + "              ]\n"
          + "            }";
  private final String mockCodeSystemsResource =
      "{\"resourceType\":\"Bundle\",\"id\":\"codesystem-search\",\"meta\":{\"lastUpdated\":\"2024-03-28T15:04:59.375-04:00\"},\"type\":\"searchset\",\"total\":831,\"link\":[{\"relation\":\"self\",\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=500&_count=2\"},{\"relation\":\"first\",\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=0&_count=2\"},{\"relation\":\"previous\",\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=498&_count=2\"},{\"relation\":\"last\",\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=829&_count=2\"}],\"entry\":[{\"fullUrl\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\",\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"ObservationInterpretation\",\"meta\":{\"versionId\":\"1710382394\",\"lastUpdated\":\"2019-04-25T00:00:00.000-04:00\",\"profile\":[\"http://hl7.org/fhir/StructureDefinition/shareablecodesystem\"]},\"url\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\",\"identifier\":[{\"system\":\"urn:ietf:rfc:3986\",\"value\":\"urn:oid:2.16.840.1.113883.5.83\"}],\"version\":\"2019-03-01\",\"name\":\"ObservationInterpretation\",\"title\":\"ObservationInterpretation\",\"status\":\"active\",\"experimental\":false,\"date\":\"2019-04-15T00:00:00-04:00\",\"_publisher\":{\"extension\":[{\"url\":\"http://hl7.org/fhir/StructureDefinition/data-absent-reason\",\"valueCode\":\"unknown\"}]},\"content\":\"complete\",\"count\":57,\"concept\":[{\"code\":\"<\",\"display\":\"Offscalelow\"},{\"code\":\">\",\"display\":\"Offscalehigh\"},{\"code\":\"A\",\"display\":\"Abnormal\"},{\"code\":\"AA\",\"display\":\"Criticalabnormal\"},{\"code\":\"AC\",\"display\":\"Anti-complementarysubstancespresent\"},{\"code\":\"B\",\"display\":\"Better\"},{\"code\":\"CAR\",\"display\":\"Carrier\"},{\"code\":\"Carrier\",\"display\":\"Carrier\"},{\"code\":\"D\",\"display\":\"Significantchangedown\"},{\"code\":\"DET\",\"display\":\"Detected\"},{\"code\":\"E\",\"display\":\"Equivocal\"},{\"code\":\"EX\",\"display\":\"outsidethreshold\"},{\"code\":\"EXP\",\"display\":\"Expected\"},{\"code\":\"H\",\"display\":\"High\"},{\"code\":\"H>\",\"display\":\"Significantlyhigh\"},{\"code\":\"HH\",\"display\":\"Criticalhigh\"},{\"code\":\"HM\",\"display\":\"HoldforMedicalReview\"},{\"code\":\"HU\",\"display\":\"Significantlyhigh\"},{\"code\":\"HX\",\"display\":\"abovehighthreshold\"},{\"code\":\"I\",\"display\":\"Intermediate\"},{\"code\":\"IE\",\"display\":\"Insufficientevidence\"},{\"code\":\"IND\",\"display\":\"Indeterminate\"},{\"code\":\"L\",\"display\":\"Low\"},{\"code\":\"L<\",\"display\":\"Significantlylow\"},{\"code\":\"LL\",\"display\":\"Criticallow\"},{\"code\":\"LU\",\"display\":\"Significantlylow\"},{\"code\":\"LX\",\"display\":\"belowlowthreshold\"},{\"code\":\"MS\",\"display\":\"moderatelysusceptible\"},{\"code\":\"N\",\"display\":\"Normal\"},{\"code\":\"NCL\",\"display\":\"NoCLSIdefinedbreakpoint\"},{\"code\":\"ND\",\"display\":\"Notdetected\"},{\"code\":\"NEG\",\"display\":\"Negative\"},{\"code\":\"NR\",\"display\":\"Non-reactive\"},{\"code\":\"NS\",\"display\":\"Non-susceptible\"},{\"code\":\"OBX\",\"display\":\"InterpretationqualifiersinseparateOBXsegments\"},{\"code\":\"ObservationInterpretationDetection\",\"display\":\"ObservationInterpretationDetection\"},{\"code\":\"ObservationInterpretationExpectation\",\"display\":\"ObservationInterpretationExpectation\"},{\"code\":\"POS\",\"display\":\"Positive\"},{\"code\":\"QCF\",\"display\":\"Qualitycontrolfailure\"},{\"code\":\"R\",\"display\":\"Resistant\"},{\"code\":\"RR\",\"display\":\"Reactive\"},{\"code\":\"ReactivityObservationInterpretation\",\"display\":\"ReactivityObservationInterpretation\"},{\"code\":\"S\",\"display\":\"Susceptible\"},{\"code\":\"SDD\",\"display\":\"Susceptible-dosedependent\"},{\"code\":\"SYN-R\",\"display\":\"Synergy-resistant\"},{\"code\":\"SYN-S\",\"display\":\"Synergy-susceptible\"},{\"code\":\"TOX\",\"display\":\"Cytotoxicsubstancepresent\"},{\"code\":\"U\",\"display\":\"Significantchangeup\"},{\"code\":\"UNE\",\"display\":\"Unexpected\"},{\"code\":\"VS\",\"display\":\"verysusceptible\"},{\"code\":\"W\",\"display\":\"Worse\"},{\"code\":\"WR\",\"display\":\"Weaklyreactive\"},{\"code\":\"_GeneticObservationInterpretation\",\"display\":\"GeneticObservationInterpretation\"},{\"code\":\"_ObservationInterpretationChange\",\"display\":\"ObservationInterpretationChange\"},{\"code\":\"_ObservationInterpretationExceptions\",\"display\":\"ObservationInterpretationExceptions\"},{\"code\":\"_ObservationInterpretationNormality\",\"display\":\"ObservationInterpretationNormality\"},{\"code\":\"_ObservationInterpretationSusceptibility\",\"display\":\"ObservationInterpretationSusceptibility\"}]}},{\"fullUrl\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\",\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"ObservationInterpretation\",\"meta\":{\"versionId\":\"1305437570\",\"lastUpdated\":\"2020-01-16T00:00:00.000-05:00\",\"profile\":[\"http://hl7.org/fhir/StructureDefinition/shareablecodesystem\"]},\"url\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\",\"identifier\":[{\"system\":\"urn:ietf:rfc:3986\",\"value\":\"urn:oid:2.16.840.1.113883.5.83\"}],\"version\":\"2019-12-01\",\"name\":\"ObservationInterpretation\",\"title\":\"ObservationInterpretation\",\"status\":\"active\",\"experimental\":false,\"date\":\"2019-12-27T00:00:00-05:00\",\"_publisher\":{\"extension\":[{\"url\":\"http://hl7.org/fhir/StructureDefinition/data-absent-reason\",\"valueCode\":\"unknown\"}]},\"content\":\"complete\",\"count\":57,\"concept\":[{\"code\":\"<\",\"display\":\"Offscalelow\"},{\"code\":\">\",\"display\":\"Offscalehigh\"},{\"code\":\"A\",\"display\":\"Abnormal\"},{\"code\":\"AA\",\"display\":\"Criticalabnormal\"},{\"code\":\"AC\",\"display\":\"Anti-complementarysubstancespresent\"},{\"code\":\"B\",\"display\":\"Better\"},{\"code\":\"CAR\",\"display\":\"Carrier\"},{\"code\":\"Carrier\",\"display\":\"Carrier\"},{\"code\":\"D\",\"display\":\"Significantchangedown\"},{\"code\":\"DET\",\"display\":\"Detected\"},{\"code\":\"E\",\"display\":\"Equivocal\"},{\"code\":\"EX\",\"display\":\"outsidethreshold\"},{\"code\":\"EXP\",\"display\":\"Expected\"},{\"code\":\"H\",\"display\":\"High\"},{\"code\":\"H>\",\"display\":\"Significantlyhigh\"},{\"code\":\"HH\",\"display\":\"Criticalhigh\"},{\"code\":\"HM\",\"display\":\"HoldforMedicalReview\"},{\"code\":\"HU\",\"display\":\"Significantlyhigh\"},{\"code\":\"HX\",\"display\":\"abovehighthreshold\"},{\"code\":\"I\",\"display\":\"Intermediate\"},{\"code\":\"IE\",\"display\":\"Insufficientevidence\"},{\"code\":\"IND\",\"display\":\"Indeterminate\"},{\"code\":\"L\",\"display\":\"Low\"},{\"code\":\"L<\",\"display\":\"Significantlylow\"},{\"code\":\"LL\",\"display\":\"Criticallow\"},{\"code\":\"LU\",\"display\":\"Significantlylow\"},{\"code\":\"LX\",\"display\":\"belowlowthreshold\"},{\"code\":\"MS\",\"display\":\"moderatelysusceptible\"},{\"code\":\"N\",\"display\":\"Normal\"},{\"code\":\"NCL\",\"display\":\"NoCLSIdefinedbreakpoint\"},{\"code\":\"ND\",\"display\":\"Notdetected\"},{\"code\":\"NEG\",\"display\":\"Negative\"},{\"code\":\"NR\",\"display\":\"Non-reactive\"},{\"code\":\"NS\",\"display\":\"Non-susceptible\"},{\"code\":\"OBX\",\"display\":\"InterpretationqualifiersinseparateOBXsegments\"},{\"code\":\"ObservationInterpretationDetection\",\"display\":\"ObservationInterpretationDetection\"},{\"code\":\"ObservationInterpretationExpectation\",\"display\":\"ObservationInterpretationExpectation\"},{\"code\":\"POS\",\"display\":\"Positive\"},{\"code\":\"QCF\",\"display\":\"Qualitycontrolfailure\"},{\"code\":\"R\",\"display\":\"Resistant\"},{\"code\":\"RR\",\"display\":\"Reactive\"},{\"code\":\"ReactivityObservationInterpretation\",\"display\":\"ReactivityObservationInterpretation\"},{\"code\":\"S\",\"display\":\"Susceptible\"},{\"code\":\"SDD\",\"display\":\"Susceptible-dosedependent\"},{\"code\":\"SYN-R\",\"display\":\"Synergy-resistant\"},{\"code\":\"SYN-S\",\"display\":\"Synergy-susceptible\"},{\"code\":\"TOX\",\"display\":\"Cytotoxicsubstancepresent\"},{\"code\":\"U\",\"display\":\"Significantchangeup\"},{\"code\":\"UNE\",\"display\":\"Unexpected\"},{\"code\":\"VS\",\"display\":\"verysusceptible\"},{\"code\":\"W\",\"display\":\"Worse\"},{\"code\":\"WR\",\"display\":\"Weaklyreactive\"},{\"code\":\"_GeneticObservationInterpretation\",\"display\":\"GeneticObservationInterpretation\"},{\"code\":\"_ObservationInterpretationChange\",\"display\":\"ObservationInterpretationChange\"},{\"code\":\"_ObservationInterpretationExceptions\",\"display\":\"ObservationInterpretationExceptions\"},{\"code\":\"_ObservationInterpretationNormality\",\"display\":\"ObservationInterpretationNormality\"},{\"code\":\"_ObservationInterpretationSusceptibility\",\"display\":\"ObservationInterpretationSusceptibility\"}]}}]}";

  private String mockValueSetResourceWithCodes;
  private String mockValueSetResourceWithNoCodes;

  @BeforeEach
  public void setUp() throws IOException {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    File fileWithCodes =
        TestHelpers.getTestResourceFile("/value-sets/value_set_with_expansion_codes.json");
    File fileWithNoCodes =
        TestHelpers.getTestResourceFile("/value-sets/value_set_with_no_expansions.json");
    mockValueSetResourceWithCodes =
        FileUtils.readFileToString(Objects.requireNonNull(fileWithCodes), Charset.defaultCharset());
    mockValueSetResourceWithNoCodes =
        FileUtils.readFileToString(
            Objects.requireNonNull(fileWithNoCodes), Charset.defaultCharset());
    codeSystemEntries = new ArrayList<>();
    CodeSystemEntry.Version version = new CodeSystemEntry.Version();
    version.setVsac("2024");
    version.setFhir("2024");
    var codeSystemEntry =
        CodeSystemEntry.builder()
            .name("Icd10CM")
            .oid("urn:oid:2.16.840.1.113883.6.90")
            .url("http://hl7.org/fhir/sid/icd-10-cm")
            .versions(Collections.toList(version))
            .build();
    codeSystemEntries.add(codeSystemEntry);
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
            .manifestExpansion(
                ManifestExpansion.builder()
                    .fullUrl("https://cts.nlm.nih.gov/fhir/Library/ecqm-update-2022-05-05")
                    .id("ecqm-update-2022-05-05")
                    .build())
            .build();
    when(fhirTerminologyServiceWebClient.getValueSetResource(
            anyString(), any(), anyString(), anyString(), any()))
        .thenReturn(mockValueSetResourceWithCodes);
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
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
            .manifestExpansion(
                ManifestExpansion.builder()
                    .fullUrl("https://cts.nlm.nih.gov/fhir/Library/ecqm-update-2022-05-05")
                    .id("ecqm-update-2022-05-05")
                    .build())
            .build();
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(fhirTerminologyServiceWebClient.getValueSetResource(
            anyString(),
            any(ValueSetsSearchCriteria.ValueSetParams.class),
            anyString(),
            anyString(),
            any(ManifestExpansion.class)))
        .thenReturn(mockValueSetResourceWithNoCodes);
    when(mappingService.getCodeSystemEntries()).thenReturn(codeSystemEntries);
    List<QdmValueSet> result =
        fhirTerminologyService.getValueSetsExpansionsForQdm(valueSetsSearchCriteria, umlsUser);
    assertEquals(1, result.size());
    assertEquals("2.16.840.1.113883.3.464.1003.113.11.1090", result.get(0).getOid());
    assertEquals("20180310", result.get(0).getVersion());
    assertEquals("AnkylosingSpondylitis", result.get(0).getDisplayName());
    assertEquals(0, result.get(0).getConcepts().size());
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
    when(codeSystemRepository.findById(anyString())).thenReturn(Optional.empty());

    List<gov.cms.madie.terminology.models.CodeSystem> result =
        fhirTerminologyService.retrieveAllCodeSystems(umlsUser);
    assertEquals(2, result.size());
    assertEquals(result.get(1).getFullUrl(), t.getFullUrl());
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
            .version("version")
            .versionId("vid")
            .oid("codeUrl")
            .lastUpdated(Instant.now())
            .lastUpdatedUpstream(new Date())
            .build();

    when(codeSystemRepository.findById(anyString()))
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
    c1.setVersion("1.0");
    var c2 = new gov.cms.madie.terminology.models.CodeSystem();
    c2.setTitle("t2");
    c2.setOid("fakeoid2");
    c2.setVersion("2.0");
    CodeSystemEntry.Version cv1 = new CodeSystemEntry.Version().toBuilder().fhir("1.0").vsac("exists").build();
    CodeSystemEntry.Version cv2 = new CodeSystemEntry.Version().toBuilder().fhir("2.0").vsac("exists").build();
    var ce1 = new CodeSystemEntry().toBuilder().versions(java.util.Collections.singletonList(cv1)).oid("fakeoid1").build();
    var ce2 = new CodeSystemEntry().toBuilder().versions(java.util.Collections.singletonList(cv2)).oid("fakeoid2").build();

    List<CodeSystemEntry> codeSystemEntries = Arrays.asList(ce1, ce2);
    List<gov.cms.madie.terminology.models.CodeSystem> codeSystems = Arrays.asList(c1, c2);
    when(mappingService.getCodeSystemEntries()).thenAnswer(invocation -> codeSystemEntries);
    when(codeSystemRepository.findAll()).thenAnswer(invocation -> codeSystems);
    List<gov.cms.madie.terminology.models.CodeSystem> result =
        fhirTerminologyService.getAllCodeSystems();

    verify(codeSystemRepository).findAll();
    assertEquals(2, result.size());
    assertEquals("t1", result.get(0).getTitle());
    assertEquals("t2", result.get(1).getTitle());
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
    when(codeSystemRepository.findByNameAndVersion(codeSystem, version))
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

    var codeSystem = gov.cms.madie.terminology.models.CodeSystem.builder().build();
    when(codeSystemRepository.findByNameAndVersion(anyString(), anyString()))
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
    assertThat(code.getVersion(), is(equalTo(version)));
    assertThat(code.getStatus(), is(equalTo(CodeStatus.ACTIVE)));
  }
}
