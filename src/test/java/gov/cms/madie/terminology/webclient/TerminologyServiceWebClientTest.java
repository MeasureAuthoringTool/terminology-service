package gov.cms.madie.terminology.webclient;

import gov.cms.madie.models.cql.terminology.VsacCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
}
