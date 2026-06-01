package gov.cms.madie.terminology.controller;

import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.service.ValueSetExpansionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = "/terminology")
@Slf4j
@RequiredArgsConstructor
public class VSESController {

  private final ValueSetExpansionService vses;

  @Value("${madie.allowed-hosts}")
  private List<String> allowedHosts;

  @GetMapping("/value-sets")
  public ResponseEntity<String> expandValueSets(
      @RequestParam String url, @RequestParam(required = false) String version) {
    log.info("Expanding ValueSet with URL: {} and version: {}", url, version);

    // Validate URL format
    if (!isValidFhirTerminologyUrl(url)) {
      throw new IllegalArgumentException("Invalid URL format");
    }

    return ResponseEntity.ok(vses.getValueSet(url, version).getValueSet());
  }

  @GetMapping("/code-systems")
  public ResponseEntity<List<CodeSystem>> retrieveCodeSystems(
      @RequestParam String url, @RequestParam(required = false) Integer count) {
    log.info("Expanding CodeSystem with URL: {} and count: {}", url, count);

    // Validate URL format
    if (!isValidFhirTerminologyUrl(url)) {
      throw new IllegalArgumentException("Invalid URL format");
    }

    return ResponseEntity.ok(vses.getCodeSystem(url, count));
  }

  private boolean isValidFhirTerminologyUrl(String url) {
    if (allowedHosts.stream().noneMatch(url::contains)) {
      return false;
    }

    return url.startsWith("http://") || url.startsWith("https://");
  }
}
