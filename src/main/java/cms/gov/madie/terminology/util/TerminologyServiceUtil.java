package cms.gov.madie.terminology.util;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.springframework.web.util.UriComponentsBuilder;

import com.nimbusds.oauth2.sdk.util.StringUtils;

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
    log.info("RetrieveMultipleValueSetsUri = " + url);
    return UriComponentsBuilder.fromUriString(url).buildAndExpand(params).encode().toUri();
  }
}
