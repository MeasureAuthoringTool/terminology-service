package gov.cms.madie.terminology.webclient;

import gov.cms.madie.models.cql.terminology.VsacCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TerminologyServiceWebClientTest {

  @Mock private WebClient webClientMock;

  @Mock private WebClient.Builder webClientBuilderMock;

  @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpecMock;

  @Mock private WebClient.RequestBodySpec requestBodySpecMock;

  @SuppressWarnings("rawtypes")
  @Mock
  private WebClient.RequestHeadersSpec requestHeadersSpecMock;

  @SuppressWarnings("rawtypes")
  @Mock
  private WebClient.RequestHeadersUriSpec requestHeadersUriSpecMock;

  @Mock private WebClient.ResponseSpec responseSpecMock;

  private TerminologyServiceWebClient terminologyServiceWebClient;

  private static final String BASE_URL = "https://test.com";
  private static final String SERVICE_TICKET_ENDPOINT =
      "/service/ticket/%s?service=https://test.ticket.com";
  private static final String VALUE_SET_ENDPOINT =
      "/valueset?id={oid}&ticket={st}&profile={profile}&includeDraft={includeDraft}";
  private static final String DEFAULT_PROFILE = "eCQM Update 2022-05-05";
  private static final String SERVICE_TICKET = "st-test";
  private static final String TGT = "tgt-test";

  private static final String TEST_API_KEY = "te$tKey";
  private static final String UTS_LOGIN_ENDPOINT = "/uts/login";

  @BeforeEach
  void setUp() {
    when(webClientBuilderMock.baseUrl(anyString())).thenReturn(webClientBuilderMock);
    when(webClientBuilderMock.build()).thenReturn(webClientMock);
    terminologyServiceWebClient =
        new TerminologyServiceWebClient(
            webClientBuilderMock,
            BASE_URL,
            SERVICE_TICKET_ENDPOINT,
            VALUE_SET_ENDPOINT,
            DEFAULT_PROFILE,
            UTS_LOGIN_ENDPOINT);
  }

  @Test
  void testGetServiceTicket() {
    when(webClientMock.post()).thenReturn(requestBodyUriSpecMock);
    when(requestBodyUriSpecMock.uri(anyString())).thenReturn(requestBodySpecMock);
    when(requestBodySpecMock.contentType(any())).thenReturn(requestBodySpecMock);
    when(requestBodySpecMock.exchangeToMono(any())).thenReturn(Mono.just(SERVICE_TICKET));

    assertEquals(SERVICE_TICKET, terminologyServiceWebClient.getServiceTicket(TGT));
  }

  @Test
  void testGetTgt() throws InterruptedException, ExecutionException {

    when(webClientMock.post()).thenReturn(requestBodyUriSpecMock);
    when(requestBodyUriSpecMock.uri(anyString())).thenReturn(requestBodySpecMock);
    when(requestBodySpecMock.contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .thenReturn(requestBodySpecMock);
    when(requestBodySpecMock.body(any())).thenReturn(requestHeadersSpecMock);
    when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);
    when(responseSpecMock.onStatus(any(), any())).thenReturn(responseSpecMock);
    when(responseSpecMock.bodyToMono(String.class)).thenReturn(Mono.just(TGT));

    assertEquals(TGT, terminologyServiceWebClient.getTgt(TEST_API_KEY));
  }

  @Test
  void testGetTgtThrows4xxClientError() {

    when(webClientMock.post()).thenReturn(requestBodyUriSpecMock);
    when(requestBodyUriSpecMock.uri(anyString())).thenReturn(requestBodySpecMock);
    when(requestBodySpecMock.contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .thenReturn(requestBodySpecMock);
    when(requestBodySpecMock.body(any())).thenReturn(requestHeadersSpecMock);
    when(requestHeadersSpecMock.retrieve()).thenReturn(responseSpecMock);

    WebClientResponseException webClientResponseException = mock(WebClientResponseException.class);
    when(responseSpecMock.onStatus(any(), any())).thenThrow(webClientResponseException);
    assertThrows(
        WebClientResponseException.class, () -> terminologyServiceWebClient.getTgt(TEST_API_KEY));
  }

  @Test
  void testGetCode() {
    VsacCode vsacCode = new VsacCode();
    String codePath = "/CodeSystem/LOINC22/Version/2.67/Code/21112-8/Info";
    when(webClientMock.get()).thenReturn(requestHeadersUriSpecMock);
    when(requestHeadersUriSpecMock.uri(any(URI.class))).thenReturn(requestHeadersSpecMock);
    when(requestHeadersSpecMock.exchangeToMono(any())).thenReturn(Mono.just(vsacCode));

    assertNotNull(terminologyServiceWebClient.getCode(codePath, SERVICE_TICKET));
  }
}
