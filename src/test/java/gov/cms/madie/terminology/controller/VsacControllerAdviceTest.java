package gov.cms.madie.terminology.controller;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.exceptions.*;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StubErrorAttributes implements org.springframework.boot.web.servlet.error.ErrorAttributes {
  @Override
  public Map<String, Object> getErrorAttributes(
      WebRequest webRequest, org.springframework.boot.web.error.ErrorAttributeOptions options) {
    return new java.util.HashMap<>(Map.of("foo", "bar"));
  }

  @Override
  public Throwable getError(WebRequest webRequest) {
    return null;
  }
}

class VsacControllerAdviceTest {
  private ErrorAttributes errorAttributes;
  private FhirContext fhirContext;
  private WebRequest webRequest;
  private VsacControllerAdvice advice;

  @BeforeEach
  void setUp() {
    errorAttributes = new StubErrorAttributes();
    fhirContext = FhirContext.forR4();
    webRequest = mock(WebRequest.class);
    when(webRequest.getContextPath()).thenReturn("/test");
    advice = new VsacControllerAdvice(errorAttributes, fhirContext);
  }

  @Test
  void handleWebClientResponseException() {
    WebClientResponseException ex =
        new WebClientResponseException(
            400, "Bad Request", null, "body".getBytes(StandardCharsets.UTF_8), null);
    var resp = advice.handleWebClientResponseException(ex, webRequest);
    assertEquals(400, resp.getStatusCode().value());
    assertTrue(resp.getBody().containsKey("validationErrors"));
  }

  @Test
  void onMissingServletRequestParameterException() {
    MissingServletRequestParameterException ex =
        new MissingServletRequestParameterException("param", "String");
    var resp = advice.onMissingServletRequestParameterException(ex, webRequest);
    assertTrue(resp.containsKey("validationErrors"));
  }

  @Test
  void onValueSetExpansionException_manifestBranch() {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue().setDiagnostics("diag");
    String opOutcomeJson = fhirContext.newJsonParser().encodeResourceToString(outcome);
    ValueSetExpansionException ex =
        new ValueSetExpansionException(
            "msg",
            HttpStatusCode.valueOf(HttpStatus.BAD_REQUEST.value()),
            "bad",
            opOutcomeJson,
            "manifest",
            "ValueSet/Library/test/$");
    var resp = advice.onValueSetExpansionException(ex, webRequest);
    assertEquals("diag", resp.get("diagnostic"));
    assertTrue(resp.containsKey("manifest"));
  }

  @Test
  void onValueSetExpansionException_nonManifestBranch() {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue().setDiagnostics("diag");
    String opOutcomeJson = fhirContext.newJsonParser().encodeResourceToString(outcome);
    ValueSetExpansionException ex =
        new ValueSetExpansionException(
            "msg",
            HttpStatusCode.valueOf(HttpStatus.BAD_REQUEST.value()),
            "bad",
            opOutcomeJson,
            "other",
            "ValueSet/test/$");
    var resp = advice.onValueSetExpansionException(ex, webRequest);
    assertEquals("diag", resp.get("diagnostic"));
    assertFalse(resp.containsKey("manifest"));
  }

  @Test
  void onValueSetExpansionException_filterNotManifest_branch() {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue().setDiagnostics("diag");
    String opOutcomeJson = fhirContext.newJsonParser().encodeResourceToString(outcome);
    ValueSetExpansionException ex =
        new ValueSetExpansionException(
            "msg",
            HttpStatusCode.valueOf(HttpStatus.BAD_REQUEST.value()),
            "bad",
            opOutcomeJson,
            "other",
            "ValueSet/Library/test/$");
    var resp = advice.onValueSetExpansionException(ex, webRequest);
    assertEquals("diag", resp.get("diagnostic"));
    assertFalse(resp.containsKey("manifest"));
  }

  @Test
  void onValueSetExpansionException_uriNotLibrary_branch() {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue().setDiagnostics("diag");
    String opOutcomeJson = fhirContext.newJsonParser().encodeResourceToString(outcome);
    ValueSetExpansionException ex =
        new ValueSetExpansionException(
            "msg",
            HttpStatusCode.valueOf(HttpStatus.BAD_REQUEST.value()),
            "bad",
            opOutcomeJson,
            "manifest",
            "ValueSet/test/$");
    var resp = advice.onValueSetExpansionException(ex, webRequest);
    assertEquals("diag", resp.get("diagnostic"));
    assertFalse(resp.containsKey("manifest"));
  }

  @Test
  void onVsacBatchValueSetExpansionException() {
    VsacBatchValueSetExpansionException ex =
        new VsacBatchValueSetExpansionException(
            "msg", HttpStatusCode.valueOf(HttpStatus.BAD_REQUEST.value()), "bad", "body");
    var resp = advice.onVsacBatchValueSetExpansionException(ex, webRequest);
    assertTrue(resp.containsKey("foo"));
  }

  @Test
  void onResourceNotFoundException() {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue().setDiagnostics("diag");
    String opOutcomeJson = fhirContext.newJsonParser().encodeResourceToString(outcome);
    ResourceNotFoundException ex =
        new ResourceNotFoundException(
            "msg",
            HttpStatusCode.valueOf(HttpStatus.NOT_FOUND.value()),
            "not found",
            opOutcomeJson,
            "ValueSet/test/$");
    var resp = advice.onResourceNotFoundException(ex, webRequest);
    assertEquals("diag", resp.get("diagnostic"));
  }

  @Test
  void onVsacUnauthorizedException() {
    VsacUnauthorizedException ex = new VsacUnauthorizedException("unauth");
    var resp = advice.onVsacUnauthorizedException(ex, webRequest);
    assertTrue(resp.containsKey("validationErrors"));
  }

  @Test
  void onVsacParseBatchValueSetExpansionException_coversBranch() {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue().setDiagnostics("diag");
    String manifestExpansionFullUrl = "http://example.com/manifest";
    String oid = "1.2.3.4.5";
    VsacParseBatchValueSetExpansionException ex =
        new VsacParseBatchValueSetExpansionException(
            "parse error", outcome, manifestExpansionFullUrl, oid);
    var resp = advice.onVsacParseBatchValueSetExpansionException(ex, webRequest);
    assertTrue(resp.containsKey("validationErrors"));
    assertEquals("diag", resp.get("diagnostic"));
    assertEquals(manifestExpansionFullUrl, resp.get("manifestExpansionFullUrl"));
    assertEquals(oid, resp.get("valueSetOid"));
  }

  @Test
  void onVsacParseBatchValueSetExpansionException_handlesNullOutcome() {
    String manifestExpansionFullUrl = "http://example.com/manifest";
    String oid = "1.2.3.4.5";
    VsacParseBatchValueSetExpansionException ex =
        mock(VsacParseBatchValueSetExpansionException.class);
    when(ex.getMessage()).thenReturn("parse error");
    when(ex.getOperationOutcome()).thenReturn(null);
    when(ex.getManifestExpansionFullUrl()).thenReturn(manifestExpansionFullUrl);
    when(ex.getOid()).thenReturn(oid);
    var resp = advice.onVsacParseBatchValueSetExpansionException(ex, webRequest);
    assertTrue(resp.containsKey("validationErrors"));
    assertEquals(manifestExpansionFullUrl, resp.get("manifestExpansionFullUrl"));
    assertEquals(oid, resp.get("valueSetOid"));
    assertFalse(resp.containsKey("diagnostic"));
  }
}
