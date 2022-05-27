package cms.gov.madie.terminology.webclient;

import org.springframework.stereotype.Component;
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
  private final String serviceticketEndpoint;
  private final String valuesetEndpoint;
  private final String utsLoginEndpoint;

  public TerminologyServiceWebClient(
      WebClient.Builder webClientBuilder,
      @Value("${client.vsac_base_url}") String baseUrl,
      @Value("${client.service_ticket_endpoint}") String serviceticketEndpoint,
      @Value("${client.valueset_endpoint}") String valuesetEndpoint,
      @Value("${client.uts_login}") String utsLoginEndpoint) {
    this.terminologyClient = webClientBuilder.baseUrl(baseUrl).build();
    this.baseUrl = baseUrl;
    this.serviceticketEndpoint = serviceticketEndpoint;
    this.valuesetEndpoint = valuesetEndpoint;
    this.utsLoginEndpoint = utsLoginEndpoint;
    log.debug(
        "baseUrl = "
            + baseUrl
            + " serviceticketEndpoint = "
            + serviceticketEndpoint
            + " utsLoginEndpoint = "
            + utsLoginEndpoint);
  }

  public String getServiceTicket(String tgt) throws InterruptedException, ExecutionException {
    String uri = String.format(baseUrl + serviceticketEndpoint, tgt);
    Mono<String> responseMono =
        terminologyClient
            .post()
            .uri(uri)
            .bodyValue(tgt)
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
    log.debug("Exiting getServiceTicket()");
    return responseMono.toFuture().get();
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
        baseUrl, valuesetEndpoint, oid, serviceTicket, profile, includeDraft, release, version);
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
