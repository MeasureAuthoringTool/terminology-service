package gov.cms.madie.terminology.controller;

import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.QdmValueSet;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.exceptions.VsacUnauthorizedException;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import gov.cms.madie.terminology.service.VsacService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

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
    Optional<UmlsUser> umlsUser = vsacService.findByHarpId(username);
    if (umlsUser.isPresent() && !StringUtils.isBlank(umlsUser.get().getApiKey())) {
      return ResponseEntity.ok().body(fhirTerminologyService.getManifests(umlsUser.get()));
    }
    log.error(
        "Unable to Retrieve List of available manifests, "
            + "UMLS Authentication Key Not found for user : [{}}]",
        username);
    throw new VsacUnauthorizedException("Please login to UMLS before proceeding");
  }

  @PutMapping("/value-sets/expansion/qdm")
  public ResponseEntity<List<QdmValueSet>> getValueSetsExpansions(
      Principal principal, @RequestBody ValueSetsSearchCriteria searchCriteria) {
    final String username = principal.getName();
    log.info(
        "User [{}] is attempting to fetch value sets expansions from VSAC FHIR Terminology Server.",
        username);
    Optional<UmlsUser> umlsUser = vsacService.findByHarpId(username);
    if (umlsUser.isPresent() && !StringUtils.isBlank(umlsUser.get().getApiKey())) {
      List<QdmValueSet> qdmValueSets =
          fhirTerminologyService.getValueSetsExpansionsForQdm(searchCriteria, umlsUser.get());
      return ResponseEntity.ok().body(qdmValueSets);
    }
    log.error(
        "Unable to Retrieve Value Sets Expansions, "
            + "UMLS Authentication Key Not found for user : [{}}]",
        username);
    throw new VsacUnauthorizedException("Please login to UMLS before proceeding");
  }
}
