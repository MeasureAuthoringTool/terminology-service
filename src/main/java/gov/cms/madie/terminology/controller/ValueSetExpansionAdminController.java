package gov.cms.madie.terminology.controller;

import gov.cms.madie.terminology.service.ValueSetExpansionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping(path = "/terminology/admin")
@Slf4j
@RequiredArgsConstructor
public class ValueSetExpansionAdminController {

  private final ValueSetExpansionService vses;

  @GetMapping(
      value = "/implementation-guides/value-sets",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<List<String>> getValueSetDependencies(
      Principal principal,
      @RequestParam(required = false) String ig,
      @RequestParam(required = false) String version) {
    if (StringUtils.isNotBlank(ig) && StringUtils.isNotBlank(version)) {
      log.info(
          "Admin User [{}] requested Value Set dependencies for IG {}, version {}",
          principal.getName(),
          ig,
          version);
      return ResponseEntity.ok(vses.getValueSetDependencies(ig, version));
    }
    log.info("Admin User [{}] requested Value Set dependencies for all IGs", principal.getName());
    return ResponseEntity.ok(vses.getValueSetDependencies());
  }

  @GetMapping(
      value = "/implementation-guides/update-value-sets",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<Void> updateValueSetDependencies(
      @RequestParam(required = false) String ig,
      @RequestParam(required = false) String version,
      Principal principal) {
    if (StringUtils.isNotBlank(ig) && StringUtils.isNotBlank(version)) {
      log.info(
          "Admin User [{}] is updating Value Set dependencies for IG {} version {}.",
          principal.getName(),
          ig,
          version);
      vses.updateIgValueSetDependencies(ig, version);
    } else {
      log.info(
          "Admin User [{}] is updating Value Set dependencies for all IGs.", principal.getName());
      vses.updateValueSetDependencies();
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }

  @GetMapping(value = "/implementation-guides", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<String>> getImplementationGuides() {
    return ResponseEntity.ok(vses.getImplementationGuides());
  }
}
