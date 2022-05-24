package cms.gov.madie.terminology.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okta.commons.lang.Collections;
import gov.cms.madiejavamodels.mappingData.CodeSystemEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
            .url("http://terminology.hl7.org/CodeSystem/v3-ActPriority")
            .version(Collections.toList(version))
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
}
