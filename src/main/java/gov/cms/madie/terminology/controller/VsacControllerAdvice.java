package gov.cms.madie.terminology.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.terminology.exceptions.VsacValueSetExpansionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import gov.cms.madie.terminology.exceptions.VsacGenericException;
import gov.cms.madie.terminology.exceptions.VsacUnauthorizedException;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@ControllerAdvice
@Slf4j
public class VsacControllerAdvice {

  private final ErrorAttributes errorAttributes;
  private final FhirContext fhirContext;

  @ExceptionHandler(WebClientResponseException.class)
  public ResponseEntity<Map<String, Object>> handleWebClientResponseException(
      WebClientResponseException ex, WebRequest request) {
    log.error(
        "Error from WebClient - Status {}, Message {}, Body {}",
        ex.getRawStatusCode(),
        ex.getLocalizedMessage(),
        ex.getResponseBodyAsString());
    Map<String, String> validationErrors = new HashMap<>();
    validationErrors.put(request.getContextPath(), ex.getLocalizedMessage());

    Map<String, Object> errorAttributes =
        getErrorAttributes(request, HttpStatus.valueOf(ex.getRawStatusCode()));
    errorAttributes.put("validationErrors", validationErrors);

    return ResponseEntity.status(ex.getRawStatusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(errorAttributes);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onMissingServletRequestParameterException(
      MissingServletRequestParameterException ex, WebRequest request) {
    Map<String, String> validationErrors = new HashMap<>();
    validationErrors.put(request.getContextPath(), ex.getMessage());
    Map<String, Object> errorAttributes = getErrorAttributes(request, HttpStatus.BAD_REQUEST);
    errorAttributes.put("validationErrors", validationErrors);
    return errorAttributes;
  }

  @ExceptionHandler(VsacValueSetExpansionException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ResponseEntity<Map<String, Object>> onVsacValueSetExpansionException(
      VsacValueSetExpansionException ex, WebRequest request) {
    IParser parser = fhirContext.newJsonParser();
    OperationOutcome outcome = parser.parseResource(OperationOutcome.class, ex.getBody());
    String filter = ex.getFilter();
    if (filter.contains("Manifest")) {
      filter += " " + ex.getValueSetUrl().substring(ex.getValueSetUrl().lastIndexOf("Library/"));
    }
    String message =
        String.format(
            "Value Set %s could not be expanded using %s. Per VSAC, \"%s\"\n\n (DEBUG) URL: %s",
            ex.getValueSetUrl()
                .substring("ValueSet/".length() + 1, ex.getValueSetUrl().lastIndexOf("/$")),
            filter,
            outcome.getIssueFirstRep().getDiagnostics(),
            ex.getValueSetUrl());
    return handleWebClientResponseException(
        new WebClientResponseException(
            message, ex.getStatusCode(), ex.getStatusText(), null, null, null, null),
        request);
  }

  @ExceptionHandler(VsacGenericException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onVsacGenericException(VsacGenericException ex, WebRequest request) {
    Map<String, String> validationErrors = new HashMap<>();
    validationErrors.put(request.getContextPath(), ex.getMessage());
    Map<String, Object> errorAttributes = getErrorAttributes(request, HttpStatus.BAD_REQUEST);
    errorAttributes.put("validationErrors", validationErrors);
    return errorAttributes;
  }

  @ExceptionHandler(VsacUnauthorizedException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  @ResponseBody
  Map<String, Object> onVsacUnauthorizedException(
      VsacUnauthorizedException ex, WebRequest request) {
    Map<String, String> validationErrors = new HashMap<>();
    validationErrors.put(request.getContextPath(), ex.getMessage());
    Map<String, Object> errorAttributes = getErrorAttributes(request, HttpStatus.UNAUTHORIZED);
    errorAttributes.put("validationErrors", validationErrors);
    return errorAttributes;
  }

  private Map<String, Object> getErrorAttributes(WebRequest request, HttpStatus httpStatus) {
    ErrorAttributeOptions errorOptions =
        ErrorAttributeOptions.of(ErrorAttributeOptions.Include.MESSAGE);
    Map<String, Object> errorAttributes =
        this.errorAttributes.getErrorAttributes(request, errorOptions);
    errorAttributes.put("status", httpStatus.value());
    errorAttributes.put("error", httpStatus.getReasonPhrase());
    return errorAttributes;
  }
}
