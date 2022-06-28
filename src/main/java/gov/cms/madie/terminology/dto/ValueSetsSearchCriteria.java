package gov.cms.madie.terminology.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
public class ValueSetsSearchCriteria {

  private String profile;
  private String includeDraft;
  private List<ValueSetParams> valueSetParams;

  @Data
  public static class ValueSetParams {
    private String oid;
    private String release;
    private String version;
  }
}
