package gov.cms.madie.terminology.controller;

import java.util.List;
import java.util.stream.Collectors;
import java.security.Principal;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

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
    if (umlsUser.isPresent() && umlsUser.get().getTgt() != null) {
      RetrieveMultipleValueSetsResponse valuesetResponse =
          vsacService.getValueSet(
              oid, umlsUser.get().getTgt(), profile, includeDraft, release, version);

      ValueSet fhirValueSet = vsacService.convertToFHIRValueSet(valuesetResponse);
      log.debug("valueset id = " + fhirValueSet.getId());

      return ResponseEntity.ok().body(serializeFhirValueset(fhirValueSet));
    }

    return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
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
    if (umlsUser.isPresent() && umlsUser.get().getTgt() != null) {

      List<RetrieveMultipleValueSetsResponse> vsacValueSets =
          vsacService.getValueSets(searchCriteria, umlsUser.get().getTgt());

      List<ValueSet> fhirValueSets = vsacService.convertToFHIRValueSets(vsacValueSets);
      String serializedValueSets =
          fhirValueSets.stream().map(this::serializeFhirValueset).collect(Collectors.joining(", "));

      return ResponseEntity.ok().body("[" + serializedValueSets + "]");
    }
    return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
  }

  @PutMapping(path = "/validations/codes", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<CqlCode>> validateCodes(
      Principal principal, @RequestBody List<CqlCode> cqlCodes) {
    final String username = principal.getName();
    Optional<UmlsUser> umlsUser = vsacService.findByHarpId(username);
    if (umlsUser.isPresent() && umlsUser.get().getApiKey() != null) {
      return ResponseEntity.ok().body(vsacService.validateCodes(cqlCodes, umlsUser.get().getTgt()));
    }
    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
  }

  @PostMapping(path = "/umls-credentials")
  public ResponseEntity<String> umlsLogin(Principal principal, @RequestBody String apiKey)
      throws InterruptedException, ExecutionException {
    final String username = principal.getName();
    log.debug("Entering: umlsLogin(): username = " + username);

    String tgt = vsacService.getTgt(apiKey);
    UmlsUser umlsUser = vsacService.saveUmlsUser(username, apiKey, tgt);
    String msg = "User: " + umlsUser.getHarpId() + " successfully loggin in to UMLS.";
    log.debug("msg = ");
    return ResponseEntity.ok().body(msg);
  }

  @GetMapping("/umls-credentials/status")
  public ResponseEntity<Boolean> checkUserLogin(Principal principal) {
    final String username = principal.getName();
    Optional<UmlsUser> umlsUser = vsacService.findByHarpId(username);
    if (umlsUser.isPresent() && umlsUser.get().getApiKey() != null) {
      UmlsUser user = umlsUser.get();
      String tgt = user.getTgt();
      vsacService.getServiceTicket(tgt);
      log.debug("User: " + username + " has valid TGT");
      return ResponseEntity.ok().body(Boolean.TRUE);
    }
    return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
  }
}
