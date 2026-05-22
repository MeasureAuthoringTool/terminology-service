package gov.cms.madie.terminology.controller;

import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.service.ValueSetExpansionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URL;
import java.util.List;

@RestController
@RequestMapping(path = "/terminology")
@Slf4j
@RequiredArgsConstructor
public class ValueSetExpansionController {

  private final ValueSetExpansionService vses;

  @Value("${madie.allowed-hosts}")
  private List<String> allowedHosts;

  @GetMapping("/ValueSet")
  public ResponseEntity<String> expandValueSet(
      @RequestParam String url, @RequestParam(required = false) String version) {
    log.info("Expanding ValueSet with URL: {} and version: {}", url, version);

    // Validate URL format
    if (!isValidUrl(url)) {
      throw new IllegalArgumentException("Invalid URL format");
    }

    return ResponseEntity.ok(vses.getValueSet(url, version).getValueSet());
  }

  @GetMapping("/CodeSystem")
  public ResponseEntity<List<CodeSystem>> expandCodeSystem(
      @RequestParam String url, @RequestParam(required = false) Integer count) {
    log.info("Expanding CodeSystem with URL: {} and count: {}", url, count);

    // Validate URL format
    if (!isValidUrl(url)) {
      throw new IllegalArgumentException("Invalid URL format");
    }

    return ResponseEntity.ok(vses.getCodeSystem(url, count));
  }

  private boolean isValidUrl(String url) {
    if (allowedHosts.stream().noneMatch(url::contains)) {
      return false;
    }

    return url.startsWith("http://") || url.startsWith("https://");
  }

}
