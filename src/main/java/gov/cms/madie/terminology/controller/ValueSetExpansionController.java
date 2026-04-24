package gov.cms.madie.terminology.controller;

import gov.cms.madie.terminology.models.MadieValueSet;
import gov.cms.madie.terminology.service.ValueSetExpansionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping(path = "/terminology/admin")
@Slf4j
@RequiredArgsConstructor
public class ValueSetExpansionController {

  private final ValueSetExpansionService vses;

  @GetMapping(
      value = "/implementation-guide/{ig}/version/{version}/value-sets",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<Set<MadieValueSet>> getValueSetDependencies(
      Principal principal, @PathVariable String ig, @PathVariable String version) {
    log.info("Getting Value Set dependencies for IG {}, version {}", ig, version);
    return ResponseEntity.ok(vses.getValueSetDependencies(ig, version));
  }

  @GetMapping(
      value = "/implementation-guide/value-sets",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<Map<String, Map<String, Set<MadieValueSet>>>> getValueSetDependencies(
      Principal principal) {
    return ResponseEntity.ok(vses.getValueSetDependencies());
  }

  @GetMapping(
      value = "/implementation-guide/update-value-sets",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public void updateValueSetDependencies(Principal principal) {
    vses.updateValueSetDependencies();
  }
}
