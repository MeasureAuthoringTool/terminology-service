package gov.cms.madie.terminology.controller;

import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.service.ValueSetExpansionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/terminology")
@Slf4j
@RequiredArgsConstructor
public class ValueSetExpansionController {

  private final ValueSetExpansionService vses;

  @GetMapping("/ValueSet")
  public ResponseEntity<String> expandValueSet(
      @RequestParam String url, @RequestParam(required = false) String version) {
    log.info("Expanding ValueSet with URL: {} and version: {}", url, version);
    return ResponseEntity.ok(vses.getValueSet(url, version).getValueSet());
  }

  @GetMapping("/CodeSystem")
  public ResponseEntity<List<CodeSystem>> expandCodeSystem(
      @RequestParam String url, @RequestParam(required = false) Integer count) {
    log.info("Expanding CodeSystem with URL: {} and count: {}", url, count);
    return ResponseEntity.ok(vses.getCodeSystem(url, count));
  }
}
