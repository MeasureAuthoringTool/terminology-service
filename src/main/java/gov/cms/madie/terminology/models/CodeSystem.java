package gov.cms.madie.terminology.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Date;

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
  private String fullUrl;
  private String title;
  private String name;
  private Version version;
  private String versionId;
  private String oid; // identifier[0].value oid of identifier List
  private Instant lastUpdated; // when queried
  private Date lastUpdatedUpstream; // when was resource last updated on vsac end
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
  @Builder(toBuilder = true)
  public static class Version {
    private String fhirVersion;
    private String vsacVersion;
  }
}
