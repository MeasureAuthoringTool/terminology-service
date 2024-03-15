package gov.cms.madie.terminology.webclient;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  public static MockWebServer mockBackEnd;

  public FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;

  @BeforeAll
  static void setUp() throws IOException {
    mockBackEnd = new MockWebServer();
    mockBackEnd.start();
  }

  @BeforeEach
  void initialize() {
    String baseUrl = String.format("http://localhost:%s", mockBackEnd.getPort());
    fhirTerminologyServiceWebClient =
        new FhirTerminologyServiceWebClient(baseUrl, MOCK_MANIFEST_URN);
  }

  @AfterAll
  static void tearDown() throws IOException {
    mockBackEnd.shutdown();
  }

  @Test
  void getManifestBundleSuccessfully() {
    mockBackEnd.enqueue(
        new MockResponse()
                .setResponseCode(200)
            .setBody(MOCK_RESPONSE_STRING)
            .addHeader("Content-Type", "application/fhir+json"));
    String actualResponse = fhirTerminologyServiceWebClient.getManifestBundle(MOCK_API_KEY);
    assertNotNull(actualResponse);
    assertEquals(MOCK_RESPONSE_STRING, actualResponse);
  }

  @Test
  void getManifestBundle_ReturnsException() {
    mockBackEnd.enqueue(
        new MockResponse()
                .setResponseCode(HttpStatus.UNAUTHORIZED.value()));
    assertThrows(WebClientResponseException.class, () -> fhirTerminologyServiceWebClient.getManifestBundle(MOCK_API_KEY));
  }
}
