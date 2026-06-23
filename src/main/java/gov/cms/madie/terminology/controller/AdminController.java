package gov.cms.madie.terminology.controller;

import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import gov.cms.madie.terminology.service.VsacService;
import gov.cms.madie.terminology.task.UpdateCodeSystemTask;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(path = "/terminology/admin")
@Slf4j
@RequiredArgsConstructor
public class AdminController {

  private final UpdateCodeSystemTask updateCodeSystemTask;
  private final FhirTerminologyService fhirTerminologyService;
  private final VsacService vsacService;
  private final CacheManager cacheManager;

  @PostMapping(path = "/update-code-systems", produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<List<CodeSystem>> retrieveAndUpdateCodeSystems(Principal principal) {
    final String username = principal.getName();
    log.info("Admin user [{}] is triggering a manual code system refresh", username);
    UmlsUser umlsUser = vsacService.verifyUmlsAccess(username);
    return ResponseEntity.ok().body(fhirTerminologyService.retrieveAllCodeSystems(umlsUser));
  }

  @PostMapping(
      path = "/code-system",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<CodeSystem> createCodeSystem(
      Principal principal, @Valid @RequestBody CodeSystem codeSystem) {
    log.info(
        "Admin user [{}] is creating a new code system version for"
            + " name: [{}] oid: [{}] fullUrl: [{}] version: [{}]",
        principal.getName(),
        codeSystem.getName(),
        codeSystem.getOid(),
        codeSystem.getFullUrl(),
        codeSystem.getVersion());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(fhirTerminologyService.createCodeSystem(codeSystem));
  }

  @PutMapping(
      path = "/code-system/{id}",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<CodeSystem> updateCodeSystem(
      Principal principal, @PathVariable String id, @Valid @RequestBody CodeSystem codeSystem) {
    log.info(
        "Admin user [{}] is updating code system with id: [{}]"
            + " name: [{}] oid: [{}] fullUrl: [{}] version: [{}]",
        principal.getName(),
        id,
        codeSystem.getName(),
        codeSystem.getOid(),
        codeSystem.getFullUrl(),
        codeSystem.getVersion());
    return ResponseEntity.ok().body(fhirTerminologyService.updateCodeSystem(id, codeSystem));
  }

  @DeleteMapping(path = "/code-system/{id}")
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<Void> deleteCodeSystem(Principal principal, @PathVariable String id) {
    log.info("Admin user [{}] is deleting code system with id: [{}]", principal.getName(), id);
    fhirTerminologyService.deleteCodeSystem(id);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping(path = "/cache/evict")
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<List<String>> evictAllCaches(Principal principal) {
    List<String> evictedCaches = new ArrayList<>(cacheManager.getCacheNames());
    log.info("Admin user [{}] is evicting all caches: {}", principal.getName(), evictedCaches);
    evictedCaches.forEach(cacheName -> cacheManager.getCache(cacheName).clear());
    return ResponseEntity.ok(evictedCaches);
  }

  @PostMapping(path = "/trigger-code-system-refresh")
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<String> triggerCodeSystemRefresh() {
    if (!updateCodeSystemTask.isRunning()) {

      new Thread(() -> updateCodeSystemTask.updateCodeSystems()).start();

      return ResponseEntity.ok("Code system refresh has been started");
    }

    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body("Update Code System is already running. We have NOT started the job again");
  }
}
