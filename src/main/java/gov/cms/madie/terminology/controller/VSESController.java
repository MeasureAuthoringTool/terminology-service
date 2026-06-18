package gov.cms.madie.terminology.controller;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.exceptions.CodeSystemNotFoundException;
import gov.cms.madie.terminology.exceptions.ValueSetNotFoundException;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.service.ValueSetExpansionService;
import gov.cms.madie.terminology.util.FhirBundleUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * This API is to mimic the endpoints of a HAPI FHIR server. They purposefully don't follow MADiE
 * standards so that validations from the Fhir Service can be properly performed
 */
@RestController
@RequestMapping(path = "/terminology")
@Slf4j
@RequiredArgsConstructor
public class VSESController {

  private final ValueSetExpansionService vses;
  private final FhirContext fhirContext;

  @Value("${madie.allowed-hosts}")
  private List<String> allowedHosts;

  @GetMapping("/ValueSet")
  public ResponseEntity<String> expandValueSets(
      @RequestParam String url, @RequestParam(required = false) String version) {
    log.info("Expanding ValueSet with URL: {} and version: {}", url, version);

    // Validate URL format
    if (!isValidFhirTerminologyUrl(url)) {
      throw new ValueSetNotFoundException("Invalid URL format");
    }

    String result = vses.getValueSet(url, version).getValueSet();

    return ResponseEntity.ok()
        .contentType(MediaType.valueOf("application/fhir+json"))
        .body(
            fhirContext
                .newJsonParser()
                .encodeResourceToString(FhirBundleUtil.createValueSetBundle(fhirContext, result)));
  }

  @GetMapping("/CodeSystem")
  public ResponseEntity<String> retrieveCodeSystems(
      @RequestParam String url, @RequestParam(required = false) Integer count) {
    log.info("Expanding CodeSystem with URL: {} and count: {}", url, count);

    // Validate URL format
    if (!isValidFhirTerminologyUrl(url)) {
      throw new CodeSystemNotFoundException("Invalid URL format");
    }

    List<CodeSystem> codeSystems = vses.getCodeSystem(url, count);

    return ResponseEntity.ok()
        .contentType(MediaType.valueOf("application/fhir+json"))
        .body(
            fhirContext
                .newJsonParser()
                .encodeResourceToString(FhirBundleUtil.createCodeSystemBundle(codeSystems)));
  }

  private boolean isValidFhirTerminologyUrl(String url) {
    if (allowedHosts.stream().noneMatch(url::contains)) {
      return false;
    }

    return url.startsWith("http://") || url.startsWith("https://");
  }
}
