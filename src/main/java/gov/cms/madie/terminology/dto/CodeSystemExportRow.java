package gov.cms.madie.terminology.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single row of the Code System Export. Field order/names mirror the excel-export service's
 * CodeSystemExportRowDto exactly. All fields are optional strings so the downstream service can lay
 * them out purely by column definition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeSystemExportRow {

  // --- Code System (columns 1-4) ---
  private String title;
  private String name;
  private String oid;
  private String fullUrl;

  // --- Version (columns 5-8) ---
  private String fhirVersion;
  private String vsacVersion;
  private String versionId;
  private String latestVersion;

  // --- Record Details (columns 9-11) ---
  private String lastUpdated;
  private String lastUpdatedUpstream;
  private String id;
}
