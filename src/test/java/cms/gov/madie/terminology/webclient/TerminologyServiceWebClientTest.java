package cms.gov.madie.terminology.webclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse.DescribedValueSet;

@ExtendWith(MockitoExtension.class)
public class TerminologyServiceWebClientTest {

  private WebClient mockWebClient;
  private WebClient.Builder mockWebClientBuilder;
  private WebClient.RequestHeadersUriSpec mockRequestHeadersUriSpec;
  private WebClient.RequestHeadersSpec mockRequestHeadersSpec;
  private WebClient.RequestBodyUriSpec mockRequestBodyUriSpec;
  private WebClient.RequestBodySpec mockRequestBodySpec;
  private WebClient.ResponseSpec mockResponseSpec;

  private static final String BASE_URL = "http://test.com";
  private static final String SERVICE_TICKET_ENDPOINT =
      "/service/ticket/%s?service=http://test.ticket.com";
  private static final String VALUE_SET_ENDPOINT =
      "/valueset?id={oid}&ticket={st}&profile={profile}&includeDraft={includeDraft}";
  private static final String DEFAULT_PROFILE = "eCQM Update 2022-05-05";
  private static final String TEST = "test";
  private RetrieveMultipleValueSetsResponse mockValueSetsResponse;
  private DescribedValueSet mockDescribedValueSet;

  @BeforeEach
  public void init() {
    mockWebClientBuilder = Mockito.mock(WebClient.Builder.class);

    mockWebClient = mock(WebClient.class);
    mockRequestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
    mockRequestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
    mockRequestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
    mockRequestBodySpec = mock(WebClient.RequestBodySpec.class);

    mockResponseSpec = mock(WebClient.ResponseSpec.class);

    mockValueSetsResponse = mock(RetrieveMultipleValueSetsResponse.class);
    mockDescribedValueSet = mock(DescribedValueSet.class);
  }

  @Test
  void testGetServiceTicket() throws InterruptedException, ExecutionException {

    when(mockWebClientBuilder.baseUrl(anyString())).thenReturn(mockWebClientBuilder);
    when(mockWebClientBuilder.build()).thenReturn(mockWebClient);

    when(mockWebClient.post()).thenReturn(mockRequestBodyUriSpec);
    when(mockRequestBodyUriSpec.uri(anyString())).thenReturn(mockRequestBodySpec);
    when(mockRequestBodySpec.bodyValue(anyString())).thenReturn(mockRequestHeadersSpec);
    when(mockRequestHeadersSpec.retrieve()).thenReturn(mockResponseSpec);

    when(mockResponseSpec.onStatus(any(), any())).thenReturn(mockResponseSpec);
    when(mockResponseSpec.bodyToMono(String.class)).thenReturn(Mono.just(TEST));

    TerminologyServiceWebClient terminologyServiceWebClient =
        new TerminologyServiceWebClient(
            mockWebClientBuilder,
            BASE_URL,
            SERVICE_TICKET_ENDPOINT,
            VALUE_SET_ENDPOINT,
            DEFAULT_PROFILE);
    String result = terminologyServiceWebClient.getServiceTicket(TEST);

    assertEquals(TEST, result);
  }
}
