package gov.cms.madie.terminology.webclient;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.exceptions.ResourceNotFoundException;
import gov.cms.madie.terminology.exceptions.ValueSetExpansionException;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.nio.charset.Charset;
import java.time.Duration;

/**
 * WebClient for the TX FHIR Terminology Service (TxFHIR) R4. <a href="https://tx.fhir.org/r4/">TX
 * FHIR</a>
 */
@Component
@Slf4j
public class TxTerminologyServiceWebClient {

  private final WebClient txTerminologyWebClient;
  private final String expansionPath;
  private final FhirContext fhirContext;

  public TxTerminologyServiceWebClient(
      @Value("${client.tx-terminology-service.base-url}") String txTerminologyServiceBaseUrl,
      @Value("${client.tx-terminology-service.expansion-urn}") String expansionUrn,
      FhirContext fhirContext) {

    DefaultUriBuilderFactory uriBuilderFactory =
        new DefaultUriBuilderFactory(txTerminologyServiceBaseUrl);
    uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);

    ConnectionProvider provider =
        ConnectionProvider.builder("tx-custom").maxIdleTime(Duration.ofSeconds(300)).build();
    HttpClient client =
        HttpClient.create(provider).followRedirect(true).option(ChannelOption.SO_KEEPALIVE, true);

    txTerminologyWebClient =
        WebClient.builder()
            .uriBuilderFactory(uriBuilderFactory)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .codecs(
                clientCodecConfigurer -> clientCodecConfigurer.defaultCodecs().maxInMemorySize(-1))
            .clientConnector(new ReactorClientHttpConnector(client))
            .build();

    this.expansionPath = expansionUrn;
    this.fhirContext = fhirContext;
  }

  /**
   * Fetches the expansion of a ValueSet from the TX Terminology Service.
   *
   * @param valueSetUrl the canonical URL of the ValueSet to expand
   * @param valueSetVersion (optional) the version of the ValueSet to expand
   * @return the raw JSON response string containing the expanded ValueSet
   */
  public String getValueSetExpansion(String valueSetUrl, String valueSetVersion) {
    String uri =
        expansionPath
            .replace("{fullUrl}", valueSetUrl)
            .replace("{version}", valueSetVersion == null ? "" : valueSetVersion);
    log.debug(
        "Retrieving ValueSet expansion from TX Terminology Service for ValueSet url: {}",
        valueSetUrl);
    return fetchResource(uri);
  }

  /**
   * Performs a generic GET against the TX Terminology Service.
   *
   * <p>Returns the raw response body on HTTP 200. Logs error on any other non-OK status.
   *
   * @param uri the full path (relative to the configured base-url) to request
   * @return the raw JSON response string
   */
  public String fetchResource(String uri) {
    return txTerminologyWebClient
        .get()
        .uri(uri)
        .accept(new MediaType("application", "json+fhir", Charset.defaultCharset()))
        .exchangeToMono(
            clientResponse -> {
              if (clientResponse.statusCode().isSameCodeAs(HttpStatus.OK)) {
                return clientResponse.bodyToMono(String.class);
              }
              if (clientResponse.statusCode().isSameCodeAs(HttpStatus.UNPROCESSABLE_ENTITY)) {
                return clientResponse
                    .bodyToMono(String.class)
                    .flatMap(
                        body -> {
                          OperationOutcome outcome =
                              fhirContext
                                  .newJsonParser()
                                  .parseResource(OperationOutcome.class, body);
                          return switch (outcome.getIssueFirstRep().getCode()) {
                            case NOTFOUND -> {
                              log.warn("Resource not found in TX Terminology Service: {}", uri);
                              yield Mono.error(
                                  new ResourceNotFoundException(
                                      "not-found", HttpStatus.NOT_FOUND, "not-found", body, uri));
                            }
                            case TOOCOSTLY -> {
                              log.warn(
                                  "Operation too costly in TX Terminology Service for {}", uri);
                              yield Mono.error(
                                  new ValueSetExpansionException(
                                      "too-costly",
                                      HttpStatus.UNPROCESSABLE_ENTITY,
                                      "too-costly",
                                      body,
                                      null,
                                      uri));
                            }
                            default -> {
                              log.error("Error from TX Terminology Service for {}: {}", uri, body);
                              yield clientResponse
                                  .createException()
                                  .flatMap(
                                      ex ->
                                          Mono.error(
                                              new ValueSetExpansionException(
                                                  "error",
                                                  ex.getStatusCode(),
                                                  ex.getStatusText(),
                                                  body,
                                                  null,
                                                  uri)));
                            }
                          };
                        });
              }
              return Mono.empty();
            })
        .block();
  }
}
