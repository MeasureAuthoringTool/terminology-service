package gov.cms.madie.terminology.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.terminology.exceptions.VsacBatchValueSetExpansionException;
import gov.cms.madie.terminology.exceptions.VsacParseBatchValueSetExpansionException;
import gov.cms.madie.terminology.exceptions.ValueSetExpansionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import gov.cms.madie.terminology.exceptions.CodeSystemNotFoundException;
import gov.cms.madie.terminology.exceptions.DuplicateCodeSystemException;
import gov.cms.madie.terminology.exceptions.ResourceNotFoundException;
import gov.cms.madie.terminology.exceptions.ValueSetNotFoundException;
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
        ex.getStatusCode().value(),
        ex.getLocalizedMessage(),
        ex.getResponseBodyAsString());
    Map<String, String> validationErrors = new HashMap<>();
    validationErrors.put(request.getContextPath(), ex.getLocalizedMessage());

    Map<String, Object> errorAttributes =
        getErrorAttributes(request, HttpStatus.valueOf(ex.getStatusCode().value()));
    errorAttributes.put("validationErrors", validationErrors);

    return ResponseEntity.status(ex.getStatusCode().value())
        .contentType(MediaType.APPLICATION_JSON)
        .body(errorAttributes);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onMethodArgumentNotValidException(
      MethodArgumentNotValidException ex, WebRequest request) {
    Map<String, String> validationErrors = new HashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(fe -> validationErrors.put(fe.getField(), fe.getDefaultMessage()));
    Map<String, Object> errorAttributes = getErrorAttributes(request, HttpStatus.BAD_REQUEST);
    errorAttributes.put("validationErrors", validationErrors);
    return errorAttributes;
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

  @ExceptionHandler(ValueSetExpansionException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onValueSetExpansionException(
      ValueSetExpansionException ex, WebRequest request) {
    IParser parser = fhirContext.newJsonParser();
    OperationOutcome outcome = parser.parseResource(OperationOutcome.class, ex.getBody());

    Map<String, Object> errorAttributes =
        getErrorAttributes(request, HttpStatus.valueOf(ex.getStatusCode().value()));

    errorAttributes.put("diagnostic", outcome.getIssueFirstRep().getDiagnostics());
    errorAttributes.put(
        "valueSet",
        ex.getValueSetUri()
            .substring("ValueSet/".length() + 1, ex.getValueSetUri().lastIndexOf("/$")));
    if (ex.getFilter().equalsIgnoreCase("manifest") && ex.getValueSetUri().contains("Library/")) {
      errorAttributes.put(
          "manifest",
          ex.getValueSetUri()
              .substring(ex.getValueSetUri().lastIndexOf("Library/") + "Library/".length()));
    }

    return errorAttributes;
  }

  @ExceptionHandler(VsacBatchValueSetExpansionException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onVsacBatchValueSetExpansionException(
      VsacBatchValueSetExpansionException ex, WebRequest request) {
    return getErrorAttributes(request, HttpStatus.valueOf(ex.getStatusCode().value()));
  }

  @ExceptionHandler(VsacParseBatchValueSetExpansionException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onVsacParseBatchValueSetExpansionException(
      VsacParseBatchValueSetExpansionException ex, WebRequest request) {
    Map<String, String> validationErrors = new HashMap<>();
    validationErrors.put(request.getContextPath(), ex.getMessage());

    Map<String, Object> errorAttributes = getErrorAttributes(request, HttpStatus.BAD_REQUEST);
    errorAttributes.put("validationErrors", validationErrors);

    OperationOutcome outcome = ex.getOperationOutcome();

    errorAttributes.put("manifestExpansionFullUrl", ex.getManifestExpansionFullUrl());
    errorAttributes.put("valueSetOid", ex.getOid());
    if (outcome == null) {
      errorAttributes.forEach((key, value) -> log.info("{}:{}", key, value));
      return errorAttributes;
    }
    errorAttributes.put("diagnostic", outcome.getIssueFirstRep().getDiagnostics());
    errorAttributes.forEach((key, value) -> log.info("{}:{}", key, value));
    return errorAttributes;
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ResponseBody
  Map<String, Object> onResourceNotFoundException(
      ResourceNotFoundException ex, WebRequest request) {
    IParser parser = fhirContext.newJsonParser();
    OperationOutcome outcome1 = parser.parseResource(OperationOutcome.class, ex.getBody());

    Map<String, Object> errorAttributes1 =
        getErrorAttributes(request, HttpStatus.valueOf(ex.getStatusCode().value()));

    errorAttributes1.put("diagnostic", outcome1.getIssueFirstRep().getDiagnostics());
    return errorAttributes1;
  }

  @ExceptionHandler(DuplicateCodeSystemException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onDuplicateCodeSystemException(
      DuplicateCodeSystemException ex, WebRequest request) {
    log.warn("Duplicate CodeSystem exception: {}", ex.getMessage());
    Map<String, String> validationErrors = new HashMap<>();
    validationErrors.put(request.getContextPath(), ex.getMessage());
    Map<String, Object> errorAttributes = getErrorAttributes(request, HttpStatus.BAD_REQUEST);
    errorAttributes.put("validationErrors", validationErrors);
    return errorAttributes;
  }

  @ExceptionHandler(CodeSystemNotFoundException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ResponseBody
  Map<String, Object> onCodeSystemNotFoundException(
      CodeSystemNotFoundException ex, WebRequest request) {
    log.warn("CodeSystem not found exception: {}", ex.getMessage());
    Map<String, String> validationErrors = new HashMap<>();
    validationErrors.put(request.getContextPath(), ex.getMessage());
    Map<String, Object> errorAttributes = getErrorAttributes(request, HttpStatus.NOT_FOUND);
    errorAttributes.put("validationErrors", validationErrors);
    return errorAttributes;
  }

  @ExceptionHandler(ValueSetNotFoundException.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ResponseBody
  Map<String, Object> onValueSetNotFoundException(
      ValueSetNotFoundException ex, WebRequest request) {
    log.warn("ValueSet not found exception: {}", ex.getMessage());
    Map<String, String> validationErrors = new HashMap<>();
    validationErrors.put(request.getContextPath(), ex.getMessage());
    Map<String, Object> errorAttributes = getErrorAttributes(request, HttpStatus.NOT_FOUND);
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
