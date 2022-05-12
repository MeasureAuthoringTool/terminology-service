package cms.gov.madie.terminology.controller;

import java.util.concurrent.ExecutionException;

import cms.gov.madie.terminology.dto.CqlCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.ValueSet;

import cms.gov.madie.terminology.service.VsacService;

import lombok.extern.slf4j.Slf4j;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "/vsac")
@Slf4j
public class VsacController {

  private final VsacService vsacService;

  @Autowired IParser parser;

  public VsacController(VsacService vsacService) {
    this.vsacService = vsacService;
  }

  @GetMapping(path = "/valueSet", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> getValueSet(
      @RequestParam(required = true, name = "tgt") String tgt,
      @RequestParam(required = true, name = "oid") String oid,
      @RequestParam(required = false, name = "profile") String profile,
      @RequestParam(required = false, name = "includeDraft") String includeDraft,
      @RequestParam(required = false, name = "release") String release,
      @RequestParam(required = false, name = "version") String version)
      throws InterruptedException, ExecutionException {

    log.debug("Entering: getValueSet()");

    String serviceTicket = vsacService.getServiceTicket(tgt);

    RetrieveMultipleValueSetsResponse valuesetResponse =
        vsacService.getValueSet(oid, serviceTicket, profile, includeDraft, release, version);

    ValueSet fhirValueSet = vsacService.convertToFHIRValueSet(valuesetResponse, oid);
    log.debug("valueset id = " + fhirValueSet.getId());

    String serialized = serializeFhirValueset(fhirValueSet);

    return ResponseEntity.ok().body(serialized);
  }

  protected String serializeFhirValueset(ValueSet fhirValueSet) {

    String serialized = parser.encodeResourceToString(fhirValueSet);

    return serialized;
  }

  @PutMapping(path = "/code")
  public ResponseEntity<String> getCode(
      @RequestBody CqlCode cqlCode, @RequestParam(name = "tgt") String tgt)
      throws InterruptedException, ExecutionException {
    return ResponseEntity.ok().body(vsacService.getCode(cqlCode, tgt));
  }
}
