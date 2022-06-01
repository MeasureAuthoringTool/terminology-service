package cms.gov.madie.terminology.webclient;

import gov.cms.madiejavamodels.cql.terminology.VsacCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import cms.gov.madie.terminology.util.TerminologyServiceUtil;

@Component
@Slf4j
public class TerminologyServiceWebClient {

  private final WebClient terminologyClient;
  private final String baseUrl;
  private final String serviceTicketEndpoint;
  private final String valueSetEndpoint;
  private final String utsLoginEndpoint;

  public TerminologyServiceWebClient(
      WebClient.Builder webClientBuilder,
      @Value("${client.vsac_base_url}") String baseUrl,
      @Value("${client.service_ticket_endpoint}") String serviceTicketEndpoint,
      @Value("${client.valueset_endpoint}") String valueSetEndpoint,
      @Value("${client.uts_login}") String utsLoginEndpoint) {
    this.terminologyClient = webClientBuilder.baseUrl(baseUrl).build();
    this.baseUrl = baseUrl;
    this.serviceTicketEndpoint = serviceTicketEndpoint;
    this.valueSetEndpoint = valueSetEndpoint;
    this.utsLoginEndpoint = utsLoginEndpoint;
    log.debug(
        "baseUrl = "
            + baseUrl
            + " serviceTicketEndpoint = "
            + serviceTicketEndpoint
            + " utsLoginEndpoint = "
            + utsLoginEndpoint);
  }

  public String getServiceTicket(String tgt) {
    return terminologyClient
        .post()
        .uri(String.format(baseUrl + serviceTicketEndpoint, tgt))
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .retrieve()
        .onStatus(HttpStatus::is5xxServerError, ClientResponse::createException)
        .onStatus(HttpStatus::is4xxClientError, ClientResponse::createException)
        .bodyToMono(String.class)
        .block();
  }

  public RetrieveMultipleValueSetsResponse getValueSet(
      String oid,
      String serviceTicket,
      String profile,
      String includeDraft,
      String release,
      String version) {
    URI valuesetURI = getValueSetURI(oid, serviceTicket, profile, includeDraft, release, version);
    log.debug("valuesetURI = " + valuesetURI.getQuery());
    Mono<RetrieveMultipleValueSetsResponse> responseMono =
        terminologyClient
            .get()
            .uri(valuesetURI)
            .retrieve()
            .onStatus(HttpStatus::is5xxServerError, ClientResponse::createException)
            .onStatus(HttpStatus::is4xxClientError, ClientResponse::createException)
            .bodyToMono(RetrieveMultipleValueSetsResponse.class);
    // temp use of block until fixing 401 issue
    return responseMono.block();
  }

  protected URI getValueSetURI(
      String oid,
      String serviceTicket,
      String profile,
      String includeDraft,
      String release,
      String version) {

    return TerminologyServiceUtil.buildRetrieveMultipleValueSetsUri(
        baseUrl, valueSetEndpoint, oid, serviceTicket, profile, includeDraft, release, version);
  }

  public VsacCode getCode(String codePath, String serviceTicket) {
    URI codeUri = TerminologyServiceUtil.buildRetrieveCodeUri(baseUrl, codePath, serviceTicket);
    log.debug("Retrieving vsacCode for codePath {}", codePath);
    return terminologyClient
        .get()
        .uri(codeUri)
        .retrieve()
        .onStatus(HttpStatus::is5xxServerError, ClientResponse::createException)
        .onStatus(HttpStatus::is4xxClientError, ClientResponse::createException)
        .bodyToMono(VsacCode.class)
        .block();
  }

  public String getTgt(String apiKey) throws InterruptedException, ExecutionException {
    String uri = String.format(baseUrl + utsLoginEndpoint, apiKey);
    Mono<String> responseMono =
        terminologyClient
            .post()
            .uri(uri)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .retrieve()
            .onStatus(
                HttpStatus::is5xxServerError,
                response -> {
                  return response.createException();
                })
            .onStatus(
                HttpStatus::is4xxClientError,
                response -> {
                  return response.createException();
                })
            .bodyToMono(String.class);
    responseMono.subscribe();
    log.debug("Exiting getTgt()");
    return responseMono.toFuture().get();
  }
}
