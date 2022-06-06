package cms.gov.madie.terminology.controller;

import java.security.Principal;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import gov.cms.madiejavamodels.cql.terminology.CqlCode;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.ValueSet;

import cms.gov.madie.terminology.models.UmlsUser;
import cms.gov.madie.terminology.service.VsacService;

import lombok.extern.slf4j.Slf4j;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;

@RestController
@RequestMapping(path = "/vsac")
@Slf4j
@RequiredArgsConstructor
public class VsacController {

  private final VsacService vsacService;
  private final IParser parser;

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
    String serialized = null;
    if (umlsUser.isPresent() && umlsUser.get().getApiKey() != null) {
      String serviceTicket = vsacService.getServiceTicket(umlsUser.get().getTgt());

      RetrieveMultipleValueSetsResponse valuesetResponse =
          vsacService.getValueSet(oid, serviceTicket, profile, includeDraft, release, version);

      ValueSet fhirValueSet = vsacService.convertToFHIRValueSet(valuesetResponse, oid);
      log.debug("valueset id = " + fhirValueSet.getId());

      serialized = serializeFhirValueset(fhirValueSet);
    }

    return ResponseEntity.ok().body(serialized);
  }

  protected String serializeFhirValueset(ValueSet fhirValueSet) {
    return parser.encodeResourceToString(fhirValueSet);
  }

  @PutMapping(path = "/validations/codes", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<CqlCode>> validateCodes(
      @RequestBody List<CqlCode> cqlCodes, @RequestParam String tgt) {
    return ResponseEntity.ok().body(vsacService.validateCodes(cqlCodes, tgt));
  }

  @PostMapping(path = "/umls-credentials")
  public ResponseEntity<String> umlsLogin(Principal principal, @RequestParam String apiKey)
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
    } else {
      log.error("UmlsApiKey is null");
    }
    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
  }
}
