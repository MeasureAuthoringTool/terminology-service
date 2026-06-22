package gov.cms.madie.terminology.webclient;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.terminology.exceptions.VsacBatchValueSetExpansionException;
import gov.cms.madie.terminology.exceptions.ValueSetExpansionException;
import gov.cms.madie.terminology.exceptions.ResourceNotFoundException;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.util.TerminologyServiceUtil;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
public class FhirTerminologyServiceWebClient {

  private final WebClient fhirTerminologyWebClient;
  private final String manifestPath;
  private final String codeSystemPath;
  private final String codeLookupsUrl;
  private final String defaultProfile;
  private final String searchValueSetEndpoint;
  private final FhirContext fhirContext;

  public FhirTerminologyServiceWebClient(
      @Value("${client.fhir-terminology-service.base-url}") String fhirTerminologyServiceBaseUrl,
      @Value("${client.fhir-terminology-service.manifests-urn}") String manifestUrn,
      @Value("${client.fhir-terminology-service.code-system-urn}") String codeSystemUrn,
      @Value("${client.fhir-terminology-service.code-lookups}") String codeLookupsUrl,
      @Value("${client.default_profile}") String defaultProfile,
      @Value("${client.search_value_set_endpoint}") String searchValueSetEndpoint,
      FhirContext fhirContext) {
    this.fhirContext = fhirContext;
    DefaultUriBuilderFactory uriBuilderFactory =
        new DefaultUriBuilderFactory(fhirTerminologyServiceBaseUrl);
    uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
    ConnectionProvider provider =
        ConnectionProvider.builder("custom").maxIdleTime(Duration.ofSeconds(300)).build();
    HttpClient client =
        HttpClient.create(provider).followRedirect(true).option(ChannelOption.SO_KEEPALIVE, true);
    fhirTerminologyWebClient =
        WebClient.builder()
            .uriBuilderFactory(uriBuilderFactory)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .codecs(
                clientCodecConfigurer -> clientCodecConfigurer.defaultCodecs().maxInMemorySize(-1))
            .clientConnector(new ReactorClientHttpConnector(client))
            .build();
    this.manifestPath = manifestUrn;
    this.codeSystemPath = codeSystemUrn;
    this.codeLookupsUrl = codeLookupsUrl;
    this.defaultProfile = defaultProfile;
    this.searchValueSetEndpoint = searchValueSetEndpoint;
  }

  public String getManifestBundle(String apiKey) {
    return fetchResourceFromVsac(manifestPath, apiKey, "Manifest");
  }

  public String getCodeSystemsPage(Integer offset, Integer count, String apiKey) {
    //  https://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=0&_count=100
    URI codeUri = TerminologyServiceUtil.buildRetrieveCodeSystemsUri(codeSystemPath, offset, count);
    log.debug("Retrieving codeSystems at {}, offset {}, count {}", codeSystemPath, offset, count);
    return fetchResourceFromVsac(codeUri.toString(), apiKey, "CodeSystem");
  }

  public String searchValueSets(String apiKey, Map<String, String> queryParams) {
    if (queryParams.containsKey("url")) {
      String urlValue = queryParams.get("url");
      // if the value does not contain the vsac url we add it
      if (!urlValue.startsWith("http://cts.nlm.nih.gov/fhir/ValueSet/")) {
        urlValue = "http://cts.nlm.nih.gov/fhir/ValueSet/" + urlValue;
      }
      // if user didn't add http:// we do
      if (!urlValue.startsWith("http://")) {
        urlValue = "http://" + urlValue;
      }
      queryParams.put("url", urlValue);
    }

    // Manually construct the query string
    String queryString =
        queryParams.entrySet().stream()
            .map(
                entry ->
                    URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));

    String url = searchValueSetEndpoint + "?" + queryString;

    URI uri = URI.create(url);

