package cms.gov.madie.terminology.controller;

import java.util.HashMap;
import java.util.Map;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@ControllerAdvice
@Slf4j
public class VsacControllerAdvice {

  private final ErrorAttributes errorAttributes;

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
