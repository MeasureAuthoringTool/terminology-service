package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.okta.commons.lang.Collections;
import gov.cms.madie.models.mapping.CodeSystemEntry;
import gov.cms.madie.models.measure.ManifestExpansion;
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
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FhirTerminologyServiceTest {

  @Mock FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;
  @Mock FhirContext fhirContext;
  @Mock MappingService mappingService;
  @InjectMocks FhirTerminologyService fhirTerminologyService;
  @Mock CodeSystemRepository codeSystemRepository;
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
    var c1 = new CodeSystem();
    var identifierList = new ArrayList<Identifier>();
    var i1 = new Identifier().setValue("codeUrl");
    identifierList.add(i1);
    var m1 = new Meta().setLastUpdated(new Date()).setVersionId("vid");
    c1.setId("titleversion");
    c1.setTitle("title");
    c1.setVersion("version");
    c1.setMeta(m1);
    c1.setIdentifier(identifierList);
    var c2 = new CodeSystem();
    var identifierList2 = new ArrayList<Identifier>();
    var i2 = new Identifier().setValue("codeUrl");
    identifierList2.add(i2);
    var m2 = new Meta().setLastUpdated(new Date()).setVersionId("vid");
    c2.setId("titleversion");
    c2.setTitle("title");
    c2.setVersion("version");
    c2.setMeta(m2);
    c2.setIdentifier(identifierList2);
    bundle.addEntry().setResource(c1);
    bundle.addEntry().setResource(c2);
    String mockCodeSystemsResource = "{\"resourceType\":\"Bundle\",\"id\":\"codesystem-search\",\"meta\":{\"lastUpdated\":\"2024-03-28T15:04:59.375-04:00\"},\"type\":\"searchset\",\"total\":831,\"link\":[{\"relation\":\"self\",\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=500&_count=2\"},{\"relation\":\"first\",\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=0&_count=2\"},{\"relation\":\"previous\",\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=498&_count=2\"},{\"relation\":\"last\",\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=829&_count=2\"}],\"entry\":[{\"fullUrl\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\",\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"ObservationInterpretation\",\"meta\":{\"versionId\":\"1710382394\",\"lastUpdated\":\"2019-04-25T00:00:00.000-04:00\",\"profile\":[\"http://hl7.org/fhir/StructureDefinition/shareablecodesystem\"]},\"url\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\",\"identifier\":[{\"system\":\"urn:ietf:rfc:3986\",\"value\":\"urn:oid:2.16.840.1.113883.5.83\"}],\"version\":\"2019-03-01\",\"name\":\"ObservationInterpretation\",\"title\":\"ObservationInterpretation\",\"status\":\"active\",\"experimental\":false,\"date\":\"2019-04-15T00:00:00-04:00\",\"_publisher\":{\"extension\":[{\"url\":\"http://hl7.org/fhir/StructureDefinition/data-absent-reason\",\"valueCode\":\"unknown\"}]},\"content\":\"complete\",\"count\":57,\"concept\":[{\"code\":\"<\",\"display\":\"Offscalelow\"},{\"code\":\">\",\"display\":\"Offscalehigh\"},{\"code\":\"A\",\"display\":\"Abnormal\"},{\"code\":\"AA\",\"display\":\"Criticalabnormal\"},{\"code\":\"AC\",\"display\":\"Anti-complementarysubstancespresent\"},{\"code\":\"B\",\"display\":\"Better\"},{\"code\":\"CAR\",\"display\":\"Carrier\"},{\"code\":\"Carrier\",\"display\":\"Carrier\"},{\"code\":\"D\",\"display\":\"Significantchangedown\"},{\"code\":\"DET\",\"display\":\"Detected\"},{\"code\":\"E\",\"display\":\"Equivocal\"},{\"code\":\"EX\",\"display\":\"outsidethreshold\"},{\"code\":\"EXP\",\"display\":\"Expected\"},{\"code\":\"H\",\"display\":\"High\"},{\"code\":\"H>\",\"display\":\"Significantlyhigh\"},{\"code\":\"HH\",\"display\":\"Criticalhigh\"},{\"code\":\"HM\",\"display\":\"HoldforMedicalReview\"},{\"code\":\"HU\",\"display\":\"Significantlyhigh\"},{\"code\":\"HX\",\"display\":\"abovehighthreshold\"},{\"code\":\"I\",\"display\":\"Intermediate\"},{\"code\":\"IE\",\"display\":\"Insufficientevidence\"},{\"code\":\"IND\",\"display\":\"Indeterminate\"},{\"code\":\"L\",\"display\":\"Low\"},{\"code\":\"L<\",\"display\":\"Significantlylow\"},{\"code\":\"LL\",\"display\":\"Criticallow\"},{\"code\":\"LU\",\"display\":\"Significantlylow\"},{\"code\":\"LX\",\"display\":\"belowlowthreshold\"},{\"code\":\"MS\",\"display\":\"moderatelysusceptible\"},{\"code\":\"N\",\"display\":\"Normal\"},{\"code\":\"NCL\",\"display\":\"NoCLSIdefinedbreakpoint\"},{\"code\":\"ND\",\"display\":\"Notdetected\"},{\"code\":\"NEG\",\"display\":\"Negative\"},{\"code\":\"NR\",\"display\":\"Non-reactive\"},{\"code\":\"NS\",\"display\":\"Non-susceptible\"},{\"code\":\"OBX\",\"display\":\"InterpretationqualifiersinseparateOBXsegments\"},{\"code\":\"ObservationInterpretationDetection\",\"display\":\"ObservationInterpretationDetection\"},{\"code\":\"ObservationInterpretationExpectation\",\"display\":\"ObservationInterpretationExpectation\"},{\"code\":\"POS\",\"display\":\"Positive\"},{\"code\":\"QCF\",\"display\":\"Qualitycontrolfailure\"},{\"code\":\"R\",\"display\":\"Resistant\"},{\"code\":\"RR\",\"display\":\"Reactive\"},{\"code\":\"ReactivityObservationInterpretation\",\"display\":\"ReactivityObservationInterpretation\"},{\"code\":\"S\",\"display\":\"Susceptible\"},{\"code\":\"SDD\",\"display\":\"Susceptible-dosedependent\"},{\"code\":\"SYN-R\",\"display\":\"Synergy-resistant\"},{\"code\":\"SYN-S\",\"display\":\"Synergy-susceptible\"},{\"code\":\"TOX\",\"display\":\"Cytotoxicsubstancepresent\"},{\"code\":\"U\",\"display\":\"Significantchangeup\"},{\"code\":\"UNE\",\"display\":\"Unexpected\"},{\"code\":\"VS\",\"display\":\"verysusceptible\"},{\"code\":\"W\",\"display\":\"Worse\"},{\"code\":\"WR\",\"display\":\"Weaklyreactive\"},{\"code\":\"_GeneticObservationInterpretation\",\"display\":\"GeneticObservationInterpretation\"},{\"code\":\"_ObservationInterpretationChange\",\"display\":\"ObservationInterpretationChange\"},{\"code\":\"_ObservationInterpretationExceptions\",\"display\":\"ObservationInterpretationExceptions\"},{\"code\":\"_ObservationInterpretationNormality\",\"display\":\"ObservationInterpretationNormality\"},{\"code\":\"_ObservationInterpretationSusceptibility\",\"display\":\"ObservationInterpretationSusceptibility\"}]}},{\"fullUrl\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\",\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"ObservationInterpretation\",\"meta\":{\"versionId\":\"1305437570\",\"lastUpdated\":\"2020-01-16T00:00:00.000-05:00\",\"profile\":[\"http://hl7.org/fhir/StructureDefinition/shareablecodesystem\"]},\"url\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\",\"identifier\":[{\"system\":\"urn:ietf:rfc:3986\",\"value\":\"urn:oid:2.16.840.1.113883.5.83\"}],\"version\":\"2019-12-01\",\"name\":\"ObservationInterpretation\",\"title\":\"ObservationInterpretation\",\"status\":\"active\",\"experimental\":false,\"date\":\"2019-12-27T00:00:00-05:00\",\"_publisher\":{\"extension\":[{\"url\":\"http://hl7.org/fhir/StructureDefinition/data-absent-reason\",\"valueCode\":\"unknown\"}]},\"content\":\"complete\",\"count\":57,\"concept\":[{\"code\":\"<\",\"display\":\"Offscalelow\"},{\"code\":\">\",\"display\":\"Offscalehigh\"},{\"code\":\"A\",\"display\":\"Abnormal\"},{\"code\":\"AA\",\"display\":\"Criticalabnormal\"},{\"code\":\"AC\",\"display\":\"Anti-complementarysubstancespresent\"},{\"code\":\"B\",\"display\":\"Better\"},{\"code\":\"CAR\",\"display\":\"Carrier\"},{\"code\":\"Carrier\",\"display\":\"Carrier\"},{\"code\":\"D\",\"display\":\"Significantchangedown\"},{\"code\":\"DET\",\"display\":\"Detected\"},{\"code\":\"E\",\"display\":\"Equivocal\"},{\"code\":\"EX\",\"display\":\"outsidethreshold\"},{\"code\":\"EXP\",\"display\":\"Expected\"},{\"code\":\"H\",\"display\":\"High\"},{\"code\":\"H>\",\"display\":\"Significantlyhigh\"},{\"code\":\"HH\",\"display\":\"Criticalhigh\"},{\"code\":\"HM\",\"display\":\"HoldforMedicalReview\"},{\"code\":\"HU\",\"display\":\"Significantlyhigh\"},{\"code\":\"HX\",\"display\":\"abovehighthreshold\"},{\"code\":\"I\",\"display\":\"Intermediate\"},{\"code\":\"IE\",\"display\":\"Insufficientevidence\"},{\"code\":\"IND\",\"display\":\"Indeterminate\"},{\"code\":\"L\",\"display\":\"Low\"},{\"code\":\"L<\",\"display\":\"Significantlylow\"},{\"code\":\"LL\",\"display\":\"Criticallow\"},{\"code\":\"LU\",\"display\":\"Significantlylow\"},{\"code\":\"LX\",\"display\":\"belowlowthreshold\"},{\"code\":\"MS\",\"display\":\"moderatelysusceptible\"},{\"code\":\"N\",\"display\":\"Normal\"},{\"code\":\"NCL\",\"display\":\"NoCLSIdefinedbreakpoint\"},{\"code\":\"ND\",\"display\":\"Notdetected\"},{\"code\":\"NEG\",\"display\":\"Negative\"},{\"code\":\"NR\",\"display\":\"Non-reactive\"},{\"code\":\"NS\",\"display\":\"Non-susceptible\"},{\"code\":\"OBX\",\"display\":\"InterpretationqualifiersinseparateOBXsegments\"},{\"code\":\"ObservationInterpretationDetection\",\"display\":\"ObservationInterpretationDetection\"},{\"code\":\"ObservationInterpretationExpectation\",\"display\":\"ObservationInterpretationExpectation\"},{\"code\":\"POS\",\"display\":\"Positive\"},{\"code\":\"QCF\",\"display\":\"Qualitycontrolfailure\"},{\"code\":\"R\",\"display\":\"Resistant\"},{\"code\":\"RR\",\"display\":\"Reactive\"},{\"code\":\"ReactivityObservationInterpretation\",\"display\":\"ReactivityObservationInterpretation\"},{\"code\":\"S\",\"display\":\"Susceptible\"},{\"code\":\"SDD\",\"display\":\"Susceptible-dosedependent\"},{\"code\":\"SYN-R\",\"display\":\"Synergy-resistant\"},{\"code\":\"SYN-S\",\"display\":\"Synergy-susceptible\"},{\"code\":\"TOX\",\"display\":\"Cytotoxicsubstancepresent\"},{\"code\":\"U\",\"display\":\"Significantchangeup\"},{\"code\":\"UNE\",\"display\":\"Unexpected\"},{\"code\":\"VS\",\"display\":\"verysusceptible\"},{\"code\":\"W\",\"display\":\"Worse\"},{\"code\":\"WR\",\"display\":\"Weaklyreactive\"},{\"code\":\"_GeneticObservationInterpretation\",\"display\":\"GeneticObservationInterpretation\"},{\"code\":\"_ObservationInterpretationChange\",\"display\":\"ObservationInterpretationChange\"},{\"code\":\"_ObservationInterpretationExceptions\",\"display\":\"ObservationInterpretationExceptions\"},{\"code\":\"_ObservationInterpretationNormality\",\"display\":\"ObservationInterpretationNormality\"},{\"code\":\"_ObservationInterpretationSusceptibility\",\"display\":\"ObservationInterpretationSusceptibility\"}]}}]}";
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
            .thenReturn(mockCodeSystemsResource);
    when(codeSystemRepository.findById(anyString())).thenReturn(Optional.empty());

    List<gov.cms.madie.terminology.models.CodeSystem> result = fhirTerminologyService.retrieveAllCodeSystems(umlsUser);

    assertEquals(2, result.size());
    verify(codeSystemRepository, times(2)).save(any(gov.cms.madie.terminology.models.CodeSystem.class));
  }

}
