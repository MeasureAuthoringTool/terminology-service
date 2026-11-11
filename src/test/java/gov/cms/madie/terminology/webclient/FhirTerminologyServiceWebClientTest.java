package gov.cms.madie.terminology.webclient;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.exceptions.ResourceNotFoundException;
import gov.cms.madie.terminology.exceptions.VsacBatchValueSetExpansionException;
import gov.cms.madie.terminology.exceptions.ValueSetExpansionException;
import gov.cms.madie.terminology.models.CodeSystem;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FhirTerminologyServiceWebClientTest {

  private static final String MOCK_RESPONSE_STRING = "test-response";

  private static final String MOCK_MANIFEST_URN = "/manifestUrn";
  private static final String MOCK_CODE_SYSTEM_URN = "/codeSystemUrn";
  private static final String MOCK_API_KEY = "test-api-key";
  private static final String MOCK_CODE_LOOKUP = "/CodeSystem/$lookup";
  private static final String DEFAULT_PROFILE = "Most Recent Code System Versions in VSAC";
  public static MockWebServer mockBackEnd;

  @Mock FhirContext fhirContext;

  private ValueSetsSearchCriteria.ValueSetParams testValueSetParams;

  private ValueSetsSearchCriteria searchCriteria;

  FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;

  @BeforeAll
  static void setUp() throws IOException {
    mockBackEnd = new MockWebServer();
    mockBackEnd.start();
  }

  @BeforeEach
  void initialize() {
    searchCriteria =
        ValueSetsSearchCriteria.builder()
            .profile(null)
            .includeDraft(null)
            .activeOnly("false")
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .url("http://cts.nlm.nih.gov/fhir/ValueSet/test-vs-id")
                        .build()))
            .build();
    testValueSetParams =
        ValueSetsSearchCriteria.ValueSetParams.builder()
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/test-vs-id")
            .build();
    String baseUrl = String.format("http://localhost:%s", mockBackEnd.getPort());
    String searchValueSetEndpoint =
        String.format("http://localhost:%s/fhir/ValueSet", mockBackEnd.getPort());
    fhirTerminologyServiceWebClient =
        new FhirTerminologyServiceWebClient(
            baseUrl,
            MOCK_MANIFEST_URN,
            MOCK_CODE_SYSTEM_URN,
            MOCK_CODE_LOOKUP,
            DEFAULT_PROFILE,
            searchValueSetEndpoint,
            fhirContext);
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
  void getManifestBundleReturnsException() throws IOException, InterruptedException {
    mockBackEnd.shutdown();
    mockBackEnd = new MockWebServer();
    mockBackEnd.start();
    String baseUrl = String.format("http://localhost:%s", mockBackEnd.getPort());
    String searchValueSetEndpoint =
        String.format("http://localhost:%s/fhir/ValueSet", mockBackEnd.getPort());
    fhirTerminologyServiceWebClient =
        new FhirTerminologyServiceWebClient(
            baseUrl,
            MOCK_MANIFEST_URN,
            MOCK_CODE_SYSTEM_URN,
            MOCK_CODE_LOOKUP,
            DEFAULT_PROFILE,
            searchValueSetEndpoint,
            fhirContext);
    mockBackEnd.enqueue(new MockResponse().setResponseCode(HttpStatus.UNAUTHORIZED.value()));
    assertThrows(
        ValueSetExpansionException.class,
        () -> fhirTerminologyServiceWebClient.getManifestBundle(MOCK_API_KEY));
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    assertEquals("/manifestUrn", recordedRequest.getPath());
  }

  @Test
  void getLatestValueSetResourceSuccessfullWhenNoCustomSearchCriteriaIsProvided()
      throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(MOCK_RESPONSE_STRING)
            .addHeader("Content-Type", "application/fhir+json"));
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    String actualResponse =
        fhirTerminologyServiceWebClient.getValueSetResourcesWithExpansion(
            MOCK_API_KEY, searchCriteria);
    assertNotNull(actualResponse);
    assertEquals(MOCK_RESPONSE_STRING, actualResponse);
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    assertThat(
        recordedRequest.getBody().readUtf8(),
        containsString(
            "/ValueSet/$expand?url=http://cts.nlm.nih.gov/fhir/ValueSet/test-vs-id&activeOnly=false"));
  }

  @Test
  void getDraftValueSetResourceSuccessfullyWhenNoCustomSearchCriteriaIsProvided()
      throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(MOCK_RESPONSE_STRING)
            .addHeader("Content-Type", "application/fhir+json"));
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    searchCriteria.setIncludeDraft("true");
    String actualResponse =
        fhirTerminologyServiceWebClient.getValueSetResourcesWithExpansion(
            MOCK_API_KEY, searchCriteria);
    assertNotNull(actualResponse);
    assertEquals(MOCK_RESPONSE_STRING, actualResponse);
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    assertThat(
        recordedRequest.getBody().readUtf8(),
        containsString(
            "/ValueSet/$expand?url=http://cts.nlm.nih.gov/fhir/ValueSet/test-vs-id&includeDraft=true&activeOnly=false"));
  }

  @Test
  void getValueSetResourceSuccessfullyWhenManifestExpansionIsProvided()
      throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(MOCK_RESPONSE_STRING)
            .addHeader("Content-Type", "application/fhir+json"));
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    searchCriteria.setManifestExpansion(
        ManifestExpansion.builder()
            .id("test-manifest-456")
            .fullUrl("https://cts.nlm.nih.gov/fhir/Library/test-manifest-456")
            .build());
    String actualResponse =
        fhirTerminologyServiceWebClient.getValueSetResourcesWithExpansion(
            MOCK_API_KEY, searchCriteria);
    assertNotNull(actualResponse);
    assertEquals(MOCK_RESPONSE_STRING, actualResponse);
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    assertThat(
        recordedRequest.getBody().readUtf8(),
        containsString(
            "/ValueSet/$expand?url=http://cts.nlm.nih.gov/fhir/ValueSet/test-vs-id&activeOnly=false"
                + "&manifest=https://cts.nlm.nih.gov/fhir/Library/test-manifest-456"));
  }

  @Test
  void getValueSetResourceReturnsException() throws InterruptedException {
    mockBackEnd.enqueue(new MockResponse().setResponseCode(HttpStatus.UNAUTHORIZED.value()));
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    assertThrows(
        VsacBatchValueSetExpansionException.class,
        () ->
            fhirTerminologyServiceWebClient.getValueSetResourcesWithExpansion(
                MOCK_API_KEY, searchCriteria));
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    assertThat(
        recordedRequest.getBody().readUtf8(),
        containsString(
            "/ValueSet/$expand?url=http://cts.nlm.nih.gov/fhir/ValueSet/test-vs-id&activeOnly=false"));
  }

  @Test
  void getCodeSystemsPageSuccessfully() throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(MOCK_RESPONSE_STRING)
            .addHeader("Content-Type", "application/fhir+json"));
    String actualResponse = fhirTerminologyServiceWebClient.getCodeSystemsPage(0, 50, MOCK_API_KEY);
    assertNotNull(actualResponse);
    assertEquals(MOCK_RESPONSE_STRING, actualResponse);
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    assertEquals("/codeSystemUrn?_offset=0&_count=50", recordedRequest.getPath());
  }

  @Test
  void getCodeSystemsPageReturnsException() throws IOException, InterruptedException {
    mockBackEnd.shutdown();
    mockBackEnd = new MockWebServer();
    mockBackEnd.start();
    String baseUrl = String.format("http://localhost:%s", mockBackEnd.getPort());
    String searchValueSetEndpoint =
        String.format("http://localhost:%s/fhir/ValueSet", mockBackEnd.getPort());
    fhirTerminologyServiceWebClient =
        new FhirTerminologyServiceWebClient(
            baseUrl,
            MOCK_MANIFEST_URN,
            MOCK_CODE_SYSTEM_URN,
            MOCK_CODE_LOOKUP,
            DEFAULT_PROFILE,
            searchValueSetEndpoint,
            fhirContext);
    mockBackEnd.enqueue(new MockResponse().setResponseCode(HttpStatus.UNAUTHORIZED.value()));
    assertThrows(
        ValueSetExpansionException.class,
        () -> fhirTerminologyServiceWebClient.getCodeSystemsPage(0, 50, MOCK_API_KEY));
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    assertEquals("/codeSystemUrn?_offset=0&_count=50", recordedRequest.getPath());
  }

  @Test
  void getCodeSystemHistoryPageUsesLogicalId() throws InterruptedException {
    // given
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(MOCK_RESPONSE_STRING)
            .addHeader("Content-Type", "application/fhir+json"));

    // when
    String actualResponse =
        fhirTerminologyServiceWebClient.getCodeSystemHistoryPage("logical-id", 0, 50, MOCK_API_KEY);

    // then
    assertEquals(MOCK_RESPONSE_STRING, actualResponse);
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    assertEquals(
        "/codeSystemUrn/logical-id/_history?_offset=0&_count=50", recordedRequest.getPath());
  }

  @Test
  void getCodeSystemHistoryPageReturnsException() throws InterruptedException {
    // given
    mockBackEnd.enqueue(new MockResponse().setResponseCode(HttpStatus.UNAUTHORIZED.value()));

    // when
    ValueSetExpansionException error =
        assertThrows(
            ValueSetExpansionException.class,
            () ->
                fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
                    "logical-id", 0, 50, MOCK_API_KEY));

    // then
    assertNotNull(error);
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    assertEquals(
        "/codeSystemUrn/logical-id/_history?_offset=0&_count=50", recordedRequest.getPath());
  }

  @Test
  void testGetCodeResource() throws InterruptedException {
    String codeName = "1963-8";
    CodeSystem codeSystem =
        CodeSystem.builder()
            .fullUrl("http://loinc.org")
            .name("LOINC")
            .version(CodeSystem.Version.builder().fhirVersion("2.40").build())
            .build();
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(MOCK_RESPONSE_STRING)
            .addHeader("Content-Type", "application/fhir+json"));

    String codeJson =
        fhirTerminologyServiceWebClient.getCodeResource(codeName, codeSystem, MOCK_API_KEY);
    assertNotNull(codeJson);
    assertEquals(MOCK_RESPONSE_STRING, codeJson);
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    assertEquals("/CodeSystem/$lookup", recordedRequest.getPath());
  }

  @Test
  void testGetUrisForValueSetSearchObject() {
    var criteria =
        searchCriteria.toBuilder()
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5.6")
                        .build(),
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5.6")
                        .build(),
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5.7")
                        .build()))
            .build();
    // duplicates should be removed and only 2 uris returned
    List<String> uriList = fhirTerminologyServiceWebClient.getValueSetExpansionUris(criteria);
    assertThat(uriList.size(), equalTo(2));
    assertThat(
        uriList.get(0),
        equalTo(
            "/ValueSet/$expand?url=http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5.6&activeOnly=false"));
    assertThat(
        uriList.get(1),
        equalTo(
            "/ValueSet/$expand?url=http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5.7&activeOnly=false"));
  }

  /* covers getValueSetExpansionUris() line 130:
   * ? defaultProfile
   */
  @Test
  void getValueSetExpansionUrisUsesDefaultProfileWhenProfileIsNotBlank() {
    ValueSetsSearchCriteria criteria =
        ValueSetsSearchCriteria.builder()
            .profile("defaultProfile")
            .includeDraft(null)
            .activeOnly("false")
            .manifestExpansion(new ManifestExpansion())
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .url("http://cts.nlm.nih.gov/fhir/ValueSet/test-vs-id")
                        .build()))
            .build();
    List<String> uris = fhirTerminologyServiceWebClient.getValueSetExpansionUris(criteria);
    assertFalse(uris.get(0).contains("defaultProfile"));
  }

  @Test
  void getValueSetResourcesWithoutExpansionSendsDefinitionSearchUris() throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(MOCK_RESPONSE_STRING)
            .addHeader("Content-Type", "application/fhir+json"));
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    testValueSetParams.setOffset(0);
    testValueSetParams.setCount(1000);
    searchCriteria.setValueSetParams(List.of(testValueSetParams));
    searchCriteria.setIncludeDraft("true");
    searchCriteria.setManifestExpansion(
        ManifestExpansion.builder()
            .fullUrl("https://cts.nlm.nih.gov/fhir/Library/test-manifest-456")
            .build());

    String actualResponse =
        fhirTerminologyServiceWebClient.getValueSetResourcesWithoutExpansion(
            MOCK_API_KEY, searchCriteria);

    assertEquals(MOCK_RESPONSE_STRING, actualResponse);
    String requestBody = mockBackEnd.takeRequest().getBody().readUtf8();
    assertThat(
        requestBody,
        containsString(
            "\"url\":\"/ValueSet?status=active,draft"
                + "&url=http://cts.nlm.nih.gov/fhir/ValueSet/test-vs-id\""));
    assertFalse(requestBody.contains("$expand"), "ACTUAL BODY: " + requestBody);
  }

  @Test
  void getValueSetSearchUrisReturnsDistinctSearchUris() {
    var criteria =
        searchCriteria.toBuilder()
            .includeDraft("true")
            .manifestExpansion(ManifestExpansion.builder().fullUrl("test-manifest").build())
            .valueSetParams(
                List.of(
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5.6")
                        .build(),
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5.6")
                        .build(),
                    ValueSetsSearchCriteria.ValueSetParams.builder()
                        .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5.7")
                        .offset(0)
                        .count(1000)
                        .build(),
                    ValueSetsSearchCriteria.ValueSetParams.builder().oid("1.2.3.4.5.8").build()))
            .build();

    List<String> uris = fhirTerminologyServiceWebClient.getValueSetSearchUris(criteria);

    assertThat(
        uris,
        equalTo(
            List.of(
                "/ValueSet?status=active,draft&url=http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5.6",
                "/ValueSet?status=active,draft"
                    + "&url=http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5.7")));
  }

  /* branch coverage for searchValueSets(), line 95:
   * urlValue.startsWith("http://cts.nlm.nih.gov/fhir/ValueSet/")
   */
  @Test
  void searchValueSetsDoesNotModifyUrlWhenUrlHasVsacPrefix() throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(401)
            .setBody("")
            .addHeader("Content-Type", "application/fhir+json"));
    var queryParams = new java.util.HashMap<String, String>();
    queryParams.put("url", "http://cts.nlm.nih.gov/fhir/ValueSet/12345");
    assertThrows(
        ValueSetExpansionException.class,
        () -> fhirTerminologyServiceWebClient.searchValueSets(MOCK_API_KEY, queryParams));
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    String path = recordedRequest.getPath();
    assertNotNull(path);
    assertTrue(path.contains("url=http%3A%2F%2Fcts.nlm.nih.gov%2Ffhir%2FValueSet%2F12345"));
  }

  /* branch coverage for searchValueSets(), line 92:
   * !queryParams.containsKey("url")
   */
  @Test
  void searchValueSetsHandlesOtherQueryParams() throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(401)
            .setBody("")
            .addHeader("Content-Type", "application/fhir+json"));
    var queryParams = new java.util.HashMap<String, String>();
    queryParams.put("foo", "bar");
    queryParams.put("baz", "qux");
    assertThrows(
        ValueSetExpansionException.class,
        () -> fhirTerminologyServiceWebClient.searchValueSets(MOCK_API_KEY, queryParams));
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    String path = recordedRequest.getPath();
    assertNotNull(path);
    assertTrue(path.contains("foo=bar"));
    assertTrue(path.contains("baz=qux"));
  }

  /* covers searchValueSets(), line 120:
   * return fetchResourceFromVsac(uri.toString(), apiKey, "bundle");
   */
  @Test
  void searchValueSetsReturnsBundleOnSuccess() throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(MOCK_RESPONSE_STRING)
            .addHeader("Content-Type", "application/fhir+json"));
    var queryParams = new java.util.HashMap<String, String>();
    queryParams.put("url", "12345");
    String response = fhirTerminologyServiceWebClient.searchValueSets(MOCK_API_KEY, queryParams);
    assertNotNull(response);
    assertEquals(MOCK_RESPONSE_STRING, response);
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    String path = recordedRequest.getPath();
    assertNotNull(path);
    assertTrue(path.contains("url=http%3A%2F%2Fcts.nlm.nih.gov%2Ffhir%2FValueSet%2F12345"));
  }

  /* covers fetchResourceFromVsac(), lines 166-179
   * if (clientResponse.statusCode().equals(HttpStatus.NOT_FOUND))
   */
  @Test
  void searchValueSetsThrowsResourceNotFoundExceptionOn404() throws InterruptedException {
    mockBackEnd.enqueue(
        new MockResponse()
            .setResponseCode(404)
            .setBody("")
            .addHeader("Content-Type", "application/fhir+json"));
    var queryParams = new java.util.HashMap<String, String>();
    queryParams.put("url", "12345");
    assertThrows(
        ResourceNotFoundException.class,
        () -> fhirTerminologyServiceWebClient.searchValueSets(MOCK_API_KEY, queryParams));
    RecordedRequest recordedRequest = mockBackEnd.takeRequest();
    String path = recordedRequest.getPath();
    assertNotNull(path);
    assertTrue(path.contains("url=http%3A%2F%2Fcts.nlm.nih.gov%2Ffhir%2FValueSet%2F12345"));
  }
}
