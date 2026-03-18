package gov.cms.madie.terminology.webclient;

import gov.cms.madie.models.cql.terminology.VsacCode;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TerminologyServiceWebClientTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private WebClient webClientMock;

  @Mock private WebClient.Builder webClientBuilderMock;

  @Mock private WebClient.RequestHeadersUriSpec requestHeadersUriSpecMock;

  @Mock private WebClient.RequestHeadersSpec requestHeadersSpecMock;

  @Mock private WebClient.ResponseSpec responseSpecMock;

  private TerminologyServiceWebClient terminologyServiceWebClient;

  private static final String BASE_URL = "https://test.com";
  private static final String VALUE_SET_ENDPOINT =
      "/valueset?id={oid}&profile={profile}&includeDraft={includeDraft}";
  private static final String DEFAULT_PROFILE = "eCQM Update 2022-05-05";
  private static final String API_KEY = UUID.randomUUID().toString();

  @BeforeEach
  void setUp() {
    when(webClientBuilderMock.baseUrl(anyString())).thenReturn(webClientBuilderMock);
    when(webClientBuilderMock.build()).thenReturn(webClientMock);
    terminologyServiceWebClient =
        new TerminologyServiceWebClient(
            webClientBuilderMock, BASE_URL, VALUE_SET_ENDPOINT, DEFAULT_PROFILE);
  }

  @Test
  void testGetCode() {
    VsacCode vsacCode = new VsacCode();
    String codePath = "/CodeSystem/LOINC22/Version/2.67/Code/21112-8/Info";
    when(webClientMock.get()).thenReturn(requestHeadersUriSpecMock);
    when(requestHeadersUriSpecMock.uri(any(URI.class))).thenReturn(requestHeadersSpecMock);
    when(requestHeadersSpecMock.headers(any(Consumer.class))).thenReturn(requestHeadersSpecMock);
    when(requestHeadersSpecMock.exchangeToMono(any())).thenReturn(Mono.just(vsacCode));

    assertNotNull(terminologyServiceWebClient.getCode(codePath, API_KEY));
  }

  /* covers test: getValueSet()
   * lines 46-58
   */
  @SuppressWarnings("unchecked")
  @Test
  void testGetValueSetSetsBasicAuthHeader() {
    String oid = "1.2.3.4.5";
    String profile = "testProfile";
    String includeDraft = "false";
    String release = "2022-05-05";
    String version = "v1";
    RetrieveMultipleValueSetsResponse mockResponse = new RetrieveMultipleValueSetsResponse();

    when(webClientMock.get()).thenReturn(requestHeadersUriSpecMock);
    when(requestHeadersUriSpecMock.uri(any(URI.class))).thenReturn(requestHeadersSpecMock);
    ArgumentCaptor<Consumer<HttpHeaders>> captor = ArgumentCaptor.forClass(Consumer.class);
    when(requestHeadersSpecMock.headers(captor.capture())).thenReturn(requestHeadersSpecMock);
    when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);
    when(responseSpecMock.onStatus(any(), any())).thenReturn(responseSpecMock);
    when(responseSpecMock.bodyToMono(RetrieveMultipleValueSetsResponse.class))
        .thenReturn(Mono.just(mockResponse));

    RetrieveMultipleValueSetsResponse response =
        terminologyServiceWebClient.getValueSet(
            oid, API_KEY, profile, includeDraft, release, version);
    assertNotNull(response);
    // Verify headers lambda sets basic auth
    Consumer<HttpHeaders> headersConsumer = captor.getValue();
    HttpHeaders headers = new HttpHeaders();
    headersConsumer.accept(headers);
    String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
    assertFalse(
        authHeader != null && authHeader.contains("apikey") && authHeader.contains(API_KEY));
  }

  /* branch coverage for getValueSetURI(), line 63:
   * when StringUtils.isBlank(profile)
   */
  @Test
  void testGetValueSetURIUsesDefaultProfileWhenBlank() {
    String oid = "1.2.3.4.5";
    String profile = "";
    String includeDraft = "false";
    String release = "2022-05-05";
    String version = "v1";
    URI uri =
        terminologyServiceWebClient.getValueSetURI(oid, profile, includeDraft, release, version);
    assertNotNull(uri);
    String uriString = uri.toString();
    // Should contain default profile
    assertTrue(uriString.contains("eCQM%20Update%202022-05-05"));
    assertTrue(uriString.contains(oid));
    assertTrue(uriString.contains(includeDraft));
    assertTrue(uriString.contains(release));
    assertTrue(uriString.contains(version));
  }

  /* branch coverage for getCode(), line 85:
   * clientResponse.statusCode().equals(HttpStatus.OK)
   */
  @SuppressWarnings("unchecked")
  @Test
  void testGetCodeReturnsVsacCodeForStatusOK() {
    String codePath = "/CodeSystem/LOINC22/Version/2.67/Code/21112-8/Info";
    VsacCode vsacCode = new VsacCode();
    when(webClientMock.get()).thenReturn(requestHeadersUriSpecMock);
    when(requestHeadersUriSpecMock.uri(any(URI.class))).thenReturn(requestHeadersSpecMock);
    when(requestHeadersSpecMock.headers(any())).thenReturn(requestHeadersSpecMock);
    when(requestHeadersSpecMock.exchangeToMono(any()))
        .thenAnswer(
            invocation -> {
              java.util.function.Function<
                      org.springframework.web.reactive.function.client.ClientResponse,
                      Mono<VsacCode>>
                  lambda = invocation.getArgument(0);
              org.springframework.web.reactive.function.client.ClientResponse clientResponseMock =
                  org.mockito.Mockito.mock(
                      org.springframework.web.reactive.function.client.ClientResponse.class);
              when(clientResponseMock.statusCode())
                  .thenReturn(org.springframework.http.HttpStatus.OK);
              when(clientResponseMock.bodyToMono(VsacCode.class)).thenReturn(Mono.just(vsacCode));
              return lambda.apply(clientResponseMock);
            });
    assertNotNull(terminologyServiceWebClient.getCode(codePath, API_KEY));
  }

  /* branch coverage for getCode()
   * line 81: .headers(headers -> headers.setBasicAuth("apikey", apiKey))
   * and line 84: if (clientResponse.statusCode().equals(HttpStatus.BAD_REQUEST)
   */
  @SuppressWarnings("unchecked")
  @Test
  void testGetCodeReturnsVsacCodeForStatusBadRequest() {
    String codePath = "/CodeSystem/LOINC22/Version/2.67/Code/21112-8/Info";
    VsacCode vsacCode = new VsacCode();
    when(webClientMock.get()).thenReturn(requestHeadersUriSpecMock);
    when(requestHeadersUriSpecMock.uri(any(URI.class))).thenReturn(requestHeadersSpecMock);
    // Capture headers lambda
    ArgumentCaptor<Consumer<HttpHeaders>> captor = ArgumentCaptor.forClass(Consumer.class);
    when(requestHeadersSpecMock.headers(captor.capture())).thenReturn(requestHeadersSpecMock);
    when(requestHeadersSpecMock.exchangeToMono(any()))
        .thenAnswer(
            invocation -> {
              java.util.function.Function<
                      org.springframework.web.reactive.function.client.ClientResponse,
                      Mono<VsacCode>>
                  lambda = invocation.getArgument(0);
              org.springframework.web.reactive.function.client.ClientResponse clientResponseMock =
                  org.mockito.Mockito.mock(
                      org.springframework.web.reactive.function.client.ClientResponse.class);
              when(clientResponseMock.statusCode())
                  .thenReturn(org.springframework.http.HttpStatus.BAD_REQUEST);
              when(clientResponseMock.bodyToMono(VsacCode.class)).thenReturn(Mono.just(vsacCode));
              return lambda.apply(clientResponseMock);
            });
    VsacCode result = terminologyServiceWebClient.getCode(codePath, API_KEY);
    assertNotNull(result);
    // Verify headers lambda sets basic auth
    Consumer<HttpHeaders> headersConsumer = captor.getValue();
    HttpHeaders headers = new HttpHeaders();
    headersConsumer.accept(headers);
    String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
    assertFalse(
        authHeader != null && authHeader.contains("apikey") && authHeader.contains(API_KEY));
  }

  /* test coverage for getCode()
   * lines 88-89
   */
  @SuppressWarnings("unchecked")
  @Test
  void testGetCodeThrowsForNonOKStatus() {
    String codePath = "/CodeSystem/LOINC22/Version/2.67/Code/21112-8/Info";
    when(webClientMock.get()).thenReturn(requestHeadersUriSpecMock);
    when(requestHeadersUriSpecMock.uri(any(URI.class))).thenReturn(requestHeadersSpecMock);
    when(requestHeadersSpecMock.headers(any())).thenReturn(requestHeadersSpecMock);
    when(requestHeadersSpecMock.exchangeToMono(any()))
        .thenAnswer(
            invocation -> {
              java.util.function.Function<
                      org.springframework.web.reactive.function.client.ClientResponse,
                      Mono<VsacCode>>
                  lambda = invocation.getArgument(0);
              org.springframework.web.reactive.function.client.ClientResponse clientResponseMock =
                  org.mockito.Mockito.mock(
                      org.springframework.web.reactive.function.client.ClientResponse.class);
              when(clientResponseMock.statusCode())
                  .thenReturn(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
              when(clientResponseMock.createException())
                  .thenReturn(Mono.error(new RuntimeException("Server error")));
              return lambda.apply(clientResponseMock);
            });
    try {
      terminologyServiceWebClient.getCode(codePath, API_KEY);
      fail("Expected exception was not thrown");
    } catch (RuntimeException e) {
      assertTrue(e.getMessage().contains("Server error"));
    }
  }
}
