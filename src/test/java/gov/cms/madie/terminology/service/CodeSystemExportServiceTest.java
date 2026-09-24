package gov.cms.madie.terminology.service;

import gov.cms.madie.terminology.config.ExcelExportServiceConfig;
import gov.cms.madie.terminology.dto.CodeSystemExportRequest;
import gov.cms.madie.terminology.dto.CodeSystemExportRow;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeSystemExportServiceTest {

  @Mock private ExcelExportServiceConfig excelExportServiceConfig;
  @Mock private RestTemplate excelExportRestTemplate;
  @Mock private CodeSystemRepository codeSystemRepository;

  @Captor private ArgumentCaptor<String> urlCaptor;
  @Captor private ArgumentCaptor<HttpEntity<CodeSystemExportRequest>> entityCaptor;

  private CodeSystemExportService codeSystemExportService;

  private static final String AUTH = "Bearer test-token";

  @BeforeEach
  void setUp() {
    codeSystemExportService =
        new CodeSystemExportService(
            excelExportServiceConfig, excelExportRestTemplate, codeSystemRepository);
  }

  private CodeSystem codeSystem() {
    return CodeSystem.builder()
        .id("LOINCversion2.40")
        .title("LOINC")
        .name("LOINC")
        .oid("urn:oid:2.16.840.1.113883.6.1")
        .fullUrl("https://loinc.org")
        .version(CodeSystem.Version.builder().fhirVersion("2.40").vsacVersion("2.74").build())
        .versionId("1")
        .isLatestVersion(true)
        .lastUpdated(Instant.parse("2026-01-15T10:30:00Z"))
        .lastUpdatedUpstream(Date.from(Instant.parse("2026-02-20T08:15:00Z")))
        .build();
  }

  @Test
  void buildRowsReturnsEmptyWhenNoCodeSystems() {
    when(codeSystemRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());
    assertThat(codeSystemExportService.buildRows(), is(empty()));
  }

  @Test
  void buildRowsSortsByTitleAscending() {
    when(codeSystemRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());

    codeSystemExportService.buildRows();

    ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
    org.mockito.Mockito.verify(codeSystemRepository).findAll(sortCaptor.capture());
    Sort.Order order = sortCaptor.getValue().getOrderFor("title");
    assertThat(order, notNullValue());
    assertThat(order.getDirection(), is(Sort.Direction.ASC));
  }

  @Test
  void buildRowsMapsAllRetainedFields() {
    when(codeSystemRepository.findAll(any(Sort.class))).thenReturn(List.of(codeSystem()));

    List<CodeSystemExportRow> rows = codeSystemExportService.buildRows();

    assertThat(rows, hasSize(1));
    CodeSystemExportRow row = rows.get(0);
    assertThat(row.getTitle(), is("LOINC"));
    assertThat(row.getName(), is("LOINC"));
    assertThat(row.getOid(), is("urn:oid:2.16.840.1.113883.6.1"));
    assertThat(row.getFullUrl(), is("https://loinc.org"));
    assertThat(row.getFhirVersion(), is("2.40"));
    assertThat(row.getVsacVersion(), is("2.74"));
    assertThat(row.getVersionId(), is("1"));
    assertThat(row.getLatestVersion(), is("Yes"));
    assertThat(row.getLastUpdated(), is("2026-01-15 10:30:00"));
    assertThat(row.getLastUpdatedUpstream(), is("2026-02-20 08:15:00"));
    assertThat(row.getId(), is("LOINCversion2.40"));
  }

  @Test
  void buildRowsHandlesNullVersionDatesAndFalseLatest() {
    CodeSystem codeSystem =
        CodeSystem.builder()
            .id("csId")
            .title("Sparse")
            .name("SPARSE")
            .oid("urn:oid:NOT.IN.VSAC")
            .fullUrl("https://example.org/sparse")
            .version(null)
            .isLatestVersion(false)
            .build();
    when(codeSystemRepository.findAll(any(Sort.class))).thenReturn(List.of(codeSystem));

    CodeSystemExportRow row = codeSystemExportService.buildRows().get(0);

    assertThat(row.getFhirVersion(), is(nullValue()));
    assertThat(row.getVsacVersion(), is(nullValue()));
    assertThat(row.getLatestVersion(), is("No"));
    assertThat(row.getLastUpdated(), is(nullValue()));
    assertThat(row.getLastUpdatedUpstream(), is(nullValue()));
  }

  @Test
  void generateCodeSystemExportSendsRowsAndReturnsBytes() {
    byte[] expectedBytes = "fake-xlsx-bytes".getBytes(StandardCharsets.UTF_8);
    when(codeSystemRepository.findAll(any(Sort.class))).thenReturn(List.of(codeSystem()));
    when(excelExportServiceConfig.getBaseUrl()).thenReturn("http://excel-export:3000/api");
    when(excelExportRestTemplate.exchange(
            urlCaptor.capture(), eq(HttpMethod.PUT), entityCaptor.capture(), eq(byte[].class)))
        .thenReturn(ResponseEntity.ok(expectedBytes));

    byte[] actual = codeSystemExportService.generateCodeSystemExport(AUTH);

    assertThat(actual, is(expectedBytes));
    assertThat(urlCaptor.getValue(), is("http://excel-export:3000/api/excel/code-system-export"));

    HttpEntity<CodeSystemExportRequest> sentEntity = entityCaptor.getValue();
    assertThat(sentEntity.getBody(), notNullValue());
    assertThat(sentEntity.getBody().getRows(), hasSize(1));

    HttpHeaders headers = sentEntity.getHeaders();
    assertThat(headers.getContentType(), is(MediaType.APPLICATION_JSON));
    assertThat(
        headers.getAccept(),
        hasItem(MediaType.parseMediaType(CodeSystemExportService.XLSX_MEDIA_TYPE)));
    assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION), is(AUTH));
  }

  @Test
  void generateCodeSystemExportOmitsAuthHeaderWhenBlank() {
    when(codeSystemRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());
    when(excelExportServiceConfig.getBaseUrl()).thenReturn("http://excel-export:3000/api");
    when(excelExportRestTemplate.exchange(
            anyString(), eq(HttpMethod.PUT), entityCaptor.capture(), eq(byte[].class)))
        .thenReturn(ResponseEntity.ok("bytes".getBytes(StandardCharsets.UTF_8)));

    codeSystemExportService.generateCodeSystemExport(null);

    assertThat(
        entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION), nullValue());
  }

  @Test
  void generateCodeSystemExportThrowsBadGatewayOnNonSuccessResponse() {
    when(codeSystemRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());
    when(excelExportServiceConfig.getBaseUrl()).thenReturn("http://excel-export:3000/api");
    when(excelExportRestTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(byte[].class)))
        .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> codeSystemExportService.generateCodeSystemExport(AUTH));
    assertThat(ex.getStatusCode(), is(HttpStatus.BAD_GATEWAY));
  }

  @Test
  void generateCodeSystemExportThrowsBadGatewayWhenDownstreamUnreachable() {
    when(codeSystemRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());
    when(excelExportServiceConfig.getBaseUrl()).thenReturn("http://excel-export:3000/api");
    when(excelExportRestTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(byte[].class)))
        .thenThrow(new RestClientException("connection refused"));

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> codeSystemExportService.generateCodeSystemExport(AUTH));
    assertThat(ex.getStatusCode(), is(HttpStatus.BAD_GATEWAY));
  }
}
