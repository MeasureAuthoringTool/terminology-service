package gov.cms.madie.terminology.webclient;

import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.util.TerminologyServiceUtil;
import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;

@Component
@Slf4j
public class FhirTerminologyServiceWebClient {

  private final WebClient fhirTerminologyWebClient;
  private final String manifestPath;
  private final String codeSystemPath;
  private final String codeLookupsUrl;
  private final String defaultProfile;

  public FhirTerminologyServiceWebClient(
      @Value("${client.fhir-terminology-service.base-url}") String fhirTerminologyServiceBaseUrl,
      @Value("${client.fhir-terminology-service.manifests-urn}") String manifestUrn,
      @Value("${client.fhir-terminology-service.code-system-urn}") String codeSystemUrn,
      @Value("${client.fhir-terminology-service.code-lookups}") String codeLookupsUrl,
      @Value("${client.default_profile}") String defaultProfile) {
    fhirTerminologyWebClient =
        WebClient.builder()
            .baseUrl(fhirTerminologyServiceBaseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .codecs(
                clientCodecConfigurer -> clientCodecConfigurer.defaultCodecs().maxInMemorySize(-1))
            .build();
    this.manifestPath = manifestUrn;
    this.codeSystemPath = codeSystemUrn;
    this.codeLookupsUrl = codeLookupsUrl;
    this.defaultProfile = defaultProfile;
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

    return fetchResourceFromVsac(uri.toString(), apiKey, "ValueSet");
  }

  public String getCodeResource(String code, CodeSystem codeSystem, String apiKey) {
    Map<String, String> params =
        Map.of(
            "fullUrl", codeSystem.getFullUrl(), "code", code, "version", codeSystem.getVersion());
    URI uri =
        UriComponentsBuilder.fromUriString(codeLookupsUrl).buildAndExpand(params).encode().toUri();
    return fetchResourceFromVsac(uri.toString(), apiKey, "Code");
  }

  private String fetchResourceFromVsac(String uri, String apiKey, String resourceType) {
    return fhirTerminologyWebClient
        .get()
        .uri(uri)
        .headers(headers -> headers.setBasicAuth("apikey", apiKey))
        .accept(new MediaType("application", "fhir+json", Charset.defaultCharset()))
        .exchangeToMono(
            clientResponse -> {
              if (clientResponse.statusCode().equals(HttpStatus.OK)) {
                return clientResponse.bodyToMono(String.class);
              } else {
                log.debug("Received NON-OK response while retrieving {}", resourceType);
                return clientResponse.createException().flatMap(Mono::error);
              }
            })
        .block();
  }
}
