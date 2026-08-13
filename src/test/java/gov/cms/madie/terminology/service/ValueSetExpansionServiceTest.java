package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.terminology.dto.ValueSetDisplayForAdmin;
import gov.cms.madie.terminology.exceptions.CodeSystemNotFoundException;
import gov.cms.madie.terminology.exceptions.DuplicateValueSetException;
import gov.cms.madie.terminology.exceptions.InvalidValueSetException;
import gov.cms.madie.terminology.exceptions.ResourceNotFoundException;
import gov.cms.madie.terminology.exceptions.ValueSetExpansionException;
import gov.cms.madie.terminology.exceptions.ValueSetNotFoundException;
import gov.cms.madie.terminology.exceptions.VsacBatchValueSetExpansionException;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.MadieValueSet;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.repositories.ValueSetExpansionRepository;
import gov.cms.madie.terminology.util.ImplementationGuideManager;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import gov.cms.madie.terminology.webclient.TxTerminologyServiceWebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatusCode;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValueSetExpansionServiceTest {
  private static final String VS_URL = "http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3";
  private static final String VS_VERSION = "20230401";
  private static final String IG_NAME = "hl7.fhir.us.core";
  private static final String IG_VERSION = "6.1.0";
  private static final String VSAC_BASE_EXPAND_URL =
      "https://cts.nlm.nih.gov/fhir/ValueSet/$expand?url=";

  @Mock private ImplementationGuideManager implementationGuideManager;
  @Mock private ValueSetExpansionRepository vseRepo;
  @Mock private CodeSystemRepository csRepo;
  @Mock private TxTerminologyServiceWebClient txTerminologyServiceWebClient;
  @Mock private FhirContext fhirContext;
  @Mock private FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;
  @Mock private FhirTerminologyService fhirTerminologyService;

  @InjectMocks private ValueSetExpansionService valueSetExpansionService;

  private MadieValueSet madieValueSet;
  private IParser realParser;

  /** Minimal FHIR ValueSet JSON for TxFHIR parsing tests. * */
  private static final String MOCK_VALUE_SET_JSON =
      """
    {
      "resourceType": "ValueSet",
      "id": "test-vs",
      "url": "http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3",
      "version": "20230401",
      "status": "active",
      "expansion": {
        "contains": [
          { "system": "http://snomed.info/sct", "code": "123456789", "display": "Test Concept" }
        ]
      }
    }
    """;

  /** Minimal FHIR Bundle wrapping a ValueSet — mirrors what VSAC returns (no pagination). */
  private static final String MOCK_VSAC_BUNDLE_JSON =
      """
    {
      "resourceType": "Bundle",
      "type": "searchset",
      "entry": [
        {
          "resource": {
            "resourceType": "ValueSet",
            "id": "vsac-vs",
            "url": "http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3",
            "version": "20230401",
            "status": "active",
            "expansion": {
              "contains": [
                { "system": "http://snomed.info/sct", "code": "987654321", "display": "VSAC Concept" }
              ]
            }
          }
        }
      ]
    }
    """;

  /** Bundle with expansion.total = 1000 — boundary: pagination must NOT trigger. */
  private static final String MOCK_VSAC_BUNDLE_TOTAL_1000_JSON =
      """
    {
      "resourceType": "Bundle",
      "type": "searchset",
      "entry": [
        {
          "resource": {
            "resourceType": "ValueSet",
            "id": "vsac-vs-exact-1000",
            "url": "http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3",
            "version": "20230401",
            "status": "active",
            "expansion": {
              "total": 1000,
              "offset": 0,
              "contains": [
                { "system": "http://snomed.info/sct", "code": "111111111", "display": "Concept" }
              ]
            }
          }
        }
      ]
    }
    """;

  /**
   * Bundle with expansion.total = 1001 (page 1 of 2) — pagination must trigger a second request.
   */
  private static final String MOCK_VSAC_BUNDLE_PAGINATED_PAGE1_JSON =
      """
    {
      "resourceType": "Bundle",
      "type": "searchset",
      "entry": [
        {
          "resource": {
            "resourceType": "ValueSet",
            "id": "vsac-vs-paged",
            "url": "http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3",
            "version": "20230401",
            "status": "active",
            "expansion": {
              "total": 1001,
              "offset": 0,
              "contains": [
                { "system": "http://snomed.info/sct", "code": "111111111", "display": "Page1 Concept" }
              ]
            }
          }
        }
      ]
    }
    """;

  /** Bundle page 2 of 2 for the paginated expansion test. */
  private static final String MOCK_VSAC_BUNDLE_PAGINATED_PAGE2_JSON =
      """
    {
      "resourceType": "Bundle",
      "type": "searchset",
      "entry": [
        {
          "resource": {
            "resourceType": "ValueSet",
            "id": "vsac-vs-paged",
            "url": "http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3",
            "version": "20230401",
            "status": "active",
            "expansion": {
              "total": 1001,
              "offset": 1000,
              "contains": [
                { "system": "http://snomed.info/sct", "code": "222222222", "display": "Page2 Concept" }
              ]
            }
          }
        }
      ]
    }
    """;

  @BeforeEach
  void setUp() {
    madieValueSet = MadieValueSet.builder().url(VS_URL).version(VS_VERSION).build();
    realParser = FhirContext.forR4().newJsonParser();
  }

  // ---------------------------------------------------------------------------
  // getImplementationGuides()
  // ---------------------------------------------------------------------------

  @Test
  void getImplementationGuidesReturnsList() {
    when(implementationGuideManager.getImplementationGuides())
        .thenReturn(List.of("hl7.fhir.us.core v6.1.0", "hl7.fhir.us.qicore v6.0.0"));

    List<String> result = valueSetExpansionService.getImplementationGuides();

    assertThat(result.size(), is(equalTo(2)));
    assertTrue(result.contains("hl7.fhir.us.core v6.1.0"));
    assertTrue(result.contains("hl7.fhir.us.qicore v6.0.0"));
    verify(implementationGuideManager, times(1)).getImplementationGuides();
  }

  @Test
  void getImplementationGuidesReturnsEmptyListWhenManagerThrows() {
    when(implementationGuideManager.getImplementationGuides())
        .thenThrow(new RuntimeException("IG load failure"));

    List<String> result = valueSetExpansionService.getImplementationGuides();

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void getImplementationGuidesReturnsEmptyListWhenNoneLoaded() {
    when(implementationGuideManager.getImplementationGuides()).thenReturn(Collections.emptyList());

    List<String> result = valueSetExpansionService.getImplementationGuides();

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  // ---------------------------------------------------------------------------
  // getValueSetDependencies(String igName, String igVersion)
  // ---------------------------------------------------------------------------

  @Test
  void getValueSetDependenciesByIgReturnsFormattedUrlWithVersion() {
    MadieValueSet vs = MadieValueSet.builder().url(VS_URL).version(VS_VERSION).build();
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(vs));

    List<String> result = valueSetExpansionService.getValueSetDependencies(IG_NAME, IG_VERSION);

    assertThat(result.size(), is(equalTo(1)));
    assertThat(result.get(0), is(equalTo(VS_URL + "|" + VS_VERSION)));
    verify(implementationGuideManager, times(1)).getValueSetDependencies(IG_NAME, IG_VERSION);
  }

  @Test
  void getValueSetDependenciesByIgOmitsVersionSuffixWhenVersionIsNull() {
    MadieValueSet vs = MadieValueSet.builder().url(VS_URL).version(null).build();
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(vs));

    List<String> result = valueSetExpansionService.getValueSetDependencies(IG_NAME, IG_VERSION);

    assertThat(result.size(), is(equalTo(1)));
    assertThat(result.get(0), is(equalTo(VS_URL)));
  }

  @Test
  void getValueSetDependenciesByIgOmitsVersionSuffixWhenVersionIsBlank() {
    MadieValueSet vs = MadieValueSet.builder().url(VS_URL).version("  ").build();
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(vs));

    List<String> result = valueSetExpansionService.getValueSetDependencies(IG_NAME, IG_VERSION);

    assertThat(result.size(), is(equalTo(1)));
    assertThat(result.get(0), is(equalTo(VS_URL)));
  }

  @Test
  void getValueSetDependenciesByIgReturnsEmptyListWhenNoneFound() {
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(Collections.emptyList());

    List<String> result = valueSetExpansionService.getValueSetDependencies(IG_NAME, IG_VERSION);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  // ---------------------------------------------------------------------------
  // getValueSetDependencies() — no-arg overload
  // ---------------------------------------------------------------------------

  @Test
  void getValueSetDependenciesReturnsFormattedUrlsAcrossAllIgs() {
    MadieValueSet vs1 = MadieValueSet.builder().url(VS_URL).version(VS_VERSION).build();
    MadieValueSet vs2 =
        MadieValueSet.builder()
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/4.5.6")
            .version(null)
            .build();
    when(implementationGuideManager.getValueSetDependencies()).thenReturn(List.of(vs1, vs2));

    List<String> result = valueSetExpansionService.getValueSetDependencies();

    assertThat(result.size(), is(equalTo(2)));
    assertTrue(result.contains(VS_URL + "|" + VS_VERSION));
    assertTrue(result.contains("http://cts.nlm.nih.gov/fhir/ValueSet/4.5.6"));
    verify(implementationGuideManager, times(1)).getValueSetDependencies();
  }

  @Test
  void getValueSetDependenciesReturnsEmptyListWhenNoneFound() {
    when(implementationGuideManager.getValueSetDependencies()).thenReturn(Collections.emptyList());

    List<String> result = valueSetExpansionService.getValueSetDependencies();

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  // ---------------------------------------------------------------------------
  // updateIgValueSetDependencies(String igName, String version)
  // ---------------------------------------------------------------------------

  @Test
  void updateIgValueSetDependenciesExpandsAndSavesNewValueSet() {
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenReturn(MOCK_VALUE_SET_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.empty());

    valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);

    verify(vseRepo, times(1))
        .save(argThat(vs -> vs.getValueSet() != null && VS_URL.equals(vs.getUrl())));
  }

  @Test
  void updateIgValueSetDependenciesOverwritesExistingWhenNotManuallyModified() {
    MadieValueSet existing =
        MadieValueSet.builder().url(VS_URL).version(VS_VERSION).valueSet("{}").build();
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenReturn(MOCK_VALUE_SET_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.of(existing));

    valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);

    verify(vseRepo, times(1)).save(any());
  }

  @Test
  void updateIgValueSetDependenciesDoesNotSaveWhenManuallyModified() {
    MadieValueSet existing =
        MadieValueSet.builder()
            .url(VS_URL)
            .version(VS_VERSION)
            .valueSet("{}")
            .manuallyModified(true)
            .build();
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenReturn(MOCK_VALUE_SET_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.of(existing));

    valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);

    verify(vseRepo, never()).save(any());
  }

  @Test
  void updateIgValueSetDependenciesUpdatesExistingValueSetWhenExpansionIsNull() {
    MadieValueSet existing =
        MadieValueSet.builder().url(VS_URL).version(VS_VERSION).valueSet(null).build();
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenReturn(MOCK_VALUE_SET_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.of(existing));

    valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);

    ArgumentCaptor<MadieValueSet> captor = ArgumentCaptor.forClass(MadieValueSet.class);
    verify(vseRepo, times(1)).save(captor.capture());
    assertNotNull(captor.getValue().getValueSet());
  }

  @Test
  void updateIgValueSetDependenciesDoesNotSaveWhenExpansionFails() {
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION)).thenReturn(null);

    valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);

    verify(vseRepo, never()).save(any());
  }

  @Test
  void updateIgValueSetDependenciesDoesNotSaveWhenNoValueSetsFound() {
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(Collections.emptyList());

    valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);

    verify(vseRepo, never()).save(any());
  }

  @Test
  void updateIgValueSetDependenciesHandlesVsacValueSetExpansionException() {
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenThrow(
            new ValueSetExpansionException(
                "expansion failed", HttpStatusCode.valueOf(423), "expansion failed", "", "", ""));

    // Should not throw — exception is caught and logged
    assertDoesNotThrow(
        () -> valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION));
    verify(vseRepo, never()).save(any());
  }

  @Test
  void updateIgValueSetDependenciesFallsBackToVsacWhenTxFhirReturnsNotFound() {
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenThrow(
            new ResourceNotFoundException(
                "not found", HttpStatusCode.valueOf(404), "not found", "not found", ""));

    // VSAC fallback also returns nothing (TODO: MAT-10003)
    valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);

    verify(vseRepo, never()).save(any());
  }

  // ---------------------------------------------------------------------------
  // updateValueSetDependencies()
  // ---------------------------------------------------------------------------

  @Test
  void updateValueSetDependenciesExpandsAndSavesNewValueSet() {
    when(implementationGuideManager.getValueSetDependencies()).thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenReturn(MOCK_VALUE_SET_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.empty());

    valueSetExpansionService.updateValueSetDependencies();

    verify(vseRepo, times(1))
        .save(argThat(vs -> vs.getValueSet() != null && VS_URL.equals(vs.getUrl())));
  }

  @Test
  void updateValueSetDependenciesOverwritesExistingWhenNotManuallyModified() {
    MadieValueSet existing =
        MadieValueSet.builder().url(VS_URL).version(VS_VERSION).valueSet("{}").build();
    when(implementationGuideManager.getValueSetDependencies()).thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenReturn(MOCK_VALUE_SET_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.of(existing));

    valueSetExpansionService.updateValueSetDependencies();

    verify(vseRepo, times(1)).save(any());
  }

  @Test
  void updateValueSetDependenciesDoesNotSaveWhenManuallyModified() {
    MadieValueSet existing =
        MadieValueSet.builder()
            .url(VS_URL)
            .version(VS_VERSION)
            .valueSet("{}")
            .manuallyModified(true)
            .build();
    when(implementationGuideManager.getValueSetDependencies()).thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenReturn(MOCK_VALUE_SET_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.of(existing));

    valueSetExpansionService.updateValueSetDependencies();

    verify(vseRepo, never()).save(any());
  }

  @Test
  void updateValueSetDependenciesUpdatesExistingValueSetWhenExpansionIsNull() {
    MadieValueSet existing =
        MadieValueSet.builder().url(VS_URL).version(VS_VERSION).valueSet(null).build();
    when(implementationGuideManager.getValueSetDependencies()).thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenReturn(MOCK_VALUE_SET_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.of(existing));

    valueSetExpansionService.updateValueSetDependencies();

    ArgumentCaptor<MadieValueSet> captor = ArgumentCaptor.forClass(MadieValueSet.class);
    verify(vseRepo, times(1)).save(captor.capture());
    assertNotNull(captor.getValue().getValueSet());
  }

  @Test
  void updateValueSetDependenciesDoesNotSaveWhenExpansionFails() {
    when(implementationGuideManager.getValueSetDependencies()).thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION)).thenReturn(null);

    valueSetExpansionService.updateValueSetDependencies();

    verify(vseRepo, never()).save(any());
  }

  @Test
  void updateValueSetDependenciesDoesNotSaveWhenNoValueSetsFound() {
    when(implementationGuideManager.getValueSetDependencies()).thenReturn(Collections.emptyList());

    valueSetExpansionService.updateValueSetDependencies();

    verify(vseRepo, never()).save(any());
  }

  @Test
  void updateValueSetDependenciesHandlesVsacValueSetExpansionException() {
    when(implementationGuideManager.getValueSetDependencies()).thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenThrow(
            new ValueSetExpansionException(
                "expansion failed", HttpStatusCode.valueOf(423), "expansion failed", "", "", ""));

    assertDoesNotThrow(() -> valueSetExpansionService.updateValueSetDependencies());
    verify(vseRepo, never()).save(any());
  }

  @Test
  void updateValueSetDependenciesHandlesMultipleValueSetsIndependently() {
    MadieValueSet vs1 = MadieValueSet.builder().url(VS_URL).version(VS_VERSION).build();
    MadieValueSet vs2 =
        MadieValueSet.builder()
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/4.5.6")
            .version("20220101")
            .build();

    when(implementationGuideManager.getValueSetDependencies()).thenReturn(List.of(vs1, vs2));
    // vs1 expands successfully
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenReturn(MOCK_VALUE_SET_JSON);
    // vs2 throws
    when(txTerminologyServiceWebClient.getValueSetExpansion(
            "http://cts.nlm.nih.gov/fhir/ValueSet/4.5.6", "20220101"))
        .thenThrow(
            new ValueSetExpansionException(
                "expansion failed", HttpStatusCode.valueOf(423), "expansion failed", "", "", ""));
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.empty());

    valueSetExpansionService.updateValueSetDependencies();

    // Only vs1 should be saved
    verify(vseRepo, times(1)).save(argThat(vs -> VS_URL.equals(vs.getUrl())));
  }

  @Test
  void updateValueSetDependenciesSetsLastUpdatedOnSuccessfulExpansion() {
    when(implementationGuideManager.getValueSetDependencies()).thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenReturn(MOCK_VALUE_SET_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.empty());

    valueSetExpansionService.updateValueSetDependencies();

    ArgumentCaptor<MadieValueSet> captor = ArgumentCaptor.forClass(MadieValueSet.class);
    verify(vseRepo, times(1)).save(captor.capture());
    assertNotNull(captor.getValue().getLastUpdated());
  }

  @Test
  void updateValueSetDependenciesDoesNotSetLastUpdatedWhenExpansionFails() {
    when(implementationGuideManager.getValueSetDependencies()).thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION)).thenReturn(null);

    valueSetExpansionService.updateValueSetDependencies();

    assertNull(madieValueSet.getLastUpdated());
    verify(vseRepo, never()).save(any());
  }

  // ---------------------------------------------------------------------------
  // fetchExpansionFromVsac()  (tested via updateIgValueSetDependencies fallback)
  // ---------------------------------------------------------------------------

  @Test
  void fetchExpansionFromVsacBuildsUrlWithVersionAndSavesValueSet() {
    // TxFHIR throws → VSAC fallback is invoked
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenThrow(
            new ResourceNotFoundException(
                "not found", HttpStatusCode.valueOf(404), "not found", "not found", ""));
    when(fhirTerminologyServiceWebClient.fetchBatchResourcesFromVsac(
            List.of(VSAC_BASE_EXPAND_URL + VS_URL + "&valueSetVersion=" + VS_VERSION),
            null,
            "ValueSet"))
        .thenReturn(MOCK_VSAC_BUNDLE_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.empty());

    valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);

    ArgumentCaptor<MadieValueSet> captor = ArgumentCaptor.forClass(MadieValueSet.class);
    verify(vseRepo, times(1)).save(captor.capture());
    assertNotNull(captor.getValue().getValueSet());
    assertTrue(captor.getValue().getValueSet().contains("vsac-vs"));
  }

  @Test
  void fetchExpansionFromVsacBuildsUrlWithoutVersionWhenVersionIsBlank() {
    MadieValueSet vsNoVersion = MadieValueSet.builder().url(VS_URL).version("").build();

    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(vsNoVersion));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, ""))
        .thenThrow(
            new ResourceNotFoundException(
                "not found", HttpStatusCode.valueOf(404), "not found", "not found", ""));
    // Expect URL without "|version" suffix
    when(fhirTerminologyServiceWebClient.fetchBatchResourcesFromVsac(
            List.of(VSAC_BASE_EXPAND_URL + VS_URL), null, "ValueSet"))
        .thenReturn(MOCK_VSAC_BUNDLE_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, "")).thenReturn(Optional.empty());

    valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);

    ArgumentCaptor<MadieValueSet> captor = ArgumentCaptor.forClass(MadieValueSet.class);
    verify(vseRepo, times(1)).save(captor.capture());
    assertNotNull(captor.getValue().getValueSet());
  }

  @Test
  void fetchExpansionFromVsacBuildsUrlWithoutVersionWhenVersionIsNull() {
    MadieValueSet vsNullVersion = MadieValueSet.builder().url(VS_URL).version(null).build();

    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(vsNullVersion));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, null))
        .thenThrow(
            new ResourceNotFoundException(
                "not found", HttpStatusCode.valueOf(404), "not found", "not found", ""));
    when(fhirTerminologyServiceWebClient.fetchBatchResourcesFromVsac(
            List.of(VSAC_BASE_EXPAND_URL + VS_URL), null, "ValueSet"))
        .thenReturn(MOCK_VSAC_BUNDLE_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, null)).thenReturn(Optional.empty());

    valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);

    ArgumentCaptor<MadieValueSet> captor = ArgumentCaptor.forClass(MadieValueSet.class);
    verify(vseRepo, times(1)).save(captor.capture());
    assertNotNull(captor.getValue().getValueSet());
  }

  @Test
  void fetchExpansionFromVsacReturnsEmptyWhenVsacResponseIsBlank() {
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenThrow(
            new ResourceNotFoundException(
                "not found", HttpStatusCode.valueOf(404), "not found", "not found", ""));
    when(fhirTerminologyServiceWebClient.fetchBatchResourcesFromVsac(
            List.of(VSAC_BASE_EXPAND_URL + VS_URL + "&valueSetVersion=" + VS_VERSION),
            null,
            "ValueSet"))
        .thenReturn("   "); // blank response

    valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);

    // Expansion failed → nothing saved
    verify(vseRepo, never()).save(any());
  }

  @Test
  void fetchExpansionFromVsacReturnsEmptyWhenVsacResponseIsNull() {
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenThrow(
            new ResourceNotFoundException(
                "not found", HttpStatusCode.valueOf(404), "not found", "not found", ""));
    when(fhirTerminologyServiceWebClient.fetchBatchResourcesFromVsac(
            List.of(VSAC_BASE_EXPAND_URL + VS_URL + "&valueSetVersion=" + VS_VERSION),
            null,
            "ValueSet"))
        .thenReturn(null);

    valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);
    verify(vseRepo, never()).save(any());
  }

  @Test
  void fetchExpansionFromVsacReturnsEmptyAndDoesNotThrowWhenVsacClientThrows() {
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenThrow(
            new ResourceNotFoundException(
                "not found", HttpStatusCode.valueOf(404), "not found", "not found", ""));
    when(fhirTerminologyServiceWebClient.fetchBatchResourcesFromVsac(
            List.of(VSAC_BASE_EXPAND_URL + VS_URL + "&valueSetVersion=" + VS_VERSION),
            null,
            "ValueSet"))
        .thenThrow(
            new VsacBatchValueSetExpansionException(
                "VSAC error", HttpStatusCode.valueOf(500), "Internal Server Error", ""));

    // Exception is caught and logged — must not propagate
    assertDoesNotThrow(
        () -> valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION));
    verify(vseRepo, never()).save(any());
  }

  // ---------------------------------------------------------------------------
  // parseVsacExpansionResponse() / fetchRemainingPages() — pagination
  // ---------------------------------------------------------------------------

  @Test
  void parseVsacExpansionResponseFetchesAdditionalPagesWhenTotalExceeds1000() {
    // TxFHIR throws → VSAC fallback invoked
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenThrow(
            new ResourceNotFoundException(
                "not found", HttpStatusCode.valueOf(404), "not found", "not found", ""));

    String page1Url = VSAC_BASE_EXPAND_URL + VS_URL + "&valueSetVersion=" + VS_VERSION;
    String page2Url = page1Url + "&offset=1000";

    when(fhirTerminologyServiceWebClient.fetchBatchResourcesFromVsac(
            List.of(page1Url), null, "ValueSet"))
        .thenReturn(MOCK_VSAC_BUNDLE_PAGINATED_PAGE1_JSON);
    when(fhirTerminologyServiceWebClient.fetchBatchResourcesFromVsac(
            List.of(page2Url), null, "ValueSet"))
        .thenReturn(MOCK_VSAC_BUNDLE_PAGINATED_PAGE2_JSON);

    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.empty());

    valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);

    // Both pages fetched
    verify(fhirTerminologyServiceWebClient, times(1))
        .fetchBatchResourcesFromVsac(List.of(page1Url), null, "ValueSet");
    verify(fhirTerminologyServiceWebClient, times(1))
        .fetchBatchResourcesFromVsac(List.of(page2Url), null, "ValueSet");

    // Saved ValueSet should contain concepts from both pages
    ArgumentCaptor<MadieValueSet> captor = ArgumentCaptor.forClass(MadieValueSet.class);
    verify(vseRepo, times(1)).save(captor.capture());
    String savedValueSet = captor.getValue().getValueSet();
    assertNotNull(savedValueSet);
    assertTrue(savedValueSet.contains("111111111"), "Should contain page-1 concept");
    assertTrue(savedValueSet.contains("222222222"), "Should contain page-2 concept");
  }

  @Test
  void parseVsacExpansionResponseDoesNotFetchAdditionalPagesWhenTotalIsExactly1000() {
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenThrow(
            new ResourceNotFoundException(
                "not found", HttpStatusCode.valueOf(404), "not found", "not found", ""));

    String page1Url = VSAC_BASE_EXPAND_URL + VS_URL + "&valueSetVersion=" + VS_VERSION;

    when(fhirTerminologyServiceWebClient.fetchBatchResourcesFromVsac(
            List.of(page1Url), null, "ValueSet"))
        .thenReturn(MOCK_VSAC_BUNDLE_TOTAL_1000_JSON);

    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.empty());

    valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);

    // total == 1000 is not > 1000, so only one VSAC call made — no pagination
    verify(fhirTerminologyServiceWebClient, times(1))
        .fetchBatchResourcesFromVsac(anyList(), any(), anyString());
  }

  @Test
  void parseVsacExpansionResponseDoesNotFetchAdditionalPagesWhenTotalIs1000OrLess() {
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenThrow(
            new ResourceNotFoundException(
                "not found", HttpStatusCode.valueOf(404), "not found", "not found", ""));

    String page1Url = VSAC_BASE_EXPAND_URL + VS_URL + "&valueSetVersion=" + VS_VERSION;

    // Bundle with total = 1000 — no second page needed
    when(fhirTerminologyServiceWebClient.fetchBatchResourcesFromVsac(
            List.of(page1Url), null, "ValueSet"))
        .thenReturn(MOCK_VSAC_BUNDLE_JSON); // total not set → 0, which is ≤ 1000

    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.empty());

    valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);

    // Only one call to VSAC — no pagination
    verify(fhirTerminologyServiceWebClient, times(1))
        .fetchBatchResourcesFromVsac(anyList(), any(), anyString());
  }

  @Test
  void parseVsacExpansionResponseReturnsEmptyWhenResponseIsBlank() {
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenThrow(
            new ResourceNotFoundException(
                "not found", HttpStatusCode.valueOf(404), "not found", "not found", ""));
    when(fhirTerminologyServiceWebClient.fetchBatchResourcesFromVsac(anyList(), any(), anyString()))
        .thenReturn("  ");

    valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);

    verify(vseRepo, never()).save(any());
  }

  // ---------------------------------------------------------------------------
  // upsertValueSet()
  // ---------------------------------------------------------------------------

  @Test
  void upsertValueSetCreatesNewWhenNoExistingUrlVersionMatch() {
    MadieValueSet incoming =
        MadieValueSet.builder()
            .url(VS_URL)
            .version(VS_VERSION)
            .valueSet(MOCK_VALUE_SET_JSON)
            .build();

    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.empty());
    when(vseRepo.save(any(MadieValueSet.class))).thenReturn(incoming);
    when(fhirContext.newJsonParser()).thenReturn(realParser);

    MadieValueSet result = valueSetExpansionService.upsertValueSet(incoming);

    assertTrue(result.isManuallyModified());
    assertNotNull(result.getLastUpdated());
    verify(vseRepo, times(1)).save(incoming);
  }

  @Test
  void upsertValueSetPreservesExistingIdWhenUrlAndVersionMatch() {
    MadieValueSet existing =
        MadieValueSet.builder()
            .id("existing-id")
            .url(VS_URL)
            .version(VS_VERSION)
            .valueSet(MOCK_VALUE_SET_JSON)
            .build();
    MadieValueSet incoming =
        MadieValueSet.builder()
            .url(VS_URL)
            .version(VS_VERSION)
            .valueSet(MOCK_VALUE_SET_JSON)
            .build();

    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.of(existing));
    when(vseRepo.save(any(MadieValueSet.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(fhirContext.newJsonParser()).thenReturn(realParser);

    MadieValueSet result = valueSetExpansionService.upsertValueSet(incoming);

    assertEquals("existing-id", result.getId());
    assertTrue(result.isManuallyModified());
    assertNotNull(result.getLastUpdated());
    verify(vseRepo, times(1)).save(incoming);
  }

  // ---------------------------------------------------------------------------
  // addValueSet()
  // ---------------------------------------------------------------------------

  private ValueSetDisplayForAdmin addRequest(String url, String version, String valueSetJson) {
    return ValueSetDisplayForAdmin.builder()
        .url(url)
        .version(version)
        .valueSet(valueSetJson)
        .build();
  }

  @Test
  void addValueSetSavesNewValueSetWithManuallyModifiedAndLastUpdated() {
    ValueSetDisplayForAdmin request = addRequest(VS_URL, VS_VERSION, MOCK_VALUE_SET_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.empty());
    when(vseRepo.save(any(MadieValueSet.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    MadieValueSet result = valueSetExpansionService.addValueSet(request);

    assertEquals(VS_URL, result.getUrl());
    assertEquals(VS_VERSION, result.getVersion());
    assertEquals(MOCK_VALUE_SET_JSON, result.getValueSet());
    assertTrue(result.isManuallyModified());
    assertNotNull(result.getLastUpdated());

    ArgumentCaptor<MadieValueSet> captor = ArgumentCaptor.forClass(MadieValueSet.class);
    verify(vseRepo, times(1)).save(captor.capture());
    assertTrue(captor.getValue().isManuallyModified());
    assertNotNull(captor.getValue().getLastUpdated());
  }

  @Test
  void addValueSetSavesWhenVersionIsBlankAndSkipsVersionValidation() {
    // JSON has a version, but the user left version blank — version check must be skipped
    ValueSetDisplayForAdmin request = addRequest(VS_URL, "", MOCK_VALUE_SET_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, "")).thenReturn(Optional.empty());
    when(vseRepo.save(any(MadieValueSet.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    MadieValueSet result = valueSetExpansionService.addValueSet(request);

    assertTrue(result.isManuallyModified());
    verify(vseRepo, times(1)).save(any(MadieValueSet.class));
  }

  @Test
  void addValueSetThrowsWhenUrlIsBlank() {
    ValueSetDisplayForAdmin request = addRequest("  ", VS_VERSION, MOCK_VALUE_SET_JSON);

    InvalidValueSetException ex =
        assertThrows(
            InvalidValueSetException.class, () -> valueSetExpansionService.addValueSet(request));
    assertTrue(ex.getMessage().contains("URL is required"));
    verify(vseRepo, never()).save(any());
  }

  @Test
  void addValueSetThrowsWhenValueSetJsonIsBlank() {
    ValueSetDisplayForAdmin request = addRequest(VS_URL, VS_VERSION, "  ");

    InvalidValueSetException ex =
        assertThrows(
            InvalidValueSetException.class, () -> valueSetExpansionService.addValueSet(request));
    assertTrue(ex.getMessage().contains("expansion JSON is required"));
    verify(vseRepo, never()).save(any());
  }

  @Test
  void addValueSetThrowsWhenJsonIsSyntacticallyInvalid() {
    ValueSetDisplayForAdmin request = addRequest(VS_URL, VS_VERSION, "{ not valid json");
    when(fhirContext.newJsonParser()).thenReturn(realParser);

    InvalidValueSetException ex =
        assertThrows(
            InvalidValueSetException.class, () -> valueSetExpansionService.addValueSet(request));
    assertTrue(ex.getMessage().contains("not valid"));
    verify(vseRepo, never()).save(any());
  }

  @Test
  void addValueSetThrowsWhenJsonIsNotAValueSetResource() {
    String patientJson =
        """
        { "resourceType": "Patient", "id": "p1" }
        """;
    ValueSetDisplayForAdmin request = addRequest(VS_URL, VS_VERSION, patientJson);
    when(fhirContext.newJsonParser()).thenReturn(realParser);

    InvalidValueSetException ex =
        assertThrows(
            InvalidValueSetException.class, () -> valueSetExpansionService.addValueSet(request));
    assertTrue(ex.getMessage().contains("not a FHIR ValueSet"));
    verify(vseRepo, never()).save(any());
  }

  @Test
  void addValueSetThrowsWhenJsonUrlDoesNotMatchProvidedUrl() {
    ValueSetDisplayForAdmin request =
        addRequest("http://cts.nlm.nih.gov/fhir/ValueSet/9.9.9", VS_VERSION, MOCK_VALUE_SET_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);

    InvalidValueSetException ex =
        assertThrows(
            InvalidValueSetException.class, () -> valueSetExpansionService.addValueSet(request));
    assertTrue(
        ex.getMessage()
            .contains("Expansion JSON URL and/or version do not match the provided values"));
    verify(vseRepo, never()).save(any());
  }

  @Test
  void addValueSetThrowsWhenJsonVersionDoesNotMatchProvidedVersion() {
    ValueSetDisplayForAdmin request = addRequest(VS_URL, "99999999", MOCK_VALUE_SET_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);

    InvalidValueSetException ex =
        assertThrows(
            InvalidValueSetException.class, () -> valueSetExpansionService.addValueSet(request));
    assertTrue(
        ex.getMessage()
            .contains("Expansion JSON URL and/or version do not match the provided values"));
    verify(vseRepo, never()).save(any());
  }

  @Test
  void addValueSetThrowsDuplicateWhenUrlAndVersionAlreadyExist() {
    ValueSetDisplayForAdmin request = addRequest(VS_URL, VS_VERSION, MOCK_VALUE_SET_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION))
        .thenReturn(Optional.of(MadieValueSet.builder().url(VS_URL).version(VS_VERSION).build()));

    DuplicateValueSetException ex =
        assertThrows(
            DuplicateValueSetException.class, () -> valueSetExpansionService.addValueSet(request));
    assertTrue(ex.getMessage().contains(VS_URL));
    verify(vseRepo, never()).save(any());
  }

  // ---------------------------------------------------------------------------
  // deleteValueSet()
  // ---------------------------------------------------------------------------

  @Test
  void deleteValueSetSuccessfully() {
    MadieValueSet existing =
        MadieValueSet.builder().id("test-id").url(VS_URL).version(VS_VERSION).build();

    when(vseRepo.findById("test-id")).thenReturn(Optional.of(existing));

    valueSetExpansionService.deleteValueSet("test-id");

    verify(vseRepo, times(1)).deleteById("test-id");
  }

  @Test
  void deleteValueSetThrowsNotFoundWhenIdDoesNotExist() {
    when(vseRepo.findById("nonexistent")).thenReturn(Optional.empty());

    assertThrows(
        ValueSetNotFoundException.class,
        () -> valueSetExpansionService.deleteValueSet("nonexistent"));
    verify(vseRepo, never()).deleteById(anyString());
  }

  @Test
  void returnsValueSetWhenFound() {
    when(vseRepo.findByUrlAndVersionOrNull(VS_URL, VS_VERSION))
        .thenReturn(Optional.of(madieValueSet));

    MadieValueSet result = valueSetExpansionService.getValueSet(VS_URL, VS_VERSION);

    assertThat(result, is(equalTo(madieValueSet)));
    verify(vseRepo, times(1)).findByUrlAndVersionOrNull(VS_URL, VS_VERSION);
  }

  @Test
  void throwsValueSetNotFoundExceptionWhenValueSetMissing() {
    when(vseRepo.findByUrlAndVersionOrNull(VS_URL, VS_VERSION)).thenReturn(Optional.empty());

    ValueSetNotFoundException exception =
        assertThrows(
            ValueSetNotFoundException.class,
            () -> valueSetExpansionService.getValueSet(VS_URL, VS_VERSION));

    assertTrue(exception.getMessage().contains(VS_URL));
    assertTrue(exception.getMessage().contains(VS_VERSION));
    verify(vseRepo, times(1)).findByUrlAndVersionOrNull(VS_URL, VS_VERSION);
  }

  @Test
  void returnsValueSetWhenVersionIsNull() {
    MadieValueSet vsNoVersion = MadieValueSet.builder().url(VS_URL).version(null).build();
    when(vseRepo.findByUrlAndVersionOrNull(VS_URL, null)).thenReturn(Optional.of(vsNoVersion));

    MadieValueSet result = valueSetExpansionService.getValueSet(VS_URL, null);

    assertNotNull(result);
    assertNull(result.getVersion());
    assertThat(result.getUrl(), is(equalTo(VS_URL)));
    verify(vseRepo, times(1)).findByUrlAndVersionOrNull(VS_URL, null);
  }

  @Test
  void getCodeSystemReturnsCodeSystemsWhenFound() {
    String url = "http://example.com/CodeSystem";
    CodeSystem cs1 = mock(CodeSystem.class);
    CodeSystem cs2 = mock(CodeSystem.class);
    List<CodeSystem> csList = List.of(cs1, cs2);

    when(csRepo.findAllByFullUrl(eq(url), any(Limit.class))).thenReturn(csList);

    List<CodeSystem> result = valueSetExpansionService.getCodeSystem(url, null);

    assertThat(result.size(), is(equalTo(2)));
    assertThat(result, is(equalTo(csList)));
    verify(csRepo, times(1)).findAllByFullUrl(url, Limit.unlimited());
  }

  @Test
  void getCodeSystemReturnsLimitedCodeSystemsWhenCountProvided() {
    String url = "http://example.com/CodeSystem";
    Integer count = 3;
    CodeSystem cs1 = mock(CodeSystem.class);
    CodeSystem cs2 = mock(CodeSystem.class);
    List<CodeSystem> csList = List.of(cs1, cs2);

    when(csRepo.findAllByFullUrl(url, Limit.of(count))).thenReturn(csList);

    List<CodeSystem> result = valueSetExpansionService.getCodeSystem(url, count);

    assertThat(result.size(), is(equalTo(2)));
    verify(csRepo, times(1)).findAllByFullUrl(url, Limit.of(count));
  }

  @Test
  void getCodeSystemThrowsCodeSystemNotFoundExceptionWithCorrectMessage() {
    String url = "http://example.com/CodeSystem/missing";

    when(csRepo.findAllByFullUrl(eq(url), any(Limit.class))).thenReturn(Collections.emptyList());

    CodeSystemNotFoundException exception =
        assertThrows(
            CodeSystemNotFoundException.class,
            () -> valueSetExpansionService.getCodeSystem(url, null));

    assertTrue(exception.getMessage().contains(url));

    verify(csRepo, times(1)).findAllByFullUrl(url, Limit.unlimited());
  }

  @Test
  void getValueSetsMapsMadieValueSetToDisplayDto() {
    Instant lastUpdated = Instant.now();

    MadieValueSet valueSet =
        MadieValueSet.builder()
            .id("vs-id")
            .url(VS_URL)
            .lastUpdated(lastUpdated)
            .manuallyModified(true)
            .build();

    Page<MadieValueSet> page = new PageImpl<>(List.of(valueSet));
    PageRequest pageable = PageRequest.of(0, 10);

    when(vseRepo.findAll(pageable)).thenReturn(page);

    Page<ValueSetDisplayForAdmin> result = valueSetExpansionService.getValueSets(pageable, any());

    assertEquals(1, result.getTotalElements());

    ValueSetDisplayForAdmin dto = result.getContent().get(0);

    assertEquals("vs-id", dto.getId());
    assertEquals(VS_URL, dto.getUrl());
    assertEquals(lastUpdated, dto.getLastUpdated());
    assertTrue(dto.isManuallyModified());

    verify(vseRepo, times(1)).findAll(pageable);
  }

  @Test
  void getValueSetsReturnsEmptyPageWhenRepositoryReturnsEmptyPage() {
    PageRequest pageable = PageRequest.of(0, 10);
    Page<MadieValueSet> emptyPage = new PageImpl<>(Collections.emptyList());

    when(vseRepo.findAll(pageable)).thenReturn(emptyPage);

    Page<ValueSetDisplayForAdmin> result = valueSetExpansionService.getValueSets(pageable, any());

    assertNotNull(result);
    assertTrue(result.getContent().isEmpty());
    assertEquals(0, result.getTotalElements());

    verify(vseRepo, times(1)).findAll(pageable);
  }

  @Test
  void getValueSetsMapsMultipleValueSetsToDisplayDtos() {
    Instant now = Instant.now();

    MadieValueSet vs1 =
        MadieValueSet.builder()
            .id("id-1")
            .url(VS_URL)
            .lastUpdated(now)
            .manuallyModified(true)
            .build();

    MadieValueSet vs2 =
        MadieValueSet.builder()
            .id("id-2")
            .url("http://example.com/valueset")
            .lastUpdated(now.minusSeconds(60))
            .manuallyModified(false)
            .build();

    PageRequest pageable = PageRequest.of(0, 10);

    when(vseRepo.findAll(pageable)).thenReturn(new PageImpl<>(List.of(vs1, vs2)));

    Page<ValueSetDisplayForAdmin> result = valueSetExpansionService.getValueSets(pageable, any());

    assertEquals(2, result.getTotalElements());

    ValueSetDisplayForAdmin first = result.getContent().get(0);
    ValueSetDisplayForAdmin second = result.getContent().get(1);

    assertEquals("id-1", first.getId());
    assertEquals(VS_URL, first.getUrl());
    assertTrue(first.isManuallyModified());

    assertEquals("id-2", second.getId());
    assertEquals("http://example.com/valueset", second.getUrl());
    assertFalse(second.isManuallyModified());

    verify(vseRepo, times(1)).findAll(pageable);
  }

  @Test
  void getValueSetsUsesUrlSearchWhenSearchTermProvided() {
    Instant lastUpdated = Instant.now();

    MadieValueSet valueSet =
        MadieValueSet.builder()
            .id("vs-id")
            .url(VS_URL)
            .lastUpdated(lastUpdated)
            .manuallyModified(true)
            .build();

    PageRequest pageable = PageRequest.of(0, 10);
    Page<MadieValueSet> page = new PageImpl<>(List.of(valueSet));

    when(vseRepo.findByUrlContainingIgnoreCase("heart", pageable)).thenReturn(page);

    Page<ValueSetDisplayForAdmin> result = valueSetExpansionService.getValueSets(pageable, "heart");

    assertEquals(1, result.getTotalElements());
    assertEquals(VS_URL, result.getContent().get(0).getUrl());

    verify(vseRepo, times(1)).findByUrlContainingIgnoreCase("heart", pageable);

    verify(vseRepo, never()).findAll(any(Pageable.class));
  }
}
