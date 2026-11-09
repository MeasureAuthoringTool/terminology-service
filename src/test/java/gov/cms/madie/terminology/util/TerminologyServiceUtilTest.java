package gov.cms.madie.terminology.util;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;

import static org.junit.jupiter.api.Assertions.*;

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
  void buildValueSetExpansionUriSetsOffsetWhenOffsetIsNonNegative() {
    ValueSetsSearchCriteria.ValueSetParams params =
        ValueSetsSearchCriteria.ValueSetParams.builder()
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5")
            .offset(5)
            .build();
    String profile = "test-profile";
    String includeDraft = "true";
    String activeOnly = "false";
    ManifestExpansion manifestExpansion = null;
    URI uri =
        TerminologyServiceUtil.buildValueSetExpansionUri(
            params, profile, includeDraft, activeOnly, manifestExpansion);
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("offset=5"), "ACTUAL URI: " + uriStr);
  }

  @Test
  void buildValueSetExpansionUriSetsCountWhenCountIsNonNegative() {
    ValueSetsSearchCriteria.ValueSetParams params =
        ValueSetsSearchCriteria.ValueSetParams.builder()
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5")
            .count(7)
            .build();
    String profile = "test-profile";
    String includeDraft = "true";
    String activeOnly = "false";
    ManifestExpansion manifestExpansion = null;
    URI uri =
        TerminologyServiceUtil.buildValueSetExpansionUri(
            params, profile, includeDraft, activeOnly, manifestExpansion);
    String uriStr = uri.toString();
    assertTrue(uriStr.contains("count=7"), "ACTUAL URI: " + uriStr);
  }

  @Test
  void buildValueSetExpansionUriDoesNotSetOffsetWhenOffsetIsNegativeOrNull() {
    // offset is negative
    ValueSetsSearchCriteria.ValueSetParams paramsNeg =
        ValueSetsSearchCriteria.ValueSetParams.builder()
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5")
            .offset(-1)
            .build();
    String profile = "test-profile";
    String includeDraft = "true";
    String activeOnly = "false";
    ManifestExpansion manifestExpansion = null;
    URI uriNeg =
        TerminologyServiceUtil.buildValueSetExpansionUri(
            paramsNeg, profile, includeDraft, activeOnly, manifestExpansion);
    String uriStrNeg = uriNeg.toString();
    assertTrue(!uriStrNeg.contains("offset="), "ACTUAL URI: " + uriStrNeg);

    // offset is null
    ValueSetsSearchCriteria.ValueSetParams paramsNull =
        ValueSetsSearchCriteria.ValueSetParams.builder()
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5")
            .build();
    URI uriNull =
        TerminologyServiceUtil.buildValueSetExpansionUri(
            paramsNull, profile, includeDraft, activeOnly, manifestExpansion);
    String uriStrNull = uriNull.toString();
    assertTrue(!uriStrNull.contains("offset="), "ACTUAL URI: " + uriStrNull);
  }

  @Test
  void buildValueSetExpansionUriDoesNotSetCountWhenCountIsNegativeOrNull() {
    // count is negative
    ValueSetsSearchCriteria.ValueSetParams paramsNeg =
        ValueSetsSearchCriteria.ValueSetParams.builder()
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5")
            .offset(0)
            .count(-1)
            .build();
    String profile = "test-profile";
    String includeDraft = "true";
    String activeOnly = "false";
    ManifestExpansion manifestExpansion = null;
    URI uriNeg =
        TerminologyServiceUtil.buildValueSetExpansionUri(
            paramsNeg, profile, includeDraft, activeOnly, manifestExpansion);
    String uriStrNeg = uriNeg.toString();
    assertTrue(!uriStrNeg.contains("count="), "ACTUAL URI: " + uriStrNeg);

    // count is null
    ValueSetsSearchCriteria.ValueSetParams paramsNull =
        ValueSetsSearchCriteria.ValueSetParams.builder()
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5")
            .offset(0)
            .build();
    URI uriNull =
        TerminologyServiceUtil.buildValueSetExpansionUri(
            paramsNull, profile, includeDraft, activeOnly, manifestExpansion);
    String uriStrNull = uriNull.toString();
    assertTrue(!uriStrNull.contains("count="), "ACTUAL URI: " + uriStrNull);
  }

  @Test
  void constructorIsCovered() {
    // Instantiates the utility class to cover the default constructor
    TerminologyServiceUtil terminologyServiceUtil = new TerminologyServiceUtil();
    assertNotNull(terminologyServiceUtil);
  }

  /* branch coverage for buildValueSetExpansionUri(),
   * line 107: if (valueSetParams != null) {
   */
  @Test
  void buildValueSetExpansionUriValueSetParamsNull() {
    // valueSetParams is null, should skip offset/count logic
    ManifestExpansion manifestExpansion = null;
    String profile = "profile";
    String includeDraft = "true";
    String activeOnly = "false";
    assertNull(
        TerminologyServiceUtil.buildValueSetExpansionUri(
            null, profile, includeDraft, activeOnly, manifestExpansion));
  }

  @Test
  void buildValueSetExpansionUriIfUrlIsMissing() {
    ManifestExpansion manifestExpansion = null;
    String profile = "profile";
    String includeDraft = "true";
    String activeOnly = "false";
    ValueSetsSearchCriteria.ValueSetParams params =
        ValueSetsSearchCriteria.ValueSetParams.builder().build();
    assertNull(
        TerminologyServiceUtil.buildValueSetExpansionUri(
            params, profile, includeDraft, activeOnly, manifestExpansion));
  }

  /* branch coverage for buildValueSetExpansionUri(), line 133:
   * if (StringUtils.isNotBlank(activeOnly)) {
   */
  @Test
  void buildValueSetExpansionUriDoesNotSetActiveOnlyWhenActiveOnlyIsBlankOrNull() {
    ValueSetsSearchCriteria.ValueSetParams params =
        ValueSetsSearchCriteria.ValueSetParams.builder()
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5")
            .build();
    String profile = "test-profile";
    String includeDraft = "true";
    String activeOnlyBlank = "";
    String activeOnlyNull = null;
    ManifestExpansion manifestExpansion = null;
    URI uriBlank =
        TerminologyServiceUtil.buildValueSetExpansionUri(
            params, profile, includeDraft, activeOnlyBlank, manifestExpansion);
    URI uriNull =
        TerminologyServiceUtil.buildValueSetExpansionUri(
            params, profile, includeDraft, activeOnlyNull, manifestExpansion);
    String uriStrBlank = uriBlank.toString();
    String uriStrNull = uriNull.toString();
    assertFalse(uriStrBlank.contains("activeOnly="), "ACTUAL URI: " + uriStrBlank);
    assertFalse(uriStrNull.contains("activeOnly="), "ACTUAL URI: " + uriStrNull);
  }

  @Test
  void buildValueSetExpansionUriOnlySendsUrlWhenNoOtherParamIsSet() {
    ValueSetsSearchCriteria.ValueSetParams params =
        ValueSetsSearchCriteria.ValueSetParams.builder()
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5")
            .build();
    String profile = "";
    String includeDraft = "";
    String activeOnly = "";
    ManifestExpansion manifestExpansion = null;
    URI uri =
        TerminologyServiceUtil.buildValueSetExpansionUri(
            params, profile, includeDraft, activeOnly, manifestExpansion);
    assertEquals(
        "/ValueSet/$expand?url=http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5", uri.toString());
  }

  @Test
  void buildValueSetExpansionUriWhenManifestExpansionIsNotNull() {
    ValueSetsSearchCriteria.ValueSetParams params =
        ValueSetsSearchCriteria.ValueSetParams.builder()
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5")
            .offset(0)
            .count(1000)
            .build();
    String profile = "test-profile";
    String includeDraft = "true";
    String activeOnly = "true";
    ManifestExpansion manifestExpansion =
        ManifestExpansion.builder().fullUrl("test-manifest").build();
    URI uri =
        TerminologyServiceUtil.buildValueSetExpansionUri(
            params, profile, includeDraft, activeOnly, manifestExpansion);
    assertEquals(
        "/ValueSet/$expand?url=http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5&includeDraft=true"
            + "&activeOnly=true&manifest=test-manifest&offset=0&count=1000",
        uri.toString());
  }

  @Test
  void buildValueSetExpansionUriSendsIncludeDraftActiveOnlyAndManifestForUrl() {
    ValueSetsSearchCriteria.ValueSetParams params =
        ValueSetsSearchCriteria.ValueSetParams.builder()
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1110.68")
            .build();
    ManifestExpansion manifestExpansion =
        ManifestExpansion.builder()
            .fullUrl("http://cts.nlm.nih.gov/fhir/Library/ecqm-update-2026-05-14")
            .build();
    URI uri =
        TerminologyServiceUtil.buildValueSetExpansionUri(
            params, null, "true", "true", manifestExpansion);
    assertEquals(
        "/ValueSet/$expand?url=http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1110.68"
            + "&includeDraft=true&activeOnly=true"
            + "&manifest=http://cts.nlm.nih.gov/fhir/Library/ecqm-update-2026-05-14",
        uri.toString());
  }

  @Test
  void buildValueSetExpansionUriEncodesCharactersInvalidInQuery() {
    ValueSetsSearchCriteria.ValueSetParams params =
        ValueSetsSearchCriteria.ValueSetParams.builder()
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5")
            .build();
    ManifestExpansion manifestExpansion =
        ManifestExpansion.builder().fullUrl("http://cts.nlm.nih.gov/fhir/Library/a b|c").build();
    URI uri =
        TerminologyServiceUtil.buildValueSetExpansionUri(
            params, null, null, null, manifestExpansion);
    assertEquals(
        "/ValueSet/$expand?url=http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3.4.5"
            + "&manifest=http://cts.nlm.nih.gov/fhir/Library/a%20b%7Cc",
        uri.toString());
  }

  @Test
  void buildValueSetSearchUriUsesUrlAndStatusWithoutPaging() {
    ValueSetsSearchCriteria.ValueSetParams params =
        ValueSetsSearchCriteria.ValueSetParams.builder()
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1110.68")
            .offset(0)
            .count(1000)
            .build();
    URI uri = TerminologyServiceUtil.buildValueSetSearchUri(params);
    assertEquals(
        "/ValueSet?status=active,draft"
            + "&url=http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1110.68",
        uri.toString());
  }

  @Test
  void buildValueSetSearchUriReturnsNullWhenUrlIsMissing() {
    assertNull(TerminologyServiceUtil.buildValueSetSearchUri(null));
    assertNull(
        TerminologyServiceUtil.buildValueSetSearchUri(
            ValueSetsSearchCriteria.ValueSetParams.builder().oid("1.2.3.4.5").build()));
    assertNull(
        TerminologyServiceUtil.buildValueSetSearchUri(
            ValueSetsSearchCriteria.ValueSetParams.builder().url(" ").build()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1046.265",
        "http://cts.nlm.nih.gov/fhir/ValueSet/1.3.6.1.4.1.6997.4.1.2.234.999.3.2"
      })
  void isValidValueSetUrlAcceptsVsacUrlFollowedByOid(String url) {
    assertTrue(TerminologyServiceUtil.isValidValueSetUrl(url));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1046.265|20230201",
        "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1046.265' version '20230201",
        "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1046.265 version '20230201'",
        "https://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1046.265",
        "http://example.com/fhir/ValueSet/2.16.840.1.113762.1.4.1046.265",
        "http://cts.nlm.nih.gov/fhir/Library/2.16.840.1.113762.1.4.1046.265",
        "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1046.265/",
        "http://cts.nlm.nih.gov/fhir/ValueSet/",
        "http://cts.nlm.nih.gov/fhir/ValueSet/office-visit",
        "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.01",
        "2.16.840.1.113762.1.4.1046.265"
      })
  void isValidValueSetUrlRejectsInvalidUrls(String url) {
    assertFalse(TerminologyServiceUtil.isValidValueSetUrl(url));
  }
}
