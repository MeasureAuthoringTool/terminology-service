package cms.gov.madie.terminology.service;

import org.springframework.stereotype.Service;

@Service
public class MappingService {
  @Value("${json.data.code-system-entry-url}")
  private String codeSystemEntryUrl;

}
