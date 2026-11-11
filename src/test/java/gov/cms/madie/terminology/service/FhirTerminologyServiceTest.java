package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.Code;
import gov.cms.madie.terminology.dto.CodeStatus;
import gov.cms.madie.terminology.dto.QdmValueSet;
import gov.cms.madie.terminology.dto.ValueSetForSearch;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.exceptions.VsacParseBatchValueSetExpansionException;
// local resource helpers used instead of the shared TestHelpers to avoid
// sure-fire single-test compilation issues during focused runs
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import org.apache.commons.io.FileUtils;
import org.hl7.fhir.r4.model.*;

import gov.cms.madie.terminology.dto.ValueSetSearchResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.mockito.junit.jupiter.MockitoExtension;

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
  FhirTerminologyService fhirTerminologyService;

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
          .isInVsac(false)
          .build();

  @BeforeEach
  public void setUp() throws IOException {
    fhirTerminologyService =
        new FhirTerminologyService(
            fhirContext, fhirTerminologyServiceWebClient, codeSystemRepository, vsacService);
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
    when(fhirTerminologyServiceWebClient.getValueSetResourcesWithExpansion(anyString(), any()))
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
                    .isInVsac(true)
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
    when(fhirTerminologyServiceWebClient.getValueSetResourcesWithExpansion(
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
    when(fhirTerminologyServiceWebClient.getValueSetResourcesWithExpansion(anyString(), any()))
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
  void getsValueSetsExpansionsForQdmAlwaysIncludesExpansion() {
    var valueSetsSearchCriteria =
        ValueSetsSearchCriteria.builder()
            .includeExpansion(false)
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .oid("2.16.840.1.113883.3.464.1003.113.11.1090")
                        .build()))
            .build();
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(fhirTerminologyServiceWebClient.getValueSetResourcesWithExpansion(
            anyString(), any(ValueSetsSearchCriteria.class)))
        .thenReturn(mockValueSetResourceWithNoCodes);

    List<QdmValueSet> result =
        fhirTerminologyService.getValueSetsExpansionsForQdm(valueSetsSearchCriteria, umlsUser);

    assertEquals(1, result.size());
    verify(fhirTerminologyServiceWebClient, never())
        .getValueSetResourcesWithoutExpansion(anyString(), any(ValueSetsSearchCriteria.class));
  }

  @Test
  void getValueSetsReturnsEmptyListIfSearchCriteriaIsNullOrHasNoValueSetParams() {
    assertTrue(fhirTerminologyService.getValueSets(null, umlsUser).isEmpty());
    assertTrue(
        fhirTerminologyService
            .getValueSets(
                ValueSetsSearchCriteria.builder().includeExpansion(false).build(), umlsUser)
            .isEmpty());
    verifyNoInteractions(fhirTerminologyServiceWebClient);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = true)
  void getValueSetsIncludesPaginatedExpansionUnlessExplicitlyDisabled(Boolean includeExpansion) {
    var valueSetsSearchCriteria =
        ValueSetsSearchCriteria.builder()
            .includeExpansion(includeExpansion)
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .oid("2.16.840.1.113883.3.464.1003.113.11.1090")
                        .build()))
            .build();
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(fhirTerminologyServiceWebClient.getValueSetResourcesWithExpansion(
            anyString(), any(ValueSetsSearchCriteria.class)))
        .thenReturn(mockValueSetResourceWithCodes);

    List<ValueSet> result = fhirTerminologyService.getValueSets(valueSetsSearchCriteria, umlsUser);

    assertEquals(1, result.size());
    assertEquals(10, result.get(0).getExpansion().getContains().size());
    ArgumentCaptor<ValueSetsSearchCriteria> criteriaCaptor =
        ArgumentCaptor.forClass(ValueSetsSearchCriteria.class);
    verify(fhirTerminologyServiceWebClient)
        .getValueSetResourcesWithExpansion(eq(TEST_API_KEY), criteriaCaptor.capture());
    ValueSetsSearchCriteria.ValueSetParams sentParams =
        criteriaCaptor.getValue().getValueSetParams().get(0);
    assertEquals(1000, sentParams.getCount());
    assertEquals(0, sentParams.getOffset());
    verify(fhirTerminologyServiceWebClient, never())
        .getValueSetResourcesWithoutExpansion(anyString(), any(ValueSetsSearchCriteria.class));
  }

  @Test
  void getValueSetsWithoutExpansionReturnsLatestDefinitionPerUrl() {
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    Bundle batchResponse = new Bundle().setType(Bundle.BundleType.BATCHRESPONSE);
    batchResponse
        .addEntry()
        .setResource(searchSetOf(valueSetDefinition("1.2.3", "20230217", "3", "FirstValueSet")));
    batchResponse
        .addEntry()
        .setResource(
            searchSetOf(
                valueSetDefinition("4.5.6", "20240101", "5", "ActiveVersion"),
                valueSetDefinition("4.5.6", "20250101", "12", "DraftVersion")));
    when(fhirTerminologyServiceWebClient.getValueSetResourcesWithoutExpansion(
            anyString(), any(ValueSetsSearchCriteria.class)))
        .thenReturn(FhirContext.forR4().newJsonParser().encodeResourceToString(batchResponse));
    var valueSetsSearchCriteria =
        ValueSetsSearchCriteria.builder()
            .includeExpansion(false)
            .includeDraft("true")
            .activeOnly("true")
            .manifestExpansion(
                ManifestExpansion.builder()
                    .fullUrl("https://cts.nlm.nih.gov/fhir/Library/ecqm-update-2022-05-05")
                    .build())
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .url(" http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3 ")
                        .build(),
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .oid("1.2.3")
                        .offset(0)
                        .count(1000)
                        .build(),
                    ValueSetsSearchCriteria.ValueSetParams.builder().oid("4.5.6").build(),
                    ValueSetsSearchCriteria.ValueSetParams.builder().oid(" ").build()))
            .build();

    List<ValueSet> result = fhirTerminologyService.getValueSets(valueSetsSearchCriteria, umlsUser);

    assertEquals(2, result.size());
    assertEquals("FirstValueSet", result.get(0).getName());
    assertEquals("DraftVersion", result.get(1).getName());
    assertFalse(result.get(1).hasExpansion());
    ArgumentCaptor<ValueSetsSearchCriteria> criteriaCaptor =
        ArgumentCaptor.forClass(ValueSetsSearchCriteria.class);
    verify(fhirTerminologyServiceWebClient, times(1))
        .getValueSetResourcesWithoutExpansion(eq(TEST_API_KEY), criteriaCaptor.capture());
    ValueSetsSearchCriteria definitionCriteria = criteriaCaptor.getValue();
    assertEquals(
        List.of(
            ValueSetsSearchCriteria.ValueSetParams.builder()
                .oid("1.2.3")
                .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3")
                .build(),
            ValueSetsSearchCriteria.ValueSetParams.builder()
                .oid("4.5.6")
                .url("http://cts.nlm.nih.gov/fhir/ValueSet/4.5.6")
                .build()),
        definitionCriteria.getValueSetParams());
    assertNull(definitionCriteria.getManifestExpansion());
    verify(fhirTerminologyServiceWebClient, never())
        .getValueSetResourcesWithExpansion(anyString(), any(ValueSetsSearchCriteria.class));
  }

  @Test
  void getValueSetsWithoutExpansionThrowsExceptionWithFailedOidWhenVsacReturnsNoValueSet() {
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue().setDiagnostics("ValueSet 4.5.6 not found");
    Bundle batchResponse = new Bundle().setType(Bundle.BundleType.BATCHRESPONSE);
    batchResponse
        .addEntry()
        .setResource(searchSetOf(valueSetDefinition("1.2.3", "20230217", "3", "Found")));
    batchResponse
        .addEntry()
        .setResponse(
            new Bundle.BundleEntryResponseComponent()
                .setStatus("400 Bad Request")
                .setOutcome(outcome));
    when(fhirTerminologyServiceWebClient.getValueSetResourcesWithoutExpansion(
            anyString(), any(ValueSetsSearchCriteria.class)))
        .thenReturn(FhirContext.forR4().newJsonParser().encodeResourceToString(batchResponse));
    var valueSetsSearchCriteria =
        ValueSetsSearchCriteria.builder()
            .includeExpansion(false)
            .manifestExpansion(
                ManifestExpansion.builder()
                    .fullUrl("https://cts.nlm.nih.gov/fhir/Library/ecqm-update-2022-05-05")
                    .build())
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder().oid("1.2.3").build(),
                    ValueSetsSearchCriteria.ValueSetParams.builder().oid("1.2.3").build(),
                    ValueSetsSearchCriteria.ValueSetParams.builder().oid("4.5.6").build()))
            .build();

    VsacParseBatchValueSetExpansionException ex =
        assertThrows(
            VsacParseBatchValueSetExpansionException.class,
            () -> fhirTerminologyService.getValueSets(valueSetsSearchCriteria, umlsUser));

    assertEquals("Failed to fetch VSAC value sets", ex.getMessage());
    assertEquals("4.5.6", ex.getOid());
    assertNull(ex.getManifestExpansionFullUrl());
    assertEquals(
        "ValueSet 4.5.6 not found", ex.getOperationOutcome().getIssueFirstRep().getDiagnostics());
  }

  @Test
  void getValueSetsWithoutExpansionThrowsExceptionWhenNoActiveOrDraftDefinitionIsFound() {
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    Bundle batchResponse = new Bundle().setType(Bundle.BundleType.BATCHRESPONSE);
    batchResponse.addEntry().setResource(searchSetOf());
    when(fhirTerminologyServiceWebClient.getValueSetResourcesWithoutExpansion(
            anyString(), any(ValueSetsSearchCriteria.class)))
        .thenReturn(FhirContext.forR4().newJsonParser().encodeResourceToString(batchResponse));
    var valueSetsSearchCriteria =
        ValueSetsSearchCriteria.builder()
            .includeExpansion(false)
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.4.5.6")
                        .build()))
            .build();

    VsacParseBatchValueSetExpansionException ex =
        assertThrows(
            VsacParseBatchValueSetExpansionException.class,
            () -> fhirTerminologyService.getValueSets(valueSetsSearchCriteria, umlsUser));

    assertEquals("Failed to fetch VSAC value sets", ex.getMessage());
    assertEquals("1.4.5.6", ex.getOid());
    assertEquals(
        "No active or draft ValueSet found for url [http://cts.nlm.nih.gov/fhir/ValueSet/1.4.5.6]",
        ex.getOperationOutcome().getIssueFirstRep().getDiagnostics());
  }

  @Test
  void getValueSetsWithoutExpansionReturnsEmptyListWhenNoUrlOrOidIsProvided() {
    var valueSetsSearchCriteria =
        ValueSetsSearchCriteria.builder()
            .includeExpansion(false)
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder().build(),
                    ValueSetsSearchCriteria.ValueSetParams.builder().oid("").url(" ").build()))
            .build();

    assertTrue(fhirTerminologyService.getValueSets(valueSetsSearchCriteria, umlsUser).isEmpty());
    verifyNoInteractions(fhirTerminologyServiceWebClient);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1046.265|20230201",
        "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1046.265' version '20230201",
        "https://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1046.265",
        "http://example.com/fhir/ValueSet/2.16.840.1.113762.1.4.1046.265"
      })
  void getValueSetsRejectsInvalidUrlBeforeCallingVsac(String invalidUrl) {
    var valueSetsSearchCriteria =
        ValueSetsSearchCriteria.builder()
            .manifestExpansion(
                ManifestExpansion.builder()
                    .fullUrl("http://cts.nlm.nih.gov/fhir/Library/ecqm-update-2026-05-14")
                    .build())
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3")
                        .build(),
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .oid("2.16.840.1.113762.1.4.1046.265")
                        .url(invalidUrl)
                        .build()))
            .build();

    VsacParseBatchValueSetExpansionException ex =
        assertThrows(
            VsacParseBatchValueSetExpansionException.class,
            () -> fhirTerminologyService.getValueSets(valueSetsSearchCriteria, umlsUser));

    assertEquals("Valueset is not a valid URL format", ex.getMessage());
    assertEquals("2.16.840.1.113762.1.4.1046.265", ex.getOid());
    assertEquals(
        "http://cts.nlm.nih.gov/fhir/Library/ecqm-update-2026-05-14",
        ex.getManifestExpansionFullUrl());
    assertEquals(
        "Valueset url ["
            + invalidUrl
            + "] is not a valid URL format, expected "
            + "http://cts.nlm.nih.gov/fhir/ValueSet/{oid} without a version",
        ex.getOperationOutcome().getIssueFirstRep().getDiagnostics());
    verifyNoInteractions(fhirTerminologyServiceWebClient);
  }

  @Test
  void getValueSetsWithoutExpansionRejectsInvalidUrlBeforeCallingVsac() {
    var valueSetsSearchCriteria =
        ValueSetsSearchCriteria.builder()
            .includeExpansion(false)
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .url(
                            "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1046.265"
                                + "|20230201")
                        .build()))
            .build();

    VsacParseBatchValueSetExpansionException ex =
        assertThrows(
            VsacParseBatchValueSetExpansionException.class,
            () -> fhirTerminologyService.getValueSets(valueSetsSearchCriteria, umlsUser));

    assertEquals("Valueset is not a valid URL format", ex.getMessage());
    assertNull(ex.getOid());
    verifyNoInteractions(fhirTerminologyServiceWebClient);
  }

  @Test
  void getsValueSetsExpansionsForQdmRejectsInvalidUrlBeforeCallingVsac() {
    var valueSetsSearchCriteria =
        ValueSetsSearchCriteria.builder()
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .oid("2.16.840.1.113762.1.4.1046.265")
                        .url(
                            "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1046.265"
                                + "|20230201")
                        .build()))
            .build();

    VsacParseBatchValueSetExpansionException ex =
        assertThrows(
            VsacParseBatchValueSetExpansionException.class,
            () ->
                fhirTerminologyService.getValueSetsExpansionsForQdm(
                    valueSetsSearchCriteria, umlsUser));

    assertEquals("Valueset is not a valid URL format", ex.getMessage());
    assertEquals("2.16.840.1.113762.1.4.1046.265", ex.getOid());
    verifyNoInteractions(fhirTerminologyServiceWebClient);
  }

  @Test
  void getValueSetsRequestsNextExpansionPageWithTheSameUrl() {
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    IParser parser = FhirContext.forR4().newJsonParser();
    Bundle firstPage = new Bundle().setType(Bundle.BundleType.BATCHRESPONSE);
    firstPage.addEntry().setResource(expandedValueSetPage("1.2.3", 0, "A"));
    Bundle secondPage = new Bundle().setType(Bundle.BundleType.BATCHRESPONSE);
    secondPage.addEntry().setResource(expandedValueSetPage("1.2.3", 1000, "B"));
    when(fhirTerminologyServiceWebClient.getValueSetResourcesWithExpansion(
            anyString(), any(ValueSetsSearchCriteria.class)))
        .thenReturn(
            parser.encodeResourceToString(firstPage), parser.encodeResourceToString(secondPage));
    ManifestExpansion manifestExpansion =
        ManifestExpansion.builder()
            .fullUrl("http://cts.nlm.nih.gov/fhir/Library/ecqm-update-2026-05-14")
            .build();
    ValueSetsSearchCriteria search =
        ValueSetsSearchCriteria.builder()
            .includeDraft("true")
            .activeOnly("true")
            .manifestExpansion(manifestExpansion)
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3")
                        .build()))
            .build();

    List<ValueSet> result = fhirTerminologyService.getValueSets(search, umlsUser);

    assertEquals(1, result.size());
    assertEquals(2, result.get(0).getExpansion().getContains().size());
    ArgumentCaptor<ValueSetsSearchCriteria> criteriaCaptor =
        ArgumentCaptor.forClass(ValueSetsSearchCriteria.class);
    verify(fhirTerminologyServiceWebClient, times(2))
        .getValueSetResourcesWithExpansion(eq(TEST_API_KEY), criteriaCaptor.capture());
    assertEquals(
        List.of(
            ValueSetsSearchCriteria.ValueSetParams.builder()
                .oid("1.2.3")
                .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3")
                .offset(0)
                .count(1000)
                .build()),
        criteriaCaptor.getAllValues().get(0).getValueSetParams());
    ValueSetsSearchCriteria nextPageCriteria = criteriaCaptor.getAllValues().get(1);
    assertEquals(
        List.of(
            ValueSetsSearchCriteria.ValueSetParams.builder()
                .oid("1.2.3")
                .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3")
                .offset(1000)
                .count(1000)
                .build()),
        nextPageCriteria.getValueSetParams());
    assertEquals("true", nextPageCriteria.getIncludeDraft());
    assertEquals("true", nextPageCriteria.getActiveOnly());
    assertEquals(manifestExpansion, nextPageCriteria.getManifestExpansion());
  }

  private static Bundle searchSetOf(ValueSet... valueSets) {
    Bundle searchSet = new Bundle().setType(Bundle.BundleType.SEARCHSET);
    Arrays.stream(valueSets).forEach(valueSet -> searchSet.addEntry().setResource(valueSet));
    return searchSet;
  }

  private static ValueSet valueSetDefinition(
      String oid, String version, String metaVersionId, String name) {
    ValueSet valueSet = new ValueSet();
    valueSet.setId(oid);
    valueSet.setUrl("http://cts.nlm.nih.gov/fhir/ValueSet/" + oid);
    valueSet.setVersion(version);
    valueSet.setName(name);
    valueSet.getMeta().setVersionId(metaVersionId);
    return valueSet;
  }

  private static ValueSet expandedValueSetPage(String oid, int offset, String code) {
    ValueSet valueSet = new ValueSet();
    valueSet.setId(oid);
    valueSet.getExpansion().setTotal(2).setOffset(offset).addContains().setCode(code);
    return valueSet;
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
            .isInVsac(true)
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
            .isInVsac(true)
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

    when(fhirTerminologyServiceWebClient.getValueSetResourcesWithExpansion(
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
        .getValueSetResourcesWithExpansion(anyString(), any(ValueSetsSearchCriteria.class));
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

    when(fhirTerminologyServiceWebClient.getValueSetResourcesWithExpansion(
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
    vs1.setCompose(new ValueSet.ValueSetComposeComponent());
    vs1.getCompose()
        .addInclude(new ValueSet.ConceptSetComponent().setSystem("http://example.com/system"));

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

  @Test
  void searchValueSetsWithNextLinkFetchesAdditionalPage() {
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    IParser parser = FhirContext.forR4().newJsonParser();

    ValueSet vs1 = new ValueSet();
    vs1.setId("v1-next");
    vs1.setTitle("TitleNextOne");
    vs1.setUrl("http://example.com/ValueSet/v1-next");
    vs1.setVersion("1.0.0");
    vs1.setStatus(org.hl7.fhir.r4.model.Enumerations.PublicationStatus.ACTIVE);
    vs1.addIdentifier(new Identifier().setValue("urn:oid:1"));
    Meta meta1 = new Meta();
    meta1.setLastUpdated(new Date());
    meta1.setVersionId("1");
    vs1.setMeta(meta1);
    vs1.setCompose(new ValueSet.ValueSetComposeComponent());
    vs1.getCompose()
        .addInclude(new ValueSet.ConceptSetComponent().setSystem("http://example.com/system"));

    ValueSet vs2 = new ValueSet();
    vs2.setId("v2-next");
    vs2.setTitle("TitleNextTwo");
    vs2.setUrl("http://example.com/ValueSet/v2-next");
    vs2.setVersion("2.0.0");
    vs2.setStatus(org.hl7.fhir.r4.model.Enumerations.PublicationStatus.ACTIVE);
    vs2.addIdentifier(new Identifier().setValue("urn:oid:2"));
    Meta meta2 = new Meta();
    meta2.setLastUpdated(new Date());
    meta2.setVersionId("2");
    vs2.setMeta(meta2);
    vs2.setCompose(new ValueSet.ValueSetComposeComponent());
    vs2.getCompose()
        .addInclude(new ValueSet.ConceptSetComponent().setSystem("http://example.com/system"));

    Bundle bundle1 = new Bundle();
    bundle1.addEntry(new Bundle.BundleEntryComponent().setResource(vs1));
    bundle1.addLink(
        new Bundle.BundleLinkComponent()
            .setRelation("next")
            .setUrl("http://example.com/next?page=2"));

    Bundle bundle2 = new Bundle();
    bundle2.addEntry(new Bundle.BundleEntryComponent().setResource(vs2));

    when(fhirTerminologyServiceWebClient.searchValueSets(anyString(), anyMap()))
        .thenReturn(parser.encodeResourceToString(bundle1));
    when(fhirTerminologyServiceWebClient.fetchResourceFromVsac(
            "https://example.com/next?page=2", TEST_API_KEY, "bundle"))
        .thenReturn(parser.encodeResourceToString(bundle2));

    ValueSetSearchResult result = fhirTerminologyService.searchValueSets(TEST_API_KEY, Map.of());

    assertEquals(2, result.getValueSets().size());
    assertEquals("urn:oid:2", result.getValueSets().get(1).getOid());
    verify(fhirTerminologyServiceWebClient)
        .fetchResourceFromVsac("https://example.com/next?page=2", TEST_API_KEY, "bundle");
  }

  @Test
  void validateCodeSystemMappingReturnsMissingCodesOnly() {
    when(codeSystemRepository.findAllByName("alpha")).thenReturn(Collections.emptyList());
    when(codeSystemRepository.findAllByName("beta"))
        .thenReturn(List.of(new gov.cms.madie.terminology.models.CodeSystem()));

    List<String> missing =
        fhirTerminologyService.validateCodeSystemMapping(List.of("alpha", "beta"));

    assertEquals(List.of("alpha"), missing);
  }

  @ParameterizedTest
  @CsvSource({
    "9, 10, Second, false",
    "10, 9, First, false",
    "9, 10, Second, true",
    "10, 9, First, true",
    ", 10, Second, true",
    "10, , First, true",
    ", , First, false",
    ", , First, true",
    "10, 10, First, false",
    "2147483648, 2147483649, Second, false"
  })
  void searchValueSetsReturnsOnlyLatestVersion(
      String firstVersionId, String secondVersionId, String expectedTitle, boolean separatePages) {
    IParser parser = FhirContext.forR4().newJsonParser();
    when(fhirContext.newJsonParser()).thenReturn(parser);

    ValueSet first = new ValueSet();
    first.setTitle("First");
    first.setVersion("2025-01-01");
    first.addIdentifier(new Identifier().setValue("urn:oid:1"));
    first.setMeta(new Meta().setVersionId(firstVersionId).setLastUpdated(new Date()));
    ValueSet second = first.copy();
    second.setTitle("Second");
    second.setVersion("2024-01-01");
    second.getMeta().setVersionId(secondVersionId);
    ValueSet unrelated = first.copy();
    unrelated.addIdentifier(new Identifier().setValue("urn:oid:2"));

    Bundle firstPage = new Bundle();
    firstPage.addEntry().setResource(first);
    firstPage.addEntry().setResource(unrelated);
    if (separatePages) {
      firstPage.addLink().setRelation("next").setUrl("http://example.com/next?page=2");
      Bundle secondPage = new Bundle();
      secondPage.addEntry().setResource(second);
      when(fhirTerminologyServiceWebClient.fetchResourceFromVsac(
              "https://example.com/next?page=2", TEST_API_KEY, "bundle"))
          .thenReturn(parser.encodeResourceToString(secondPage));
    } else {
      firstPage.addEntry().setResource(second);
    }
    String response = parser.encodeResourceToString(firstPage);
    when(fhirTerminologyServiceWebClient.searchValueSets(TEST_API_KEY, Map.of()))
        .thenReturn(response);

    ValueSetSearchResult result = fhirTerminologyService.searchValueSets(TEST_API_KEY, Map.of());

    assertEquals(2, result.getValueSets().size());
    assertEquals("urn:oid:1", result.getValueSets().get(0).getOid());
    assertEquals(expectedTitle, result.getValueSets().get(0).getTitle());
    assertEquals("urn:oid:2", result.getValueSets().get(1).getOid());
    assertEquals(response, result.getResultBundle());
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

  @Test
  void retrieveCodeCodeSystemNotInVsac() {
    String codeName = "test-code";
    String codeSystemName = "LOINC";
    String version = "2.40";
    // Mock codeSystem with isInVsac() == false
    gov.cms.madie.terminology.models.CodeSystem codeSystem =
        mock(gov.cms.madie.terminology.models.CodeSystem.class);
    when(codeSystem.isInVsac()).thenReturn(false);
    when(codeSystemRepository.findByNameAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.of(codeSystem));
    Code result =
        fhirTerminologyService.retrieveCode(codeName, codeSystemName, version, TEST_API_KEY);
    assertNull(result);
  }

  @Test
  void retrieveCodeCodeSystemInVsac() {
    String codeName = "test-code";
    String codeSystemName = "LOINC";
    String version = "2.40";
    // Mock codeSystem with isInVsac() == true
    gov.cms.madie.terminology.models.CodeSystem codeSystem =
        mock(gov.cms.madie.terminology.models.CodeSystem.class);
    when(codeSystem.isInVsac()).thenReturn(true);
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
}
