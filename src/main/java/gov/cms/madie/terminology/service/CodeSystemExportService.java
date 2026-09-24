package gov.cms.madie.terminology.service;

import gov.cms.madie.terminology.config.ExcelExportServiceConfig;
import gov.cms.madie.terminology.dto.CodeSystemExportRequest;
import gov.cms.madie.terminology.dto.CodeSystemExportRow;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * Facilitates the Code System Export: reads every code system retained in the database, flattens
 * them into export rows and delegates .xlsx generation to the downstream excel-export service.
 *
 * <p>This export is a straight DB-to-spreadsheet dump, so there is no concurrent processing.
 */
@Slf4j
@Service
public class CodeSystemExportService {

  /** Media type for the Office Open XML spreadsheet (.xlsx). */
  public static final String XLSX_MEDIA_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private static final String CODE_SYSTEM_EXPORT_PATH = "/excel/code-system-export";

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

  private final ExcelExportServiceConfig excelExportServiceConfig;
  private final RestTemplate excelExportRestTemplate;
  private final CodeSystemRepository codeSystemRepository;

  public CodeSystemExportService(
      ExcelExportServiceConfig excelExportServiceConfig,
      @Qualifier("excelExportRestTemplate") RestTemplate excelExportRestTemplate,
      CodeSystemRepository codeSystemRepository) {
    this.excelExportServiceConfig = excelExportServiceConfig;
    this.excelExportRestTemplate = excelExportRestTemplate;
    this.codeSystemRepository = codeSystemRepository;
  }

  /**
   * Generates the Code System Export workbook by reading all code systems and calling the
   * excel-export service.
   *
   * @param authorizationHeader the caller's {@code Authorization} header to forward downstream (may
   *     be {@code null})
   * @return the generated .xlsx as bytes
   */
  public byte[] generateCodeSystemExport(String authorizationHeader) {
    List<CodeSystemExportRow> rows = buildRows();
    CodeSystemExportRequest requestBody = CodeSystemExportRequest.builder().rows(rows).build();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(List.of(MediaType.parseMediaType(XLSX_MEDIA_TYPE)));

    if (StringUtils.isNotBlank(authorizationHeader)) {
      headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
    }

    String url = excelExportServiceConfig.getBaseUrl() + CODE_SYSTEM_EXPORT_PATH;
    HttpEntity<CodeSystemExportRequest> requestEntity = new HttpEntity<>(requestBody, headers);

    log.info("Requesting Code System Export from excel-export service for {} row(s)", rows.size());
    try {
      ResponseEntity<byte[]> response =
          excelExportRestTemplate.exchange(url, HttpMethod.PUT, requestEntity, byte[].class);
      if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
        log.error(
            "excel-export service returned a non-2xx/empty response: status={}",
            response.getStatusCode());
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "Failed to generate the code system export: excel-export service returned "
                + response.getStatusCode());
      }
      return response.getBody();
    } catch (RestClientException ex) {
      log.error("Error calling excel-export service to generate the code system export", ex);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          "Failed to generate the code system export: unable to reach excel-export service",
          ex);
    }
  }

  /**
   * Reads the full list of code systems retained in the database (sorted by title) and maps each to
   * an export row. Unlike {@code FhirTerminologyService#getAllCodeSystems()}, this intentionally
   * does NOT filter out non-VSAC-searchable code systems: the export is meant to show the complete
   * list, including code systems missing a VSAC version.
   *
   * @return one export row per code system in the database
   */
  public List<CodeSystemExportRow> buildRows() {
    return codeSystemRepository.findAll(Sort.by(Sort.Direction.ASC, "title")).stream()
        .map(this::toRow)
        .toList();
  }

  private CodeSystemExportRow toRow(CodeSystem codeSystem) {
    CodeSystem.Version version = codeSystem.getVersion();
    return CodeSystemExportRow.builder()
        .title(codeSystem.getTitle())
        .name(codeSystem.getName())
        .oid(codeSystem.getOid())
        .fullUrl(codeSystem.getFullUrl())
        .fhirVersion(version == null ? null : version.getFhirVersion())
        .vsacVersion(version == null ? null : version.getVsacVersion())
        .versionId(codeSystem.getVersionId())
        .latestVersion(codeSystem.isLatestVersion() ? "Yes" : "No")
        .lastUpdated(formatInstant(codeSystem.getLastUpdated()))
        .lastUpdatedUpstream(formatDate(codeSystem.getLastUpdatedUpstream()))
        .id(codeSystem.getId())
        .build();
  }

  private String formatInstant(Instant instant) {
    return instant == null ? null : DATE_TIME_FORMATTER.format(instant);
  }

  private String formatDate(Date date) {
    return date == null ? null : DATE_TIME_FORMATTER.format(date.toInstant());
  }
}
