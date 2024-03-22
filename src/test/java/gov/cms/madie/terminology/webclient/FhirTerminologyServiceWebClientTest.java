package gov.cms.madie.terminology.webclient;

import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FhirTerminologyServiceWebClientTest {

  private static final String MOCK_RESPONSE_STRING = "test-response";

  private static final String MOCK_MANIFEST_URN = "/manifestUrn";
  private static final String MOCK_API_KEY = "test-api-key";
  private static final String DEFAULT_PROFILE = "Most Recent Code System Versions in VSAC";
  public static MockWebServer mockBackEnd;

  private ValueSetsSearchCriteria.ValueSetParams testValueSetParams;

  @InjectMocks FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;

  @BeforeAll
  static void setUp() throws IOException {
    mockBackEnd = new MockWebServer();
    mockBackEnd.start();
  }

  @BeforeEach
  void initialize() {
    testValueSetParams = ValueSetsSearchCriteria.ValueSetParams.builder().oid("test-vs-id").build();
    String baseUrl = String.format("http://localhost:%s", mockBackEnd.getPort());
    fhirTerminologyServiceWebClient =
        new FhirTerminologyServiceWebClient(baseUrl, MOCK_MANIFEST_URN, DEFAULT_PROFILE);
  }

  @AfterAll
  static void tearDown() throws IOException {
    mockBackEnd.shutdown();
  }

  @Test
  void getManifestBundleSuccessfully() throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(MOCK_RESPONSE_STRING)
            .addHeader("Content-Type", "application/fhir+json"));
    String actualResponse = fhirTerminologyServiceWebClient.getManifestBundle(MOCK_API_KEY);
    assertNotNull(actualResponse);
    assertEquals(MOCK_RESPONSE_STRING, actualResponse);
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    assertEquals("/manifestUrn", recordedRequest.getPath());
  }

  @Test
  void getManifestBundle_ReturnsException() throws InterruptedException {
    mockBackEnd.enqueue(new MockResponse().setResponseCode(HttpStatus.UNAUTHORIZED.value()));
    assertThrows(
        WebClientResponseException.class,
        () -> fhirTerminologyServiceWebClient.getManifestBundle(MOCK_API_KEY));
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    assertEquals("/manifestUrn", recordedRequest.getPath());
  }

  @Test
  void getLatestValueSetResourceSuccessfully_when_noCustomSearchCriteriaIsProvided()
      throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(MOCK_RESPONSE_STRING)
            .addHeader("Content-Type", "application/fhir+json"));
    String actualResponse =
        fhirTerminologyServiceWebClient.getValueSetResource(
            MOCK_API_KEY, testValueSetParams, null, null, new ManifestExpansion());
    assertNotNull(actualResponse);
    assertEquals(MOCK_RESPONSE_STRING, actualResponse);
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    assertEquals("/ValueSet/test-vs-id/$expand", recordedRequest.getPath());
  }

  @Test
  void getValueSetResourceSuccessfully_when_manifestExpansionIsProvided()
      throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(MOCK_RESPONSE_STRING)
            .addHeader("Content-Type", "application/fhir+json"));
    String actualResponse =
        fhirTerminologyServiceWebClient.getValueSetResource(
            MOCK_API_KEY,
            testValueSetParams,
            null,
            null,
            ManifestExpansion.builder()
                .id("test-manifest-456")
                .fullUrl("https://cts.nlm.nih.gov/fhir/Library/test-manifest-456")
                .build());
    assertNotNull(actualResponse);
    assertEquals(MOCK_RESPONSE_STRING, actualResponse);
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    assertEquals(
        "/ValueSet/test-vs-id/$expand?manifest=https://cts.nlm.nih.gov/fhir/Library/test-manifest-456",
        recordedRequest.getPath());
  }

  @Test
  void getValueSetResourceSuccessfully_when_ValueSetVersionIsProvided()
      throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(MOCK_RESPONSE_STRING)
            .addHeader("Content-Type", "application/fhir+json"));
    testValueSetParams.setVersion("test-value-set-version-2024");
    String actualResponse =
        fhirTerminologyServiceWebClient.getValueSetResource(
            MOCK_API_KEY, testValueSetParams, null, null, new ManifestExpansion());
    assertNotNull(actualResponse);
    assertEquals(MOCK_RESPONSE_STRING, actualResponse);
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    assertEquals(
        "/ValueSet/test-vs-id/$expand?valueSetVersion=test-value-set-version-2024",
        recordedRequest.getPath());
  }

  @Test
  void getValueSetResource_ReturnsException() throws InterruptedException {
    testValueSetParams.setVersion("");
    mockBackEnd.enqueue(new MockResponse().setResponseCode(HttpStatus.UNAUTHORIZED.value()));
    assertThrows(
        WebClientResponseException.class,
        () ->
            fhirTerminologyServiceWebClient.getValueSetResource(
                MOCK_API_KEY, testValueSetParams, null, null, new ManifestExpansion()));
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    assertEquals("/ValueSet/test-vs-id/$expand", recordedRequest.getPath());
  }
}
