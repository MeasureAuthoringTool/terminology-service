package cms.gov.madie.terminology.util;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TerminologyServiceUtil {

  public static URI buildRetrieveMultipleValueSetsUri(
      String baseUrl,
      String valuesetEndpoint,
      String oid,
      String serviceTicket,
      String profile,
      String includeDraft,
      String release,
      String version) {
    Map<String, String> params = new HashMap<>();
    String url = baseUrl + valuesetEndpoint;
    params.put("oid", oid);
    params.put("st", serviceTicket);
    if (StringUtils.hasLength(profile)) {
      params.put("profile", profile);
    } else {
      params.put("profile", "Most Recent Code System Versions in VSAC");
    }
    if (StringUtils.hasLength(includeDraft)) {
      params.put("includeDraft", includeDraft);
    } else {
      params.put("includeDraft", "yes");
    }
    if (StringUtils.hasLength(release)) {
      params.put("release", release);
      url += "&release={release}";
    }
    if (StringUtils.hasLength(version)) {
      params.put("version", version);
      url += "&version={version}";
    }
    log.debug("RetrieveMultipleValueSetsUri = " + url);
    return UriComponentsBuilder.fromUriString(url).buildAndExpand(params).encode().toUri();
  }
}
