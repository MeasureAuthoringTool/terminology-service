package gov.cms.madie.terminology.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okta.commons.lang.Collections;
import gov.cms.madie.models.mapping.CodeSystemEntry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class MappingServiceTest {

  @Mock ObjectMapper objectMapper;

  @InjectMocks MappingService mappingService;

  CodeSystemEntry[] codeSystemEntries;

  @BeforeEach
  public void setup() {
    ReflectionTestUtils.setField(mappingService, "codeSystemEntryUrl", "https://Codesystem.com");

    codeSystemEntries = new CodeSystemEntry[1];
    CodeSystemEntry.Version version = new CodeSystemEntry.Version();
    version.setVsac("2.3");
    version.setFhir("2.3");
    var codeSystemEntry =
        CodeSystemEntry.builder()
            .name("ActPriority")
            .oid("1.2.3.4.5.6.7.8.9")
            .url("https://terminology.hl7.org/CodeSystem/v3-ActPriority")
            .versions(Collections.toList(version))
            .build();
    codeSystemEntries[0] = codeSystemEntry;
  }

  @Test
  void getCodeSystemEntries() throws IOException {
    when(objectMapper.readValue(any(URL.class), eq(CodeSystemEntry[].class)))
        .thenReturn(codeSystemEntries);
    List<CodeSystemEntry> response = mappingService.getCodeSystemEntries();
    assertFalse(response.isEmpty());
  }

  @Test
  void getCodeSystemEntriesThrowsException() throws IOException {
    when(objectMapper.readValue(any(URL.class), eq(CodeSystemEntry[].class)))
        .thenThrow(new IOException("Error fetching code system entries"));
    assertThrows(RuntimeException.class, () -> mappingService.getCodeSystemEntries());
  }

  @Test
  void getCodeSystemEntriesDataEmpty() throws IOException {
    when(objectMapper.readValue(any(URL.class), eq(CodeSystemEntry[].class))).thenReturn(null);
    List<CodeSystemEntry> response = mappingService.getCodeSystemEntries();
    assertTrue(response.isEmpty());
  }

  @Test
  void getCodeSystemEntryByOidNotFound() throws IOException {
    when(objectMapper.readValue(any(URL.class), eq(CodeSystemEntry[].class)))
        .thenReturn(codeSystemEntries);
    CodeSystemEntry response = mappingService.getCodeSystemEntryByOid("1.2.3.4.5.6.7.8.0");
    assertNull(response);
  }

  @Test
  void getCodeSystemEntryByOidCodeSystemEntriesNull() throws IOException {
    when(objectMapper.readValue(any(URL.class), eq(CodeSystemEntry[].class))).thenReturn(null);
    CodeSystemEntry response = mappingService.getCodeSystemEntryByOid("1.2.3.4.5.6.7.8.9");
    assertNull(response);
  }

  @Test
  void getCodeSystemEntriesEmptyArray() throws IOException {
    when(objectMapper.readValue(any(URL.class), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[0]);
    List<CodeSystemEntry> response = mappingService.getCodeSystemEntries();
    assertTrue(response.isEmpty());
  }

  @Test
  void getCodeSystemEntryOidNotStartsWithUrnOid() throws IOException {
    // ensure stored entry uses URN OID form to match production behavior
    codeSystemEntries[0].setOid("urn:oid:1.2.3.4.5.6.7.8.9");
    when(objectMapper.readValue(any(URL.class), eq(CodeSystemEntry[].class)))
        .thenReturn(codeSystemEntries);
    CodeSystemEntry response = mappingService.getCodeSystemEntryByOid("urn:oid:1.2.3.4.5.6.7.8.9");
    assertNotNull(response);
    assertEquals("ActPriority", response.getName());
  }

  @Test
  void getCodeSystemEntryStoredUrnInputRawMatches() throws IOException {
    // stored value in URN form should match raw oid input
    codeSystemEntries[0].setOid("urn:oid:1.2.3.4.5.6.7.8.9");
    when(objectMapper.readValue(any(URL.class), eq(CodeSystemEntry[].class)))
        .thenReturn(codeSystemEntries);
    CodeSystemEntry response = mappingService.getCodeSystemEntryByOid("1.2.3.4.5.6.7.8.9");
    assertNotNull(response);
    assertEquals("ActPriority", response.getName());
  }
}
