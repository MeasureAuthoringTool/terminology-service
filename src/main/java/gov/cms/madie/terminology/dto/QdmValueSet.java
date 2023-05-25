package gov.cms.madie.terminology.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QdmValueSet {
  private String oid;

  @JsonProperty("display_name")
  private String displayName;

  private String version;
  private List<Concept> concepts;

  @Data
  @Builder
  public static class Concept {
    private String code;

    @JsonProperty("code_system_oid")
    private String codeSystemOid;

    @JsonProperty("code_system_name")
    private String codeSystemName;

    @JsonProperty("code_system_version")
    private String codeSystemVersion;

    @JsonProperty("display_name")
    private String displayName;
  }
}
