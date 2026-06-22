package gov.cms.madie.terminology.models;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor
@Document
public class CodeSystem {
  @Id private String id;
  @NotBlank private String fullUrl;
  private String title;
  @NotBlank private String name;
  @NotNull @Valid private Version version;
  private String versionId;
  @NotBlank private String oid; // identifier[0].value oid of identifier List
  private Instant lastUpdated; // when queried
  private Date lastUpdatedUpstream; // when was resource last updated on vsac end

  @JsonProperty("isLatestVersion")
  private boolean isLatestVersion;

  public boolean isFhir() {
    return version != null && StringUtils.isNotBlank(version.getFhirVersion()) && fullUrl != null;
  }

  public boolean isQdm() {
    return version != null && StringUtils.isNotBlank(version.getVsacVersion()) && oid != null;
  }

  public boolean isVsacSearchable() {
    return isFhir() && !oid.contains("NOT.IN.VSAC") || isQdm();
  }

  @Data
  @AllArgsConstructor
  @Builder(toBuilder = true)
  @NoArgsConstructor
  public static class Version {
    @NotBlank private String fhirVersion;
    private String vsacVersion;
  }
}
