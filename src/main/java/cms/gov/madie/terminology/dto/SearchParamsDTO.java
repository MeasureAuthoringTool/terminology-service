package cms.gov.madie.terminology.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
public class SearchParamsDTO {

  private String profile;
  private String includeDraft;
  private List<ValueSetParams> valueSetParams;
  // TODO: remove this oce we complete MAT-4203
  private String tgt;

  @Data
  public static class ValueSetParams {
    private String oid;
    private String release;
    private String version;
  }
}
