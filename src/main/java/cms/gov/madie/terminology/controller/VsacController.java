package cms.gov.madie.terminology.controller;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.ValueSet;

import cms.gov.madie.terminology.models.UmlsUser;
import cms.gov.madie.terminology.service.VsacService;

import lombok.extern.slf4j.Slf4j;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;

@RestController
@RequestMapping(path = "/vsac")
@Slf4j
public class VsacController {

  private final VsacService vsacService;

  @Autowired IParser parser;

  // @Autowired UmlsKeyRepository repository;

  public VsacController(VsacService vsacService) {
    this.vsacService = vsacService;
  }

  @GetMapping(path = "/valueSet", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> getValueSet(
      // @RequestParam(required = true, name = "tgt") String tgt,
      Principal principal,
      @RequestParam(required = true, name = "oid") String oid,
      @RequestParam(required = false, name = "profile") String profile,
      @RequestParam(required = false, name = "includeDraft") String includeDraft,
      @RequestParam(required = false, name = "release") String release,
      @RequestParam(required = false, name = "version") String version)
      throws InterruptedException, ExecutionException {

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

    String serialized = parser.encodeResourceToString(fhirValueSet);

    return serialized;
  }

  @PostMapping(path = "/umlsLogin")
  public ResponseEntity<String> umlsLogin(
      Principal principal, @RequestParam(required = true, name = "apiKey") String apiKey) {
    final String username = principal.getName();
    log.info("Entering: umlsLogin(): username = " + username + " apiKey = " + apiKey);

    String tgt = null;
    try {
      tgt = vsacService.getTgt(apiKey);
      UmlsUser umlsUser = vsacService.saveUmlsUser(username, apiKey, tgt);
      String msg =
          "User: "
              + username
              + " successfully loggin in to UMLS. TGT expiry time = "
              + Timestamp.from(umlsUser.getTgtExpiryTime()).toString();
      return ResponseEntity.ok().body(msg);
    } catch (WebClientResponseException ex) {
      log.error("WebClientResponseException -> " + ex.getMessage());
    } catch (InterruptedException ex) {
      log.error("InterruptedException -> " + ex.getMessage());
    } catch (ExecutionException ex) {
      log.error("ExecutionException -> " + ex.getMessage());
    }
    return ResponseEntity.badRequest().body("Invalid UMLS Key. Please re-enter a valid UMLS Key.");
  }

  @GetMapping("/checkLogin")
  public ResponseEntity<Boolean> checkUserLogin(Principal principal) {
    final String username = principal.getName();
    Optional<UmlsUser> umlsUser = vsacService.findByHarpId(username);
    if (umlsUser.isPresent() && umlsUser.get().getApiKey() != null) {
      UmlsUser user = umlsUser.get();
      String tgt = user.getTgt();
      try {
        vsacService.getServiceTicket(tgt);
        log.info("User: " + username + " has valid TGT");
        return ResponseEntity.ok().body(Boolean.TRUE);
      } catch (WebClientResponseException ex) {
        log.error("WebClientResponseException -> " + ex.getMessage());
        if (ex.getMessage().contains("401")) {
          return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
      } catch (InterruptedException ex) {
        log.error("InterruptedException -> " + ex.getMessage());
      } catch (ExecutionException ex) {
        log.error("ExecutionException -> " + ex.getMessage());
      }
    } else {
      log.error("UmlsApiKey is null");
    }
    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
  }
}
