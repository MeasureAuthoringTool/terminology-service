package gov.cms.madie.terminology.exceptions;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.Charset;

@Getter
@Setter
public class VsacBatchValueSetExpansionException extends WebClientResponseException {
  public VsacBatchValueSetExpansionException(
      String message, HttpStatusCode status, String statusText, String body) {
    super(message, status, statusText, null, body.getBytes(), Charset.defaultCharset(), null);
  }
}
