package gov.cms.madie.terminology.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValueSetDisplayForAdmin {
  private String id;

  @NotBlank(message = "Value set URL is required")
  private String url;

  private String version;
  private Instant lastUpdated;
  private boolean manuallyModified;

  @NotBlank(message = "Value set expansion JSON is required")
  private String valueSet;
}
