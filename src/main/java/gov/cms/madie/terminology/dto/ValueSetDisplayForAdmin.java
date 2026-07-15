package gov.cms.madie.terminology.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ValueSetDisplayForAdmin {
  private String id;
  private String url;
  private Instant lastUpdated;
  private boolean manuallyModified;
}
