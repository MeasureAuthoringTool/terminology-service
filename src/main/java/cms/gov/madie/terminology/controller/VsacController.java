package cms.gov.madie.terminology.controller;

import java.util.concurrent.ExecutionException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.hl7.fhir.r4.model.ValueSet;

import cms.gov.madie.terminology.service.VsacService;

import lombok.extern.slf4j.Slf4j;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;

@RestController
@RequestMapping(path = "/vsac")
@Slf4j
public class VsacController {

  private final VsacService vsacService;

  public VsacController(VsacService vsacService) {
    this.vsacService = vsacService;
  }

  @GetMapping(path = "/valueSet", produces = MediaType.APPLICATION_ATOM_XML_VALUE)
  public ResponseEntity<ValueSet> getValueSet(
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

    // This works
    // return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(valuesetResponse);

    // This will produce error:
    // com.fasterxml.jackson.databind.ser.BeanPropertyWriter.serializeAsField
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(fhirValueSet);
  }
}
