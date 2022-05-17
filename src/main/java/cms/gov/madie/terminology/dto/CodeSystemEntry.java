package cms.gov.madie.terminology.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
public class CodeSystemEntry {
  private String oid;
  private String url;
  private String name;
  private List<Version> version;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Version {
    String vsac;
    String fhir;
  }
}
