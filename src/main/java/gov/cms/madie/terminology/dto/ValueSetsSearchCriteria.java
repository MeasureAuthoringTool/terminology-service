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
  private Boolean includeExpansion;

  @Data
  @AllArgsConstructor
  @Builder(toBuilder = true)
  @NoArgsConstructor
  public static class ValueSetParams {
    private String oid;
    private String url;
    private String release;
    private Integer count;
    private Integer offset;
  }
}
