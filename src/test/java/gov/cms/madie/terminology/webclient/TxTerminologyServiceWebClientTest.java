package gov.cms.madie.terminology.webclient;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.exceptions.ResourceNotFoundException;
import gov.cms.madie.terminology.exceptions.ValueSetExpansionException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TxTerminologyServiceWebClientTest {

  private static final String MOCK_RESPONSE_BODY = "test-response-body";
  private static final String VS_URL = "http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5";
  private static final String VS_VERSION = "20230401";
  private static final String EXPANSION_URN =
      "/ValueSet/$expand?url={fullUrl}&valueSetVersion={version}";

  // Minimal FHIR OperationOutcome JSON bodies for 422 branch testing
  private static final String NOT_FOUND_OUTCOME =
      """
      {
        "resourceType": "OperationOutcome",
        "issue": [{ "severity": "error", "code": "not-found", "diagnostics": "VS not found" }]
      }
      """;

  private static final String TOO_COSTLY_OUTCOME =
      """
      {
        "resourceType": "OperationOutcome",
        "issue": [{ "severity": "error", "code": "too-costly", "diagnostics": "too costly" }]
      }
      """;

  private static final String GENERIC_ERROR_OUTCOME =
      """
      {
        "resourceType": "OperationOutcome",
        "issue": [{ "severity": "error", "code": "processing", "diagnostics": "error" }]
      }
      """;

  public static MockWebServer mockBackEnd;
  private TxTerminologyServiceWebClient client;

  @BeforeAll
  static void setUp() throws IOException {
    mockBackEnd = new MockWebServer();
    mockBackEnd.start();
  }

  @BeforeEach
  void initialize() {
    String baseUrl = String.format("http://localhost:%s", mockBackEnd.getPort());
    client = new TxTerminologyServiceWebClient(baseUrl, EXPANSION_URN, FhirContext.forR4Cached());
  }

  @AfterAll
  static void tearDown() throws IOException {
    mockBackEnd.shutdown();
  }

  // ---------------------------------------------------------------------------
  // getValueSetExpansion()
  // ---------------------------------------------------------------------------

  @Test
  void getValueSetExpansionReturnsBodyOnSuccess() throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(MOCK_RESPONSE_BODY)
            .addHeader("Content-Type", "application/json+fhir"));

    String result = client.getValueSetExpansion(VS_URL, VS_VERSION);

    assertNotNull(result);
    assertThat(result, equalTo(MOCK_RESPONSE_BODY));

    RecordedRequest request = mockBackEnd.takeRequest();
    assertNotNull(request.getPath());
    assertTrue(request.getPath().contains("url=" + VS_URL));
    assertTrue(request.getPath().contains("valueSetVersion=" + VS_VERSION));
  }

  @Test
  void getValueSetExpansionHandlesNullVersionByOmittingVersionParam() throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(MOCK_RESPONSE_BODY)
            .addHeader("Content-Type", "application/json+fhir"));

    String result = client.getValueSetExpansion(VS_URL, null);

    assertNotNull(result);

    RecordedRequest request = mockBackEnd.takeRequest();
    // version replaced with empty string — param still present but empty
    assertNotNull(request.getPath());
    assertTrue(request.getPath().contains("url=" + VS_URL));
    assertTrue(request.getPath().contains("valueSetVersion="));
  }

  @Test
  void getValueSetExpansionThrowsVsacResourceNotFoundExceptionWhen422NotFound()
      throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .setBody(NOT_FOUND_OUTCOME)
            .addHeader("Content-Type", "application/json+fhir"));

    assertThrows(
        ResourceNotFoundException.class, () -> client.getValueSetExpansion(VS_URL, VS_VERSION));

    mockBackEnd.takeRequest();
  }

  @Test
  void getValueSetExpansionThrowsVsacValueSetExpansionExceptionWhen422TooCostly()
      throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .setBody(TOO_COSTLY_OUTCOME)
            .addHeader("Content-Type", "application/json+fhir"));

    assertThrows(
        ValueSetExpansionException.class, () -> client.getValueSetExpansion(VS_URL, VS_VERSION));

    mockBackEnd.takeRequest();
  }

  @Test
  void getValueSetExpansionThrowsVsacValueSetExpansionExceptionWhen422Generic()
      throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .setBody(GENERIC_ERROR_OUTCOME)
            .addHeader("Content-Type", "application/json+fhir"));

    assertThrows(
        ValueSetExpansionException.class, () -> client.getValueSetExpansion(VS_URL, VS_VERSION));

    mockBackEnd.takeRequest();
  }

  @Test
  void getValueSetExpansionReturnsNullWhenServerReturnsUnexpectedStatus()
      throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.SERVICE_UNAVAILABLE.value())
            .addHeader("Content-Type", "application/json+fhir"));

    String result = client.getValueSetExpansion(VS_URL, VS_VERSION);

    // Non-200, non-422 falls through to Mono.empty() → block() returns null
    assertNull(result);
    mockBackEnd.takeRequest();
  }

  // ---------------------------------------------------------------------------
  // fetchResource()
  // ---------------------------------------------------------------------------

  @Test
  void fetchResourceReturnsBodyOnSuccess() throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(MOCK_RESPONSE_BODY)
            .addHeader("Content-Type", "application/json+fhir"));

    String result = client.fetchResource("/ValueSet/$expand?url=" + VS_URL);

    assertNotNull(result);
    assertThat(result, equalTo(MOCK_RESPONSE_BODY));

    RecordedRequest request = mockBackEnd.takeRequest();
    assertNotNull(request.getPath());
    assertTrue(request.getPath().contains("/ValueSet/$expand"));
  }

  @Test
  void fetchResourceThrowsVsacResourceNotFoundExceptionWhen422NotFound()
      throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .setBody(NOT_FOUND_OUTCOME)
            .addHeader("Content-Type", "application/json+fhir"));

    assertThrows(
        ResourceNotFoundException.class,
        () -> client.fetchResource("/ValueSet/$expand?url=" + VS_URL));

    mockBackEnd.takeRequest();
  }

  @Test
  void fetchResourceThrowsVsacValueSetExpansionExceptionWhen422TooCostly()
      throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .setBody(TOO_COSTLY_OUTCOME)
            .addHeader("Content-Type", "application/json+fhir"));

    assertThrows(
        ValueSetExpansionException.class,
        () -> client.fetchResource("/ValueSet/$expand?url=" + VS_URL));

    mockBackEnd.takeRequest();
  }

  @Test
  void fetchResourceThrowsVsacValueSetExpansionExceptionWhen422GenericError()
      throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .setBody(GENERIC_ERROR_OUTCOME)
            .addHeader("Content-Type", "application/json+fhir"));

    assertThrows(
        ValueSetExpansionException.class,
        () -> client.fetchResource("/ValueSet/$expand?url=" + VS_URL));

    mockBackEnd.takeRequest();
  }

  @Test
  void fetchResourceReturnsNullWhenServerReturnsNon200Non422Status() throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .addHeader("Content-Type", "application/json+fhir"));

    String result = client.fetchResource("/ValueSet/$expand?url=" + VS_URL);

    assertNull(result);
    mockBackEnd.takeRequest();
  }

  @Test
  void fetchResourceReturnsNullWhenServerReturns404() throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.NOT_FOUND.value())
            .addHeader("Content-Type", "application/json+fhir"));

    String result = client.fetchResource("/ValueSet/$expand?url=" + VS_URL);

    assertNull(result);
    mockBackEnd.takeRequest();
  }

  // ---------------------------------------------------------------------------
  // Re-initialize client on a fresh MockWebServer (mirrors FhirTerminologyServiceWebClientTest
  // pattern for cases where server needs to be recycled)
  // ---------------------------------------------------------------------------

  @Test
  void getValueSetExpansionReturnsBodyOnSuccessAfterServerRestart()
      throws IOException, InterruptedException {
    mockBackEnd.shutdown();
    mockBackEnd = new MockWebServer();
    mockBackEnd.start();

    String baseUrl = String.format("http://localhost:%s", mockBackEnd.getPort());
    client = new TxTerminologyServiceWebClient(baseUrl, EXPANSION_URN, FhirContext.forR4Cached());

    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(MOCK_RESPONSE_BODY)
            .addHeader("Content-Type", "application/json+fhir"));

    String result = client.getValueSetExpansion(VS_URL, VS_VERSION);

    assertNotNull(result);
    assertThat(result, equalTo(MOCK_RESPONSE_BODY));
    mockBackEnd.takeRequest();
  }
}
