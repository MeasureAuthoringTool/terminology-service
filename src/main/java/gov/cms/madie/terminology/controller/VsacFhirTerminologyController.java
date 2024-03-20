package gov.cms.madie.terminology.controller;

import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.exceptions.VsacUnauthorizedException;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import gov.cms.madie.terminology.service.VsacService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(path = "/terminology/fhir")
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
}
