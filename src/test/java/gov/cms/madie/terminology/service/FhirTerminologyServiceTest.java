package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import com.okta.commons.lang.Collections;
import gov.cms.madie.models.mapping.CodeSystemEntry;
import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.QdmValueSet;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.helpers.TestHelpers;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FhirTerminologyServiceTest {

  @Mock FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;
  @Mock FhirContext fhirContext;
  @Mock MappingService mappingService;
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
}
