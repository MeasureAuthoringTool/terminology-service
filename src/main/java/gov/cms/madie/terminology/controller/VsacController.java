package gov.cms.madie.terminology.controller;

import java.util.List;
import java.util.stream.Collectors;
import java.security.Principal;
import java.util.Optional;

import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.QdmValueSet;
import gov.cms.madie.terminology.exceptions.VsacUnauthorizedException;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.ValueSet;

import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import gov.cms.madie.models.cql.terminology.CqlCode;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.VsacService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(path = "/vsac")
@Slf4j
@RequiredArgsConstructor
public class VsacController {

  private final VsacService vsacService;
  private final FhirTerminologyService fhirTerminologyService;
  private final FhirContext fhirContext;

  @GetMapping(path = "/valueset", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> getValueSet(
      Principal principal,
      @RequestParam String oid,
      @RequestParam(required = false, name = "profile") String profile,
      @RequestParam(required = false, name = "includeDraft") String includeDraft,
      @RequestParam(required = false, name = "release") String release,
      @RequestParam(required = false, name = "version") String version) {
    log.debug("Entering: getValueSet()");

    final String username = principal.getName();
    Optional<UmlsUser> umlsUser = vsacService.findByHarpId(username);
    if (umlsUser.isPresent()) {
      RetrieveMultipleValueSetsResponse valuesetResponse =
          vsacService.getValueSet(oid, umlsUser.get(), profile, includeDraft, release, version);

      ValueSet fhirValueSet = vsacService.convertToFHIRValueSet(valuesetResponse);
      log.debug("valueset id = " + fhirValueSet.getId());

      return ResponseEntity.ok().body(serializeFhirValueset(fhirValueSet));
    }

    return new ResponseEntity<>("No UMLS session", HttpStatus.UNAUTHORIZED);
  }

  protected String serializeFhirValueset(ValueSet fhirValueSet) {
    return fhirContext.newJsonParser().encodeResourceToString(fhirValueSet);
  }

  @PutMapping(path = "/value-sets/searches", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> searchValueSets(
      Principal principal, @RequestBody ValueSetsSearchCriteria searchCriteria) {
    log.debug("VsacController::getValueSets");

    final String username = principal.getName();
    Optional<UmlsUser> umlsUser = vsacService.findByHarpId(username);
    if (umlsUser.isPresent()) {

      List<RetrieveMultipleValueSetsResponse> vsacValueSets =
          vsacService.getValueSets(searchCriteria, umlsUser.get());

      List<ValueSet> fhirValueSets = vsacService.convertToFHIRValueSets(vsacValueSets);
      String serializedValueSets =
          fhirValueSets.stream().map(this::serializeFhirValueset).collect(Collectors.joining(", "));

      return ResponseEntity.ok().body("[" + serializedValueSets + "]");
    }
    return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
  }

  @PutMapping("/qdm/value-sets/searches")
  public ResponseEntity<List<QdmValueSet>> getQdmValueSets(
      Principal principal, @RequestBody ValueSetsSearchCriteria searchCriteria) {
    log.debug("VsacController::getQdmValueSets");

    final String username = principal.getName();
    Optional<UmlsUser> umlsUser = vsacService.findByHarpId(username);
    if (umlsUser.isPresent()) {
      List<QdmValueSet> qdmValueSets =
          vsacService.getValueSetsInQdmFormat(searchCriteria, umlsUser.get());

      return ResponseEntity.ok().body(qdmValueSets);
    }
    return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
  }

  @PutMapping(path = "/validations/codes", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<CqlCode>> validateCodes(
      Principal principal,
      @RequestBody List<CqlCode> cqlCodes,
      @RequestParam(required = false, defaultValue = "FHIR") String model) {
    final String username = principal.getName();
    Optional<UmlsUser> umlsUser = vsacService.findByHarpId(username);
    if (umlsUser.isPresent() && umlsUser.get().getApiKey() != null) {
      return ResponseEntity.ok().body(vsacService.validateCodes(cqlCodes, umlsUser.get(), model));
    }
    return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
  }

  @PostMapping(
      path = "/umls-credentials",
      produces = {MediaType.TEXT_PLAIN_VALUE})
  public ResponseEntity<String> umlsLogin(Principal principal, @RequestBody String apiKey) {
    final String username = principal.getName();
    log.debug("Entering: umlsLogin(): username = " + username);

    UmlsUser umlsUser = vsacService.saveUmlsUser(username, apiKey);
    String msg = "User: " + umlsUser.getHarpId() + " is successfully logged in to UMLS.";
    log.debug("msg = ");
    return ResponseEntity.ok().body(msg);
  }

  @GetMapping("/umls-credentials/status")
  public ResponseEntity<Boolean> checkUserLogin(Principal principal) {
    return vsacService.validateUmlsInformation(principal.getName())
        ? ResponseEntity.ok().body(Boolean.TRUE)
        : new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
  }

  @GetMapping("/manifest-list")
  public ResponseEntity<List<ManifestExpansion>> getManifests(Principal principal) {
    final String username = principal.getName();
    log.info("Retrieving List of available manifests, requested by HARP ID : [{}}]", username);
    Optional<UmlsUser> umlsUser = vsacService.findByHarpId(username);
    if (umlsUser.isPresent() && !StringUtils.isBlank(umlsUser.get().getApiKey())) {
      return ResponseEntity.ok().body(fhirTerminologyService.getManifests(umlsUser.get()));
    }
    log.error("Unable to Retrieve List of available manifests, UMLS Authentication Key Not found for user : [{}}]", username);
    throw new VsacUnauthorizedException("Please login to UMLS before proceeding");
  }
}
