package gov.cms.madie.terminology.webclient;

import gov.cms.madie.models.cql.terminology.VsacCode;
import gov.cms.madie.terminology.util.TerminologyServiceUtil;

import org.springframework.http.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;

@Component
@Slf4j
public class TerminologyServiceWebClient {

  private final WebClient terminologyClient;
  private final String baseUrl;
  private final String serviceTicketEndpoint;
  private final String valueSetEndpoint;
  private final String defaultProfile;
  private final String utsLoginEndpoint;

  public TerminologyServiceWebClient(
      WebClient.Builder webClientBuilder,
      @Value("${client.vsac_base_url}") String baseUrl,
      @Value("${client.service_ticket_endpoint}") String serviceTicketEndpoint,
      @Value("${client.valueset_endpoint}") String valueSetEndpoint,
      @Value("${client.default_profile}") String defaultProfile,
      @Value("${client.uts_login}") String utsLoginEndpoint) {

    this.terminologyClient = webClientBuilder.baseUrl(baseUrl).build();
    this.baseUrl = baseUrl;
    this.serviceTicketEndpoint = serviceTicketEndpoint;
    this.valueSetEndpoint = valueSetEndpoint;
    this.defaultProfile = defaultProfile;
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
        .exchangeToMono(
            clientResponse -> {
              if (clientResponse.statusCode().equals(HttpStatus.OK)
                  || clientResponse.statusCode().equals(HttpStatus.UNAUTHORIZED)) {
                return clientResponse.bodyToMono(String.class);
              } else {
                return clientResponse.createException().flatMap(Mono::error);
              }
            })
        .block();
  }

  public String getTgt(String apiKey) throws InterruptedException, ExecutionException {
    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
    formData.add("apikey", apiKey);
    Mono<String> responseMono =
        terminologyClient
            .post()
            .uri(baseUrl + utsLoginEndpoint)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromValue(formData))
            .retrieve()
            .onStatus(HttpStatus::is5xxServerError, ClientResponse::createException)
            .onStatus(HttpStatus::is4xxClientError, ClientResponse::createException)
            .bodyToMono(String.class);
    responseMono.subscribe();
    log.debug("Exiting getTgt()");
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
    profile = StringUtils.isBlank(profile) ? defaultProfile : profile;
    return TerminologyServiceUtil.buildRetrieveMultipleValueSetsUri(
        baseUrl, valueSetEndpoint, oid, serviceTicket, profile, includeDraft, release, version);
  }

  /**
   * @param codePath code path build to call VSAC services
   * @param serviceTicket single use service ticket
   * @return the response from VSAC is the statusCode is either 200 or 400 Status Code 200 indicates
   *     a valid code Status Code 400 indicates either CodeSystem or CodeSystem version or Code is
   *     not found
   */
  public VsacCode getCode(String codePath, String serviceTicket) {
    URI codeUri = TerminologyServiceUtil.buildRetrieveCodeUri(baseUrl, codePath, serviceTicket);
    log.debug("Retrieving vsacCode for codePath {}", codePath);
    return terminologyClient
        .get()
        .uri(codeUri)
        .exchangeToMono(
            clientResponse -> {
              if (clientResponse.statusCode().equals(HttpStatus.BAD_REQUEST)
                  || clientResponse.statusCode().equals(HttpStatus.OK)) {
                return clientResponse.bodyToMono(VsacCode.class);
              } else {
                log.debug("Received NON-OK response while retrieving codePath {}", codePath);
                return clientResponse.createException().flatMap(Mono::error);
              }
            })
        .block();
  }
}
