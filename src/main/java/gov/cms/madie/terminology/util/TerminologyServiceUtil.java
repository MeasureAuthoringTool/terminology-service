package gov.cms.madie.terminology.util;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TerminologyServiceUtil {

  public static final String VSAC_VALUE_SET_URL_PREFIX = "http://cts.nlm.nih.gov/fhir/ValueSet/";
  // The prefix followed by an OID, e.g. http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1
  private static final Pattern VSAC_VALUE_SET_URL_PATTERN =
      Pattern.compile(Pattern.quote(VSAC_VALUE_SET_URL_PREFIX) + "[0-2](\\.(0|[1-9]\\d*))+");
  // Statuses accepted when fetching a ValueSet definition (draft | active | retired | unknown)
  private static final String VALUE_SET_DEFINITION_STATUSES = "active,draft";

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

  public static URI buildRetrieveCodeSystemsHistoryUri(
      String baseUrl, String codeSystemId, Integer offset, Integer count) {
    return UriComponentsBuilder.fromUriString(baseUrl)
        .pathSegment(codeSystemId, "_history")
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

  /**
   * A valid url is VSAC_VALUE_SET_URL_PREFIX followed by an OID and nothing else, so a "|version"
   * suffix, a version keyword, or a different domain or structure is rejected.
   */
  public static boolean isValidValueSetUrl(String url) {
    return url != null && VSAC_VALUE_SET_URL_PATTERN.matcher(url).matches();
  }

  /**
   * Builds a relative URI that searches for a ValueSet definition without expansion, e.g.
   * /ValueSet?status=active,draft&url={url}. Paging does not apply here.
   */
  public static URI buildValueSetSearchUri(ValueSetsSearchCriteria.ValueSetParams valueSetParams) {
    if (valueSetParams == null || StringUtils.isBlank(valueSetParams.getUrl())) {
      return null;
    }
    Map<String, String> queryParams = new LinkedHashMap<>();
    queryParams.put("status", VALUE_SET_DEFINITION_STATUSES);
    queryParams.put("url", valueSetParams.getUrl());
    return buildRelativeUri("/ValueSet", queryParams);
  }

  /**
   * Builds a relative URI for the VSAC $expand operation, e.g.
   * /ValueSet/$expand?url={url}&includeDraft=true&activeOnly=true&manifest={manifest}. A
   * valueSetVersion is never sent.
   */
  public static URI buildValueSetExpansionUri(
      ValueSetsSearchCriteria.ValueSetParams valueSetParams,
      String profile,
      String includeDraft,
      String activeOnly,
      ManifestExpansion manifestExpansion) {
    if (valueSetParams == null || StringUtils.isBlank(valueSetParams.getUrl())) {
      return null;
    }
    Map<String, String> queryParams = new LinkedHashMap<>();
    queryParams.put("url", valueSetParams.getUrl());
    if (StringUtils.isNotBlank(includeDraft)) {
      queryParams.put("includeDraft", includeDraft);
    }
    if (StringUtils.isNotBlank(activeOnly)) {
      queryParams.put("activeOnly", activeOnly);
    }
    if (manifestExpansion != null && StringUtils.isNotBlank(manifestExpansion.getFullUrl())) {
      queryParams.put("manifest", manifestExpansion.getFullUrl());
    }
    if (valueSetParams.getOffset() != null && valueSetParams.getOffset() >= 0) {
      queryParams.put("offset", String.valueOf(valueSetParams.getOffset()));
    }
    if (valueSetParams.getCount() != null && valueSetParams.getCount() >= 0) {
      queryParams.put("count", String.valueOf(valueSetParams.getCount()));
    }
    return buildRelativeUri("/ValueSet/$expand", queryParams);
  }

  // Encodes only characters that are invalid in a query value (e.g. "|"), so urls stay readable
  private static URI buildRelativeUri(String path, Map<String, String> queryParams) {
    String query =
        queryParams.entrySet().stream()
            .map(
                param ->
                    param.getKey()
                        + "="
                        + UriUtils.encodeQueryParam(param.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    return URI.create(path + "?" + query);
  }
}
