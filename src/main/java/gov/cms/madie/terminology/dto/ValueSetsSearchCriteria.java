package gov.cms.madie.terminology.dto;

import gov.cms.madie.models.measure.ManifestExpansion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor
public class ValueSetsSearchCriteria {

  private String profile;
  private String includeDraft;
  private String activeOnly;
  private ManifestExpansion manifestExpansion;
  private List<ValueSetParams> valueSetParams;

  @Data
  @AllArgsConstructor
  @Builder
  @NoArgsConstructor
  public static class ValueSetParams {
    private String oid;
    private String release;
    private String version;
    private Integer count;
    private Integer offset;
  }
}
