package cms.gov.madie.terminology.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
public class CqlCode {
  String codeId;
  CqlCodeSystem codeSystem;
  long hits;
  String text;
  String name;
  LineInfo start;
  LineInfo stop;
  boolean isValid;
  String errorMessage;

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class CqlCodeSystem {
    String oid;
    long hits;
    String version;
    String text;
    String name;
    LineInfo start;
    LineInfo stop;
    boolean isValid;
    String errorMessage;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class LineInfo {
    int line;
    int position;
  }
}
