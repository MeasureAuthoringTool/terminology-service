package gov.cms.madie.terminology.util;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TerminologyServiceUtil {

  public static URI buildRetrieveMultipleValueSetsUri(
      String baseUrl,
      String valuesetEndpoint,
      String oid,
      String profile,
      String includeDraft,
      String release,
      String version) {
    Map<String, String> params = new HashMap<>();
    String url = baseUrl + valuesetEndpoint;
    params.put("oid", oid);
    if (StringUtils.isNotBlank(profile)) {
      params.put("profile", profile);
    } else {
      params.put("profile", "Most Recent Code System Versions in VSAC");
    }
    if (StringUtils.isNotBlank(includeDraft)) {
      params.put("includeDraft", includeDraft);
    } else {
      params.put("includeDraft", "yes");
    }
    if (StringUtils.isNotBlank(release)) {
      params.put("release", release);
      url += "&release={release}";
    }
    if (StringUtils.isNotBlank(version)) {
      params.put("version", version);
      url += "&version={version}";
    }
    log.debug("RetrieveMultipleValueSetsUri = " + url);
    return UriComponentsBuilder.fromUriString(url).buildAndExpand(params).encode().toUri();
  }

  public static URI buildRetrieveCodeUri(String baseUrl, String codePath) {
    Map<String, String> params = new HashMap<>();
    params.put("resultFormat", "json");
    params.put("resultSet", "standard");
    return UriComponentsBuilder.fromUriString(
            baseUrl + codePath + "?resultFormat={resultFormat}&resultSet={resultSet}")
        .buildAndExpand(params)
        .encode()
        .toUri();
  }

  public static URI buildRetrieveCodeSystemsUri(String baseUrl, Integer offset, Integer count) {
    //    http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=100&_count=100
    return UriComponentsBuilder.fromUriString(baseUrl)
        .queryParam("_offset", Integer.toString(offset))
        .queryParam("_count", Integer.toString(count))
        .buildAndExpand()
        .encode()
        .toUri();
  }

  public static String buildCodePath(
      String codeSystemName, String codeSystemVersion, String codeId) {
    // "/CodeSystem/LOINC22/Version/2.67/Code/21112-8/Info";
    return "/CodeSystem/"
        + codeSystemName
        + "/Version/"
        + codeSystemVersion
        + "/Code/"
        + codeId
        + "/Info";
  }

  public static String sanitizeInput(String input) {
    return StringUtils.isBlank(input) ? "" : StringUtils.remove(input, "'");
  }

  public static String removeUrnOidSubString(String oid) {
    if (StringUtils.isNotBlank(oid) && oid.startsWith("urn:oid:")) {
      return oid.split("urn:oid:")[1];
    }
    return oid;
  }

  // Future stories will add ability to call new FHIR Terminology service
  // with additional parameters
  public static URI buildValueSetResourceUri(
      ValueSetsSearchCriteria.ValueSetParams valueSetParams,
      String profile,
      String includeDraft,
      String activeOnly,
      ManifestExpansion manifestExpansion) {
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    String expandValueSetUri;
    if (valueSetParams == null || StringUtils.isBlank(valueSetParams.getOid())) {
      return null;
    }
    expandValueSetUri = "/ValueSet/" + valueSetParams.getOid() + "/$expand";
    Integer offset = valueSetParams.getOffset();
    Integer count = valueSetParams.getCount();
    if (offset != null && offset >= 0) {
      params.put("offset", List.of(String.valueOf(offset)));
    }
    if (count != null && count >= 0) {
      params.put("count", List.of(String.valueOf(count)));
    }
    String version = valueSetParams.getVersion();
    if (StringUtils.isNotBlank(version)) {
      params.put("valueSetVersion", List.of(version));
    } else if (manifestExpansion != null) {
      String manifestUrl = manifestExpansion.getFullUrl();
      if (StringUtils.isNotBlank(manifestUrl)) {
        params.put("manifest", List.of(manifestUrl));
      }
    } else {
      if (StringUtils.isNotBlank(activeOnly)) {
        params.put("activeOnly", List.of(activeOnly));
      }
    }

    if (StringUtils.isNotBlank(includeDraft)) {
      params.put("includeDraft", List.of(includeDraft));
    }

    String query =
        params.entrySet().stream()
            .flatMap(e -> e.getValue().stream().map(v -> e.getKey() + "=" + v))
            .reduce((a, b) -> a + "&" + b)
            .orElse("");
    return URI.create(expandValueSetUri + (query.isEmpty() ? "" : "?" + query));
  }
}
