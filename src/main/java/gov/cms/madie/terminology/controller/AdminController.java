package gov.cms.madie.terminology.controller;

import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping(path = "/terminology/admin/code-system")
@Slf4j
@RequiredArgsConstructor
public class AdminController {

  private final FhirTerminologyService fhirTerminologyService;

  @PostMapping(
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<CodeSystem> createCodeSystem(
      Principal principal, @Valid @RequestBody CodeSystem codeSystem) {
    log.info(
        "Admin user [{}] is creating a new code system version for name: [{}] oid: [{}] fullUrl: [{}] version: [{}]",
        principal.getName(),
        codeSystem.getName(),
        codeSystem.getOid(),
        codeSystem.getFullUrl(),
        codeSystem.getVersion());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(fhirTerminologyService.createCodeSystem(codeSystem));
  }

  @PutMapping(
      path = "/{id}",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<CodeSystem> updateCodeSystem(
      Principal principal, @PathVariable String id, @Valid @RequestBody CodeSystem codeSystem) {
    log.info(
        "Admin user [{}] is updating code system with id: [{}] name: [{}] oid: [{}] fullUrl: [{}] version: [{}]",
        principal.getName(),
        id,
        codeSystem.getName(),
        codeSystem.getOid(),
        codeSystem.getFullUrl(),
        codeSystem.getVersion());
    return ResponseEntity.ok().body(fhirTerminologyService.updateCodeSystem(id, codeSystem));
  }

  @DeleteMapping(path = "/{id}")
  public ResponseEntity<Void> deleteCodeSystem(Principal principal, @PathVariable String id) {
    log.info("Admin user [{}] is deleting code system with id: [{}]", principal.getName(), id);
    fhirTerminologyService.deleteCodeSystem(id);
    return ResponseEntity.noContent().build();
  }
}
