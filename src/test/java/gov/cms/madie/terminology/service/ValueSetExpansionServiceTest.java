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
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.MadieValueSet;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.repositories.ValueSetExpansionRepository;
import gov.cms.madie.terminology.util.ImplementationGuideManager;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import gov.cms.madie.terminology.webclient.TxTerminologyServiceWebClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValueSetExpansionServiceTest {
  private static final String VS_URL = "http://hl7.org/fhir/ValueSet/test-vs";
  private static final String VS_VERSION = "20230401";
  private static final String IG_NAME = "hl7.fhir.us.core";
  private static final String IG_VERSION = "6.1.0";
  private static final String VSAC_URL = "http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3";
  private static final String VSAC_SEARCH =
      "/ValueSet?url=http%3A%2F%2Fcts.nlm.nih.gov%2Ffhir%2FValueSet%2F1.2.3&_summary=count";
  private static final String SYSTEM_API_KEY = "test-system-key";
  private static final String VSAC_ABSENT =
      "{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"total\":0}";
  private static final String VSAC_PRESENT =
      "{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"total\":1}";

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
      "url": "http://hl7.org/fhir/ValueSet/test-vs",
      "version": "20230401",
      "status": "active",
      "expansion": {
        "contains": [
          { "system": "http://snomed.info/sct", "code": "123456789", "display": "Test Concept" }
        ]
      }
    }
    """;

  @BeforeEach
  void setUp() {
    madieValueSet = MadieValueSet.builder().url(VS_URL).version(VS_VERSION).build();
    realParser = FhirContext.forR4().newJsonParser();
    ReflectionTestUtils.setField(valueSetExpansionService, "systemVsacApiKey", SYSTEM_API_KEY);
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
  void updateIgValueSetDependenciesDoesNotFallBackToVsacWhenTxFhirReturnsNotFound() {
    when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
        .thenReturn(List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenThrow(
            new ResourceNotFoundException(
                "not found", HttpStatusCode.valueOf(404), "not found", "not found", ""));

    valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);

    verify(vseRepo, never()).save(any());
    verifyNoInteractions(fhirTerminologyServiceWebClient);
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
  void updateValueSetDependenciesReturnsNumberOfValueSetsSaved() {
    // given
    String manuallyModifiedUrl = "http://hl7.org/fhir/ValueSet/other-vs";
    MadieValueSet manuallyModifiedDependency =
        MadieValueSet.builder().url(manuallyModifiedUrl).version(VS_VERSION).build();
    MadieValueSet existingManuallyModifiedValueSet =
        MadieValueSet.builder()
            .url(manuallyModifiedUrl)
            .version(VS_VERSION)
            .valueSet("{}")
            .manuallyModified(true)
            .build();
    when(implementationGuideManager.getValueSetDependencies())
        .thenReturn(List.of(madieValueSet, manuallyModifiedDependency));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenReturn(MOCK_VALUE_SET_JSON);
    when(txTerminologyServiceWebClient.getValueSetExpansion(manuallyModifiedUrl, VS_VERSION))
        .thenReturn(MOCK_VALUE_SET_JSON);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.empty());
    when(vseRepo.findByUrlAndVersion(manuallyModifiedUrl, VS_VERSION))
        .thenReturn(Optional.of(existingManuallyModifiedValueSet));

    // when
    int savedValueSetCount = valueSetExpansionService.updateValueSetDependencies().join();

    // then
    assertEquals(1, savedValueSetCount);
    verify(vseRepo, times(1)).save(argThat(vs -> VS_URL.equals(vs.getUrl())));
    verify(vseRepo, never()).save(argThat(vs -> manuallyModifiedUrl.equals(vs.getUrl())));
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
            .url("http://hl7.org/fhir/ValueSet/other-vs")
            .version("20220101")
            .build();

    when(implementationGuideManager.getValueSetDependencies()).thenReturn(List.of(vs1, vs2));
    // vs1 expands successfully
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenReturn(MOCK_VALUE_SET_JSON);
    // vs2 throws
    when(txTerminologyServiceWebClient.getValueSetExpansion(
            "http://hl7.org/fhir/ValueSet/other-vs", "20220101"))
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

  /** Existing VSAC definitions are skipped even when a previous run cached an expansion. */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void skipsVsacValueSetAndContinues(boolean allIgs) {
    MadieValueSet vsac =
        MadieValueSet.builder()
            .url(VSAC_URL)
            .version(VS_VERSION)
            .valueSet("old VSAC content")
            .build();
    mockDependencies(allIgs, List.of(vsac, madieValueSet));
    when(fhirTerminologyServiceWebClient.fetchResourceFromVsac(
            VSAC_SEARCH + "&version=" + VS_VERSION, SYSTEM_API_KEY, "ValueSet"))
        .thenReturn(VSAC_PRESENT);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenReturn(MOCK_VALUE_SET_JSON);

    refreshDependencies(allIgs);

    verify(txTerminologyServiceWebClient, never()).getValueSetExpansion(VSAC_URL, VS_VERSION);
    verify(vseRepo).save(argThat(vs -> VS_URL.equals(vs.getUrl())));
    verify(vseRepo, never()).findByUrlAndVersion(VSAC_URL, VS_VERSION);
    verify(vseRepo, never()).deleteById(anyString());
    verify(fhirTerminologyServiceWebClient)
        .fetchResourceFromVsac(VSAC_SEARCH + "&version=" + VS_VERSION, SYSTEM_API_KEY, "ValueSet");
    verifyNoMoreInteractions(fhirTerminologyServiceWebClient);
  }

  /** Confirmed VSAC absence permits only the TX.FHIR response to be persisted. */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void retrievesFromTxFhirOnlyAfterVsacAbsence(boolean allIgs) {
    MadieValueSet vsac = MadieValueSet.builder().url(VSAC_URL).version(VS_VERSION).build();
    mockDependencies(allIgs, List.of(vsac));
    when(fhirTerminologyServiceWebClient.fetchResourceFromVsac(
            VSAC_SEARCH + "&version=" + VS_VERSION, SYSTEM_API_KEY, "ValueSet"))
        .thenReturn(VSAC_ABSENT);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    String txJson = MOCK_VALUE_SET_JSON.replace(VS_URL, VSAC_URL);
    when(txTerminologyServiceWebClient.getValueSetExpansion(VSAC_URL, VS_VERSION))
        .thenReturn(txJson);

    refreshDependencies(allIgs);

    var order = inOrder(fhirTerminologyServiceWebClient, txTerminologyServiceWebClient, vseRepo);
    order
        .verify(fhirTerminologyServiceWebClient)
        .fetchResourceFromVsac(VSAC_SEARCH + "&version=" + VS_VERSION, SYSTEM_API_KEY, "ValueSet");
    order.verify(txTerminologyServiceWebClient).getValueSetExpansion(VSAC_URL, VS_VERSION);
    ArgumentCaptor<MadieValueSet> captor = ArgumentCaptor.forClass(MadieValueSet.class);
    verify(vseRepo).save(captor.capture());
    assertEquals(
        "test-vs",
        realParser
            .parseResource(ValueSet.class, captor.getValue().getValueSet())
            .getIdElement()
            .getIdPart());
    assertNotNull(captor.getValue().getLastUpdated());
    verifyNoMoreInteractions(fhirTerminologyServiceWebClient);
  }

  /** Missing TX.FHIR data must not cause cached content to be saved. */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void skipsWhenAbsentFromBothSources(boolean allIgs) {
    MadieValueSet vsac =
        MadieValueSet.builder().url(VSAC_URL).version(VS_VERSION).valueSet("stale content").build();
    mockDependencies(allIgs, List.of(vsac));
    when(fhirTerminologyServiceWebClient.fetchResourceFromVsac(
            VSAC_SEARCH + "&version=" + VS_VERSION, SYSTEM_API_KEY, "ValueSet"))
        .thenReturn(VSAC_ABSENT);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(txTerminologyServiceWebClient.getValueSetExpansion(VSAC_URL, VS_VERSION))
        .thenThrow(
            new ResourceNotFoundException(
                "not found", HttpStatusCode.valueOf(404), "not found", "", ""));

    refreshDependencies(allIgs);

    verifyNoInteractions(vseRepo);
    verify(fhirTerminologyServiceWebClient)
        .fetchResourceFromVsac(VSAC_SEARCH + "&version=" + VS_VERSION, SYSTEM_API_KEY, "ValueSet");
    verifyNoMoreInteractions(fhirTerminologyServiceWebClient);
  }

  /** Omitted versions check the canonical without adding a version query parameter. */
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void checksVsacWithoutVersion(String version) {
    MadieValueSet vsac = MadieValueSet.builder().url(VSAC_URL).version(version).build();
    mockDependencies(true, List.of(vsac));
    when(fhirTerminologyServiceWebClient.fetchResourceFromVsac(
            VSAC_SEARCH, SYSTEM_API_KEY, "ValueSet"))
        .thenReturn(VSAC_PRESENT);
    when(fhirContext.newJsonParser()).thenReturn(realParser);

    refreshDependencies(true);

    verifyNoInteractions(txTerminologyServiceWebClient, vseRepo);
    verify(fhirTerminologyServiceWebClient)
        .fetchResourceFromVsac(VSAC_SEARCH, SYSTEM_API_KEY, "ValueSet");
  }

  /** Unusable VSAC responses are not evidence of absence. */
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        " ",
        "invalid JSON",
        "{\"resourceType\":\"Patient\"}",
        "{\"resourceType\":\"Bundle\",\"type\":\"searchset\"}",
        "{\"resourceType\":\"Bundle\",\"type\":\"batch-response\",\"total\":0}"
      })
  void skipsInvalidVsacResponsesAndContinues(String response) {
    MadieValueSet vsac = MadieValueSet.builder().url(VSAC_URL).version(VS_VERSION).build();
    mockDependencies(true, List.of(vsac, madieValueSet));
    when(fhirTerminologyServiceWebClient.fetchResourceFromVsac(
            VSAC_SEARCH + "&version=" + VS_VERSION, SYSTEM_API_KEY, "ValueSet"))
        .thenReturn(response);
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenReturn(MOCK_VALUE_SET_JSON);

    refreshDependencies(true);

    verify(txTerminologyServiceWebClient, never()).getValueSetExpansion(VSAC_URL, VS_VERSION);
    verify(vseRepo).save(argThat(vs -> VS_URL.equals(vs.getUrl())));
  }

  /** VSAC HTTP failures skip only the affected value set, including an endpoint-level 404. */
  @ParameterizedTest
  @ValueSource(ints = {401, 403, 404, 500})
  void skipsVsacHttpErrorsAndContinues(int status) {
    MadieValueSet vsac = MadieValueSet.builder().url(VSAC_URL).version(VS_VERSION).build();
    mockDependencies(true, List.of(vsac, madieValueSet));
    when(fhirTerminologyServiceWebClient.fetchResourceFromVsac(
            VSAC_SEARCH + "&version=" + VS_VERSION, SYSTEM_API_KEY, "ValueSet"))
        .thenThrow(
            WebClientResponseException.create(status, "VSAC unavailable", null, new byte[0], null));
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenReturn(MOCK_VALUE_SET_JSON);

    refreshDependencies(true);

    verify(txTerminologyServiceWebClient, never()).getValueSetExpansion(VSAC_URL, VS_VERSION);
    verify(vseRepo).save(argThat(vs -> VS_URL.equals(vs.getUrl())));
  }

  /** Transport failures must not permit unverified VSAC data through TX.FHIR. */
  @Test
  void skipsVsacNetworkErrorsAndContinues() {
    MadieValueSet vsac = MadieValueSet.builder().url(VSAC_URL).version(VS_VERSION).build();
    mockDependencies(true, List.of(vsac, madieValueSet));
    when(fhirTerminologyServiceWebClient.fetchResourceFromVsac(
            VSAC_SEARCH + "&version=" + VS_VERSION, SYSTEM_API_KEY, "ValueSet"))
        .thenThrow(
            new WebClientRequestException(
                new IOException("connection failed"),
                HttpMethod.GET,
                URI.create("https://cts.nlm.nih.gov/fhir/ValueSet"),
                new HttpHeaders()));
    when(fhirContext.newJsonParser()).thenReturn(realParser);
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenReturn(MOCK_VALUE_SET_JSON);

    refreshDependencies(true);

    verify(txTerminologyServiceWebClient, never()).getValueSetExpansion(VSAC_URL, VS_VERSION);
    verify(vseRepo).save(argThat(vs -> VS_URL.equals(vs.getUrl())));
  }

  /** Only the exact HTTP VSAC prefix triggers an existence check. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://cts.nlm.nih.gov/fhir/ValueSet/1.2.3",
        "http://cts.nlm.nih.gov.example/fhir/ValueSet/1.2.3",
        "http://example.org/fhir/ValueSet/1.2.3"
      })
  void otherUrlsNeverFallBackToVsac(String url) {
    mockDependencies(true, List.of(MadieValueSet.builder().url(url).build()));
    when(txTerminologyServiceWebClient.getValueSetExpansion(url, null)).thenReturn(null);

    refreshDependencies(true);

    verify(txTerminologyServiceWebClient).getValueSetExpansion(url, null);
    verifyNoInteractions(fhirTerminologyServiceWebClient, vseRepo);
  }

  /** Failed refreshes cannot reuse a prior expansion from a shared IG dependency. */
  @Test
  void failedTxRefreshDoesNotSaveCachedPayload() {
    madieValueSet.setValueSet("cached payload");
    mockDependencies(true, List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenThrow(
            new ValueSetExpansionException(
                "failed", HttpStatusCode.valueOf(422), "failed", "", "", ""));

    refreshDependencies(true);

    verifyNoInteractions(vseRepo, fhirTerminologyServiceWebClient);
  }

  /** Empty and invalid TX.FHIR responses cannot reuse content from a prior refresh. */
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "not JSON", "{\"resourceType\":\"Patient\"}"})
  void unusableTxResponseDoesNotSaveCachedPayload(String response) {
    madieValueSet.setValueSet("cached payload");
    mockDependencies(true, List.of(madieValueSet));
    when(txTerminologyServiceWebClient.getValueSetExpansion(VS_URL, VS_VERSION))
        .thenReturn(response);
    if (StringUtils.isNotBlank(response)) {
      when(fhirContext.newJsonParser()).thenReturn(realParser);
    }

    refreshDependencies(true);

    verifyNoInteractions(vseRepo, fhirTerminologyServiceWebClient);
  }

  /** Versions remain a single exact search value rather than injecting query parameters. */
  @Test
  void encodesVsacVersionQueryParameter() {
    String version = "v1+draft&release=2";
    mockDependencies(true, List.of(MadieValueSet.builder().url(VSAC_URL).version(version).build()));
    String search = VSAC_SEARCH + "&version=v1%2Bdraft%26release%3D2";
    when(fhirTerminologyServiceWebClient.fetchResourceFromVsac(search, SYSTEM_API_KEY, "ValueSet"))
        .thenReturn(VSAC_PRESENT);
    when(fhirContext.newJsonParser()).thenReturn(realParser);

    refreshDependencies(true);

    verify(fhirTerminologyServiceWebClient)
        .fetchResourceFromVsac(search, SYSTEM_API_KEY, "ValueSet");
    verifyNoInteractions(txTerminologyServiceWebClient, vseRepo);
  }

  /**
   * The existing HTTP client checks only the VSAC count using the configured base and system key.
   */
  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void checksVsacCountOverHttpBeforeTxIngestion(int total) throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.start();
      server.enqueue(
          new MockResponse()
              .setHeader("Content-Type", "application/fhir+json")
              .setBody(total == 0 ? VSAC_ABSENT : VSAC_PRESENT));
      FhirContext context = FhirContext.forR4();
      FhirTerminologyServiceWebClient client =
          new FhirTerminologyServiceWebClient(
              server.url("/fhir").toString(),
              "",
              "",
              "",
              "",
              server.url("/fhir/ValueSet").toString(),
              context);
      ValueSetExpansionService service =
          new ValueSetExpansionService(
              implementationGuideManager,
              vseRepo,
              csRepo,
              txTerminologyServiceWebClient,
              context,
              client);
      ReflectionTestUtils.setField(service, "systemVsacApiKey", SYSTEM_API_KEY);
      mockDependencies(
          true, List.of(MadieValueSet.builder().url(VSAC_URL).version(VS_VERSION).build()));
      if (total == 0) {
        when(txTerminologyServiceWebClient.getValueSetExpansion(VSAC_URL, VS_VERSION))
            .thenReturn(MOCK_VALUE_SET_JSON.replace(VS_URL, VSAC_URL));
      }

      assertEquals(total == 0 ? 1 : 0, service.updateValueSetDependencies().join());

      RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
      assertNotNull(request);
      assertEquals("GET", request.getMethod());
      assertEquals("/fhir" + VSAC_SEARCH + "&version=" + VS_VERSION, request.getPath());
      assertEquals(
          "Basic "
              + Base64.getEncoder()
                  .encodeToString(("apikey:" + SYSTEM_API_KEY).getBytes(StandardCharsets.UTF_8)),
          request.getHeader("Authorization"));
      assertEquals(1, server.getRequestCount());
      if (total == 0) {
        verify(vseRepo).save(argThat(vs -> vs.getValueSet().contains("test-vs")));
      } else {
        verifyNoInteractions(txTerminologyServiceWebClient, vseRepo);
      }
    }
  }

  /** Supplies the same dependency list to either public refresh entry point. */
  private void mockDependencies(boolean allIgs, List<MadieValueSet> dependencies) {
    if (allIgs) {
      when(implementationGuideManager.getValueSetDependencies()).thenReturn(dependencies);
    } else {
      when(implementationGuideManager.getValueSetDependencies(IG_NAME, IG_VERSION))
          .thenReturn(dependencies);
    }
  }

  /** Exercises either all-IG or single-IG refresh through its public API. */
  private void refreshDependencies(boolean allIgs) {
    if (allIgs) {
      valueSetExpansionService.updateValueSetDependencies().join();
    } else {
      valueSetExpansionService.updateIgValueSetDependencies(IG_NAME, IG_VERSION);
    }
  }

  // ---------------------------------------------------------------------------
  // upsertValueSet()
  // ---------------------------------------------------------------------------

  @Test
  void upsertValueSetThrowsWhenNoExistingUrlVersionMatch() {
    MadieValueSet incoming =
        MadieValueSet.builder()
            .url(VS_URL)
            .version(VS_VERSION)
            .valueSet(MOCK_VALUE_SET_JSON)
            .build();

    when(vseRepo.findByUrlAndVersion(VS_URL, VS_VERSION)).thenReturn(Optional.empty());
    when(fhirContext.newJsonParser()).thenReturn(realParser);

    ValueSetNotFoundException ex =
        assertThrows(
            ValueSetNotFoundException.class,
            () -> valueSetExpansionService.upsertValueSet(incoming));

    assertTrue(ex.getMessage().contains(VS_URL));
    assertTrue(ex.getMessage().contains(VS_VERSION));
    verify(vseRepo, never()).save(any(MadieValueSet.class));
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
  void addValueSetThrowsWhenJsonIsSyntacticallyInvalid() {
    ValueSetDisplayForAdmin request = addRequest(VS_URL, VS_VERSION, "{ not valid json");
    when(fhirContext.newJsonParser()).thenReturn(realParser);

    InvalidValueSetException ex =
        assertThrows(
            InvalidValueSetException.class, () -> valueSetExpansionService.addValueSet(request));
    assertTrue(ex.getMessage().contains("could not be read as valid JSON"));
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
    assertTrue(ex.getMessage().contains("not a valid FHIR ValueSet"));
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

    MadieValueSet deleted = valueSetExpansionService.deleteValueSet("test-id");

    assertSame(existing, deleted);
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
