package gov.cms.madie.terminology.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Code {
  private String name;
  private String display;
  private String version;
  private String codeSystem;
  private String codeSystemOid;
  private boolean active;
}