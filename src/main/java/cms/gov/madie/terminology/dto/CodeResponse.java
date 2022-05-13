package cms.gov.madie.terminology.dto;

import lombok.Data;

@Data
public class CodeResponse {
  public CodeResponseData data;
  public String message;
  public String status;
}
