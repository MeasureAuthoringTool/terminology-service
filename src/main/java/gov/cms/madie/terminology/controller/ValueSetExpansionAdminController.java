package gov.cms.madie.terminology.controller;

import gov.cms.madie.terminology.dto.ValueSetDisplayForAdmin;
import gov.cms.madie.terminology.models.MadieValueSet;
import gov.cms.madie.terminology.service.ValueSetExpansionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
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

  @PutMapping(
      value = "/value-set",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<MadieValueSet> upsertValueSet(
      Principal principal, @Valid @RequestBody MadieValueSet valueSet) {
    log.info(
        "Admin user [{}] is upserting value set with url: [{}] version: [{}]",
        principal.getName(),
        valueSet.getUrl(),
        valueSet.getVersion());
    return ResponseEntity.ok().body(vses.upsertValueSet(valueSet));
  }

  @DeleteMapping(value = "/value-set/{id}")
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<Void> deleteValueSet(Principal principal, @PathVariable String id) {
    log.info("Admin user [{}] is deleting value set with id: [{}]", principal.getName(), id);
    vses.deleteValueSet(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/valuesets")
  public ResponseEntity<Page<ValueSetDisplayForAdmin>> getValueSets(
      @RequestParam(required = false, defaultValue = "10", name = "limit") int limit,
      @RequestParam(required = false, defaultValue = "0", name = "page") int page,
      @RequestParam(required = false, name = "sortInfo") String sortInfo) {

    Pageable pageReq;

    if (StringUtils.isNotBlank(sortInfo)) {
      String[] sortParts = sortInfo.split(",");

      if (sortParts.length == 2) {
        String sortBy = mapSortField(sortParts[0]);
        boolean desc = Boolean.parseBoolean(sortParts[1]);

        pageReq =
            PageRequest.of(
                page, limit, Sort.by(desc ? Sort.Order.desc(sortBy) : Sort.Order.asc(sortBy)));
      } else {
        pageReq = PageRequest.of(page, limit, Sort.by(Sort.Order.desc("lastUpdated")));
      }
    } else {
      pageReq = PageRequest.of(page, limit, Sort.by(Sort.Order.desc("lastUpdated")));
    }

    return ResponseEntity.ok(vses.getValueSets(pageReq));
  }

  private String mapSortField(String sortField) {
    return switch (sortField) {
      case "url" -> "url";
      case "lastUpdated" -> "lastUpdated";
      case "manuallyModified" -> "manuallyModified";
      default -> "lastUpdated";
    };
  }
}
