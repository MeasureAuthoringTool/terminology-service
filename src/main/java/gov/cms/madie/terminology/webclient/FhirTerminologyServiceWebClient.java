package gov.cms.madie.terminology.webclient;

import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.util.TerminologyServiceUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponents;
import org.yaml.snakeyaml.Yaml;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.Charset;

@Component
@Slf4j
public class FhirTerminologyServiceWebClient {

  private final WebClient fhirTerminologyWebClient;
  private final String manifestPath;

  private final String defaultProfile;

  public FhirTerminologyServiceWebClient(
      @Value("${client.fhir-terminology-service.base-url}") String fhirTerminologyServiceBaseUrl,
      @Value("${client.fhir-terminology-service.manifests-urn}") String manifestUrn,
      @Value("${client.default_profile}") String defaultProfile) {
    fhirTerminologyWebClient =
        WebClient.builder()
            .baseUrl(fhirTerminologyServiceBaseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    this.manifestPath = manifestUrn;
    this.defaultProfile = defaultProfile;
  }

  public String getManifestBundle(String apiKey) {
    return fhirTerminologyWebClient
        .get()
        .uri(manifestPath)
        .headers(headers -> headers.setBasicAuth("apikey", apiKey))
        .accept(new MediaType("application", "fhir+json", Charset.defaultCharset()))
        .exchangeToMono(
            clientResponse -> {
              if (clientResponse.statusCode().equals(HttpStatus.OK)) {
                return clientResponse.bodyToMono(String.class);
              } else {
                log.debug("Received NON-OK response while retrieving Manifests");
                return clientResponse.createException().flatMap(Mono::error);
              }
            })
        .block();
  }

  public String getValueSetResource(
      String apiKey,
      ValueSetsSearchCriteria.ValueSetParams valueSetParams,
      String profile,
      String includeDraft,
      ManifestExpansion manifestExpansion) {
    profile = StringUtils.isNotBlank(profile) ? defaultProfile : profile;
    URI uri =
        TerminologyServiceUtil.buildValueSetResourceUri(
            valueSetParams, profile, includeDraft, manifestExpansion);
    return fhirTerminologyWebClient
        .get()
        .uri(uri.toString())
        .headers(headers -> headers.setBasicAuth("apikey", apiKey))
        .accept(new MediaType("application", "fhir+json", Charset.defaultCharset()))
        .exchangeToMono(
            clientResponse -> {
              if (clientResponse.statusCode().equals(HttpStatus.OK)) {
                return clientResponse.bodyToMono(String.class);
              } else {
                log.debug("Received NON-OK response while retrieving Manifests");
                return clientResponse.createException().flatMap(Mono::error);
              }
            })
        .block();
  }
}
