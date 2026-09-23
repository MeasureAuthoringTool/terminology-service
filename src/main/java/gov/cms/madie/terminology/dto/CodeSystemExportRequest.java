package gov.cms.madie.terminology.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body sent to the excel-export service's {@code PUT /excel/code-system-export} endpoint.
 * Matches the downstream {@code GenerateCodeSystemExportDto} contract: {@code { "rows": [...] }}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeSystemExportRequest {
  private List<CodeSystemExportRow> rows;
}
