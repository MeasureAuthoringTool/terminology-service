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
    return input.replaceAll("'", "");
  }

  // Todo need to consider pagination, find a valueSet with lot of codes
  public static URI buildValueSetResourceUri(
      ValueSetsSearchCriteria.ValueSetParams valueSetParams,
      String profile,
      String includeDraft,
      ManifestExpansion manifestExpansion) {
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    String expandValueSetUri = "/ValueSet/" + valueSetParams.getOid() + "/$expand";
    if (StringUtils.isNotBlank(valueSetParams.getVersion())) {
      params.put("valueSetVersion", List.of(valueSetParams.getVersion()));
    } else if (manifestExpansion != null
        && StringUtils.isNotBlank(manifestExpansion.getFullUrl())) {
      params.put("manifest", List.of(manifestExpansion.getFullUrl()));
    }
    return UriComponentsBuilder.fromPath(expandValueSetUri).queryParams(params).build().toUri();
  }
}
