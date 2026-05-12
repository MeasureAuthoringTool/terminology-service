package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.terminology.exceptions.ResourceNotFoundException;
import gov.cms.madie.terminology.exceptions.ValueSetExpansionException;
import gov.cms.madie.terminology.exceptions.ValueSetNotFoundException;
import gov.cms.madie.terminology.models.MadieValueSet;
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
import org.springframework.http.HttpStatusCode;

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

  @Mock private ImplementationGuideManager implementationGuideManager;
  @Mock private ValueSetExpansionRepository vseRepo;
  @Mock private TxTerminologyServiceWebClient txTerminologyServiceWebClient;
  @Mock private FhirContext fhirContext;
  @Mock private FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;
  @Mock private FhirTerminologyService fhirTerminologyService;

  @InjectMocks private ValueSetExpansionService valueSetExpansionService;

  private static final String VS_URL = "http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3";
  private static final String VS_VERSION = "20230401";
  private static final String IG_NAME = "hl7.fhir.us.core";
  private static final String IG_VERSION = "6.1.0";

  // Minimal FHIR ValueSet JSON for parsing tests
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

  private MadieValueSet madieValueSet;
  private IParser realParser;

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

    MadieValueSet result = valueSetExpansionService.upsertValueSet(incoming);

    assertEquals("existing-id", result.getId());
    assertTrue(result.isManuallyModified());
    assertNotNull(result.getLastUpdated());
    verify(vseRepo, times(1)).save(incoming);
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
}