    log.info("value set search url is: {}", uri.toString());
    return fetchResourceFromVsac(uri.toString(), apiKey, "bundle");
  }

  public String getValueSetResources(String apiKey, ValueSetsSearchCriteria searchCriteria) {
    return fetchBatchResourcesFromVsac(getUris(searchCriteria), apiKey, "ValueSet");
  }

  public List<String> getUris(ValueSetsSearchCriteria searchCriteria) {
    String profile =
        StringUtils.isNotBlank(searchCriteria.getProfile())
            ? defaultProfile
            : searchCriteria.getProfile();

    return searchCriteria.getValueSetParams().stream()
        .map(
            valueSetParam ->
                TerminologyServiceUtil.buildValueSetResourceUri(
                    valueSetParam,
                    profile,
                    searchCriteria.getIncludeDraft(),
                    searchCriteria.getActiveOnly(),
                    searchCriteria.getManifestExpansion()))
        .filter(Objects::nonNull)
        .map(URI::toString)
        .distinct()
        .collect(Collectors.toList());
  }

  public String getCodeResource(String code, CodeSystem codeSystem, String apiKey) {
    Map<String, String> params =
        Map.of(
            "fullUrl",
            codeSystem.getFullUrl(),
            "code",
            code,
            "version",
            codeSystem.getVersion().getFhirVersion());
    URI uri =
        UriComponentsBuilder.fromUriString(codeLookupsUrl).buildAndExpand(params).encode().toUri();
    return fetchResourceFromVsac(uri.toString(), apiKey, "Code");
  }

  public String fetchResourceFromVsac(String uri, String apiKey, String resourceType) {
    return fhirTerminologyWebClient
        .get()
        .uri(uri)
        .headers(headers -> headers.setBasicAuth("apikey", apiKey))
        .accept(new MediaType("application", "fhir+json", Charset.defaultCharset()))
        .exchangeToMono(
            clientResponse -> {
              if (clientResponse.statusCode().isSameCodeAs(HttpStatus.OK)) {
                return clientResponse.bodyToMono(String.class);
              } else if (clientResponse.statusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                log.debug("Received NOT_FOUND response while retrieving {}", resourceType);
                return clientResponse
                    .createException()
                    .flatMap(
                        ex ->
                            Mono.error(
                                new ResourceNotFoundException(
                                    "",
                                    ex.getStatusCode(),
                                    ex.getStatusText(),
                                    ex.getResponseBodyAsString(),
                                    uri)));

              } else {
                log.debug("Received NON-OK response while retrieving {}", resourceType);
                return clientResponse
                    .createException()
                    .flatMap(
                        ex ->
                            Mono.error(
                                new ValueSetExpansionException(
                                    "",
                                    ex.getStatusCode(),
                                    ex.getStatusText(),
                                    ex.getResponseBodyAsString(),
                                    uri.contains("manifest") ? "Manifest" : "Latest",
                                    uri)));
              }
            })
        .block();
  }

  @SuppressWarnings("CPD-START")
  public String fetchBatchResourcesFromVsac(List<String> uris, String apiKey, String resourceType) {
    String result =
        fhirTerminologyWebClient
            .post()
            .headers(headers -> headers.setBasicAuth("apikey", apiKey))
            .bodyValue(buildBatchBundle(uris))
            .header("Content-Type", "application/fhir+json")
            .accept(new MediaType("application", "fhir+json", Charset.defaultCharset()))
            .exchangeToMono(
                clientResponse -> {
                  if (clientResponse.statusCode().isSameCodeAs(HttpStatus.OK)) {
                    return clientResponse.bodyToMono(String.class);
                  } else {
                    return clientResponse
                        .createException()
                        .flatMap(
                            ex -> {
                              log.debug(
                                  "Received NON-OK response while retrieving [{}] with " + "[{}] ",
                                  resourceType,
                                  uris,
                                  ex);
                              return Mono.error(
                                  new VsacBatchValueSetExpansionException(
                                      "Failed to fetch batch resources from VSAC",
                                      ex.getStatusCode(),
                                      ex.getStatusText(),
                                      ex.getResponseBodyAsString()));
                            });
                  }
                })
            .block();
    return result;
  }

  private String buildBatchBundle(List<String> uris) {

    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.BATCH);
    uris.forEach(
        value -> {
          Bundle.BundleEntryComponent compo = new Bundle.BundleEntryComponent();
          Bundle.BundleEntryRequestComponent request = new Bundle.BundleEntryRequestComponent();
          request.setMethod(Bundle.HTTPVerb.GET);
          request.setUrl(value);
          compo.setRequest(request);
          bundle.addEntry(compo);
        });

    IParser parser = fhirContext.newJsonParser();
    return parser.encodeToString(bundle);
  }
}
