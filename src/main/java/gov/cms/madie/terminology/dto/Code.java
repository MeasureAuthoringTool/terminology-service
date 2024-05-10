package gov.cms.madie.terminology.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class Code {
  private String name;
  private String display;
  private String version; //'fhir' in the code-system-entry.json
  private String svsVersion; //'vsac' in the code-system-entry.json
  private String codeSystem;
  private String codeSystemOid;
  private CodeStatus status;
}
