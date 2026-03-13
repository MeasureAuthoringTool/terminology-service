package gov.cms.madie.terminology.exceptions;

public class CodeSystemNotFoundException extends RuntimeException {
  public CodeSystemNotFoundException(String message) {
    super(message);
  }
}
