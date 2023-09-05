package gov.cms.madie.terminology.webclient;

import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import gov.cms.madie.models.cql.terminology.VsacCode;
import gov.cms.madie.terminology.util.TerminologyServiceUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
@Slf4j
public class TerminologyServiceWebClient {

  private final WebClient terminologyClient;
  private final String baseUrl;
  private final String valueSetEndpoint;
  private final String defaultProfile;

  public TerminologyServiceWebClient(
      WebClient.Builder webClientBuilder,
      @Value("${client.vsac_base_url}") String baseUrl,
      @Value("${client.valueset_endpoint}") String valueSetEndpoint,
      @Value("${client.default_profile}") String defaultProfile) {

    this.terminologyClient = webClientBuilder.baseUrl(baseUrl).build();
    this.baseUrl = baseUrl;
    this.valueSetEndpoint = valueSetEndpoint;
    this.defaultProfile = defaultProfile;
    log.debug("baseUrl = " + baseUrl);
  }

  public RetrieveMultipleValueSetsResponse getValueSet(
      String oid,
      String apiKey,
      String profile,
      String includeDraft,
      String release,
      String version) {
    URI valuesetURI = getValueSetURI(oid, profile, includeDraft, release, version);
    log.debug("valuesetURI = " + valuesetURI.getQuery());
    Mono<RetrieveMultipleValueSetsResponse> responseMono =
        terminologyClient
            .get()
            .uri(valuesetURI)
            .headers(headers -> headers.setBasicAuth("apikey", apiKey))
            .retrieve()
            .onStatus(HttpStatus::is5xxServerError, ClientResponse::createException)
            .onStatus(HttpStatus::is4xxClientError, ClientResponse::createException)
            .bodyToMono(RetrieveMultipleValueSetsResponse.class);
    // temp use of block until fixing 401 issue
    return responseMono.block();
  }

  protected URI getValueSetURI(
      String oid, String profile, String includeDraft, String release, String version) {
    profile = StringUtils.isBlank(profile) ? defaultProfile : profile;
    return TerminologyServiceUtil.buildRetrieveMultipleValueSetsUri(
        baseUrl, valueSetEndpoint, oid, profile, includeDraft, release, version);
  }

  /**
   * @param codePath code path build to call VSAC services.
   * @param apiKey user's UMLS ApiKey.
   * @return the response from VSAC is the statusCode is either 200 or 400 Status Code 200 indicates
   *     a valid code Status Code 400 indicates either CodeSystem or CodeSystem version or Code is
   *     not found.
   */
  public VsacCode getCode(String codePath, String apiKey) {
    URI codeUri = TerminologyServiceUtil.buildRetrieveCodeUri(baseUrl, codePath);
    log.debug("Retrieving vsacCode for codePath {}", codePath);
    return terminologyClient
        .get()
        .uri(codeUri)
        .headers(headers -> headers.setBasicAuth("apikey", apiKey))
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
