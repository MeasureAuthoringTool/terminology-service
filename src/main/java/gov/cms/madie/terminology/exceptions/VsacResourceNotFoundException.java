package gov.cms.madie.terminology.exceptions;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.Charset;

@Getter
@Setter
public class VsacValueSetNotFoundException extends WebClientResponseException {

  private String body;

  private String valueSetUri;

  public VsacValueSetNotFoundException(
      String message, HttpStatusCode status, String statusText, String body, String valueSetUri) {
    super(message, status, statusText, null, body.getBytes(), Charset.defaultCharset(), null);
    this.body = body;

    this.valueSetUri = valueSetUri;
  }
}
