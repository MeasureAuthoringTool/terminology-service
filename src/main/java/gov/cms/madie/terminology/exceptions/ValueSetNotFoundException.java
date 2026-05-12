package gov.cms.madie.terminology.exceptions;

public class ValueSetNotFoundException extends RuntimeException {
  public ValueSetNotFoundException(String message) {
    super(message);
  }
}
