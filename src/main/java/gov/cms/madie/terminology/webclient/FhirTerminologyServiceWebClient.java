package gov.cms.madie.terminology.webclient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.nio.charset.Charset;

@Component
@Slf4j
public class FhirTerminologyServiceWebClient {

  private final WebClient fhirTerminologyWebClient;
  private final String manifestPath;

  public FhirTerminologyServiceWebClient(
      @Value("${client.fhir-terminology-service.base-url}") String fhirTerminologyServiceBaseUrl,
      @Value("${client.fhir-terminology-service.manifests-urn}") String manifestUrn) {
    fhirTerminologyWebClient =
        WebClient.builder()
            .baseUrl(fhirTerminologyServiceBaseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    this.manifestPath = manifestUrn;
  }

  public String getManifestBundleResources(String apiKey) {
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
}
