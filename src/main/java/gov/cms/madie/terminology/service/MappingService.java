package gov.cms.madie.terminology.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.models.mapping.CodeSystemEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MappingService {
  @Value("${mapping.data.code-system-entry-url}")
  private String codeSystemEntryUrl;

  private final ObjectMapper objectMapper;

  public List<CodeSystemEntry> getCodeSystemEntries() {
    try {
      CodeSystemEntry[] data =
          objectMapper.readValue(new URL(codeSystemEntryUrl), CodeSystemEntry[].class);
      if (data != null) {
        return Arrays.asList(data);
      }
    } catch (IOException ioException) {
      throw new RuntimeException(
          "Error while accessing code system entry mapping document", ioException);
    }
    return Collections.emptyList();
  }
}
