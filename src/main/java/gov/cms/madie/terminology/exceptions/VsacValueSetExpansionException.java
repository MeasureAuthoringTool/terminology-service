package gov.cms.madie.terminology.exceptions;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.Charset;

@Getter
@Setter
public class VsacValueSetExpansionException extends WebClientResponseException {

  private String body;
  private String filter;
  private String valueSetUri;

  public VsacValueSetExpansionException(
      String message,
      HttpStatusCode status,
      String statusText,
      String body,
      String filter,
      String valueSetUri) {
    super(message, status, statusText, null, body.getBytes(), Charset.defaultCharset(), null);
    this.body = body;
    this.filter = filter;
    this.valueSetUri = valueSetUri;
  }
}
