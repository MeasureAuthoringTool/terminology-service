package gov.cms.madie.terminology.controller;

import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.Code;
import gov.cms.madie.terminology.dto.QdmValueSet;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import gov.cms.madie.terminology.service.VsacService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "/terminology")
@Slf4j
@RequiredArgsConstructor
public class VsacFhirTerminologyController {
  private final FhirTerminologyService fhirTerminologyService;
  private final VsacService vsacService;

  @GetMapping("/manifest-list")
  public ResponseEntity<List<ManifestExpansion>> getManifests(Principal principal) {
    final String username = principal.getName();
    log.info("Retrieving List of available manifests, requested by HARP ID : [{}}]", username);
    UmlsUser umlsUser = vsacService.verifyUmlsAccess(username);
    return ResponseEntity.ok().body(fhirTerminologyService.getManifests(umlsUser));
  }

  @PutMapping("/value-sets/expansion/qdm")
  public ResponseEntity<List<QdmValueSet>> getValueSetsExpansions(
      Principal principal, @RequestBody ValueSetsSearchCriteria searchCriteria) {
    final String username = principal.getName();
    log.info(
        "User [{}] is attempting to fetch value sets expansions from VSAC FHIR Terminology Server.",
        username);
    UmlsUser umlsUser = vsacService.verifyUmlsAccess(username);
    List<QdmValueSet> qdmValueSets =
        fhirTerminologyService.getValueSetsExpansionsForQdm(searchCriteria, umlsUser);
    return ResponseEntity.ok().body(qdmValueSets);
  }

  @GetMapping(path = "/update-code-systems", produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<List<CodeSystem>> retrieveAndUpdateCodeSystems(
      Principal principal,
      HttpServletRequest request,
      @Value("${code-system-refresh-task.admin-api-key}") String apiKey,
      @RequestHeader("Authorization") String accessToken) {
    final String username = principal.getName();
    UmlsUser umlsUser = vsacService.verifyUmlsAccess(username);
    return ResponseEntity.ok().body(fhirTerminologyService.retrieveAllCodeSystems(umlsUser));
  }

  @GetMapping(path = "/get-code-systems", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<CodeSystem>> getAllCodeSystems(Principal principal) {
    final String username = principal.getName();
    log.info("Retrieving list of codeSystems for user: {}", username);
    return ResponseEntity.ok().body(fhirTerminologyService.getAllCodeSystems());
  }

  @GetMapping(path = "/code", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Code> getCode(
      @RequestParam() String code,
      @RequestParam() String codeSystem,
      @RequestParam() String version,
      Principal principal) {
    final String username = principal.getName();
    log.info(
        "User {} is performing code retrieve for code: {}, system: {} and version: {}",
        username,
        code,
        codeSystem,
        version);
    UmlsUser user = vsacService.verifyUmlsAccess(username);
    return ResponseEntity.ok()
        .body(fhirTerminologyService.retrieveCode(code, codeSystem, version, user.getApiKey()));
  }

  @PostMapping(path = "/codesList", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Code>> getCodesList(
      @RequestBody() List<Map<String, String>> codeList, Principal principal) {
    final String username = principal.getName();
    UmlsUser user = vsacService.verifyUmlsAccess(username);
    return ResponseEntity.ok()
        .body(fhirTerminologyService.retrieveCodesList(codeList, user.getApiKey()));
  }
}
