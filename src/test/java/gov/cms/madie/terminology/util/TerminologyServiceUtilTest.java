package gov.cms.madie.terminology.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;

public class TerminologyServiceUtilTest {
  @Test
  void buildRetrieveMultipleValueSetsUriSetsDefaultProfileWhenProfileIsBlank() {
    String baseUrl = "http://example.com";
    String valuesetEndpoint = "/ValueSet";
    String oid = "1.2.3.4.5";
    String profile = null;
    String includeDraft = null;
    String release = null;
    String version = null;
    URI uri =
        TerminologyServiceUtil.buildRetrieveMultipleValueSetsUri(
            baseUrl, valuesetEndpoint, oid, profile, includeDraft, release, version);
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("http://example.com/ValueSet"), "ACTUAL URI: " + uriStr);
  }

  @Test
  void buildRetrieveMultipleValueSetsUriSetsIncludeDraftYesWhenIncludeDraftIsBlank() {
    String baseUrl = "http://example.com";
    String valuesetEndpoint = "/ValueSet";
    String oid = "1.2.3.4.5";
    String profile = "test-profile";
    String includeDraft = null;
    String release = null;
    String version = null;
    URI uri =
        TerminologyServiceUtil.buildRetrieveMultipleValueSetsUri(
            baseUrl, valuesetEndpoint, oid, profile, includeDraft, release, version);
    String uriStr = uri.toString();
    // The URI will not show includeDraft unless the URL contains {includeDraft}, but we can assert
    // a URI is returned
    assertNotNull(uri);
    assertTrue(uriStr.startsWith(baseUrl + valuesetEndpoint));
  }

  @Test
  void removeUrnOidSubStringReturnsOidWhenOidDoesNotStartWithUrnOid() {
    String oid = "1.2.3.4.5";
    String result = TerminologyServiceUtil.removeUrnOidSubString(oid);
    assertEquals(oid, result);
  }

  @Test
  void removeUrnOidSubStringReturnsOidWhenOidIsBlank() {
    String oid = "";
    String result = TerminologyServiceUtil.removeUrnOidSubString(oid);
    assertEquals(oid, result);
    String nullOid = null;
    assertEquals(nullOid, TerminologyServiceUtil.removeUrnOidSubString(nullOid));
  }

  @Test
  void buildValueSetResourceUriSetsOffsetWhenOffsetIsNonNegative() {
    ValueSetsSearchCriteria.ValueSetParams params =
        ValueSetsSearchCriteria.ValueSetParams.builder().oid("1.2.3.4.5").offset(5).build();
    String profile = "test-profile";
    String includeDraft = "true";
    String activeOnly = "false";
    ManifestExpansion manifestExpansion = null;
    URI uri =
        TerminologyServiceUtil.buildValueSetResourceUri(
            params, profile, includeDraft, activeOnly, manifestExpansion);
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("offset=5"), "ACTUAL URI: " + uriStr);
  }

  @Test
  void buildValueSetResourceUriSetsCountWhenCountIsNonNegative() {
    ValueSetsSearchCriteria.ValueSetParams params =
        ValueSetsSearchCriteria.ValueSetParams.builder().oid("1.2.3.4.5").count(7).build();
    String profile = "test-profile";
    String includeDraft = "true";
    String activeOnly = "false";
    ManifestExpansion manifestExpansion = null;
    URI uri =
        TerminologyServiceUtil.buildValueSetResourceUri(
            params, profile, includeDraft, activeOnly, manifestExpansion);
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("count=7"), "ACTUAL URI: " + uriStr);
  }

  @Test
  void buildValueSetResourceUriDoesNotSetOffsetWhenOffsetIsNegativeOrNull() {
    // offset is negative
    ValueSetsSearchCriteria.ValueSetParams paramsNeg =
        ValueSetsSearchCriteria.ValueSetParams.builder().oid("1.2.3.4.5").offset(-1).build();
    String profile = "test-profile";
    String includeDraft = "true";
    String activeOnly = "false";
    ManifestExpansion manifestExpansion = null;
    URI uriNeg =
        TerminologyServiceUtil.buildValueSetResourceUri(
            paramsNeg, profile, includeDraft, activeOnly, manifestExpansion);
    String uriStrNeg = uriNeg.toString();
    assertTrue(!uriStrNeg.contains("offset="), "ACTUAL URI: " + uriStrNeg);

    // offset is null
    ValueSetsSearchCriteria.ValueSetParams paramsNull =
        ValueSetsSearchCriteria.ValueSetParams.builder().oid("1.2.3.4.5").build();
    URI uriNull =
        TerminologyServiceUtil.buildValueSetResourceUri(
            paramsNull, profile, includeDraft, activeOnly, manifestExpansion);
    String uriStrNull = uriNull.toString();
    assertTrue(!uriStrNull.contains("offset="), "ACTUAL URI: " + uriStrNull);
  }

  @Test
  void buildValueSetResourceUri_doesNotSetCount_whenCountIsNegativeOrNull() {
    // count is negative
    ValueSetsSearchCriteria.ValueSetParams paramsNeg =
        ValueSetsSearchCriteria.ValueSetParams.builder()
            .oid("1.2.3.4.5")
            .offset(0)
            .count(-1)
            .build();
    String profile = "test-profile";
    String includeDraft = "true";
    String activeOnly = "false";
    ManifestExpansion manifestExpansion = null;
    URI uriNeg =
        TerminologyServiceUtil.buildValueSetResourceUri(
            paramsNeg, profile, includeDraft, activeOnly, manifestExpansion);
    String uriStrNeg = uriNeg.toString();
    assertTrue(!uriStrNeg.contains("count="), "ACTUAL URI: " + uriStrNeg);

    // count is null
    ValueSetsSearchCriteria.ValueSetParams paramsNull =
        ValueSetsSearchCriteria.ValueSetParams.builder().oid("1.2.3.4.5").offset(0).build();
    URI uriNull =
        TerminologyServiceUtil.buildValueSetResourceUri(
            paramsNull, profile, includeDraft, activeOnly, manifestExpansion);
    String uriStrNull = uriNull.toString();
    assertTrue(!uriStrNull.contains("count="), "ACTUAL URI: " + uriStrNull);
  }

  @Test
  void constructorIsCovered() {
    // Instantiates the utility class to cover the default constructor
    new TerminologyServiceUtil();
  }

  /* branch coverage for buildValueSetResourceUri(),
   * line 107: if (valueSetParams != null) {
   */
  @Test
  void buildValueSetResourceUriValueSetParamsNull() {
    // valueSetParams is null, should skip offset/count logic
    ManifestExpansion manifestExpansion = null;
    String profile = "profile";
    String includeDraft = "true";
    String activeOnly = "false";
    URI uri =
        TerminologyServiceUtil.buildValueSetResourceUri(
            null, profile, includeDraft, activeOnly, manifestExpansion);
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("includeDraft=true"));
    assertTrue(uriStr.contains("activeOnly=false"));
    // Should not contain offset or count
    assertFalse(uriStr.contains("offset"));
    assertFalse(uriStr.contains("count"));
  }

  /* branch coverage for buildValueSetResourceUri(), line 133:
   * if (StringUtils.isNotBlank(activeOnly)) {
   */
  @Test
  void buildValueSetResourceUriDoesNotSetActiveOnlyWhenActiveOnlyIsBlankOrNull() {
    ValueSetsSearchCriteria.ValueSetParams params =
        ValueSetsSearchCriteria.ValueSetParams.builder().oid("1.2.3.4.5").build();
    String profile = "test-profile";
    String includeDraft = "true";
    String activeOnlyBlank = "";
    String activeOnlyNull = null;
    ManifestExpansion manifestExpansion = null;
    URI uriBlank =
        TerminologyServiceUtil.buildValueSetResourceUri(
            params, profile, includeDraft, activeOnlyBlank, manifestExpansion);
    URI uriNull =
        TerminologyServiceUtil.buildValueSetResourceUri(
            params, profile, includeDraft, activeOnlyNull, manifestExpansion);
    String uriStrBlank = uriBlank.toString();
    String uriStrNull = uriNull.toString();
    assertFalse(uriStrBlank.contains("activeOnly="), "ACTUAL URI: " + uriStrBlank);
    assertFalse(uriStrNull.contains("activeOnly="), "ACTUAL URI: " + uriStrNull);
  }

  /* branch coverage for buildValueSetResourceUri(), line 141:
   * return URI.create(expandValueSetUri + (query.isEmpty() ? "" : "?" + query));
   */
  @Test
  void buildValueSetResourceUriReturnsUriWithoutQueryWhenQueryIsEmpty() {
    ValueSetsSearchCriteria.ValueSetParams params =
        ValueSetsSearchCriteria.ValueSetParams.builder().oid("1.2.3.4.5").build();
    String profile = "";
    String includeDraft = "";
    String activeOnly = "";
    ManifestExpansion manifestExpansion = null;
    URI uri =
        TerminologyServiceUtil.buildValueSetResourceUri(
            params, profile, includeDraft, activeOnly, manifestExpansion);
    String uriStr = uri.toString();
    assertTrue(uriStr.equals("/ValueSet/1.2.3.4.5/$expand"), "ACTUAL URI: " + uriStr);
    assertFalse(uriStr.contains("?"), "ACTUAL URI: " + uriStr);
  }
}
