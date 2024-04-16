package gov.cms.madie.terminology.dto;

public enum CodeStatus {
  ACTIVE("active"),
  INACTIVE("inactive"),
  NA("Not available");

  private final String value;

  CodeStatus(String value) {
    this.value = value;
  }
}
