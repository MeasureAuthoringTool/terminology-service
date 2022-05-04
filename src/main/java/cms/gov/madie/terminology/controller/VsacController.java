package cms.gov.madie.terminology.controller;

import org.springframework.web.bind.annotation.PostMapping;

import java.util.concurrent.ExecutionException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

  @PostMapping(path = "/serviceTicket", produces = "text/plain")
  public ResponseEntity<String> getServiceTicket(
      @RequestParam(required = true, name = "tgt") String tgt)
      throws InterruptedException, ExecutionException {
    log.info("Entering: getServiceTicket(): tgt = " + tgt);
    String st = vsacService.getServiceTicket(tgt);
    return ResponseEntity.ok().body(st);
  }

  @GetMapping(
      path = "/valueSet",
      consumes = "text/plain",
      produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<RetrieveMultipleValueSetsResponse> getValueSet(
      @RequestParam(required = true, name = "oid") String oid,
      @RequestParam(required = true, name = "stNumber") String stNumber,
      @RequestParam(required = false, name = "profile") String profile,
      @RequestParam(required = false, name = "includeDraft") String includeDraft,
      @RequestParam(required = false, name = "release") String release,
      @RequestParam(required = false, name = "version") String version) {
    log.info("Entering: getValueSet()");
    RetrieveMultipleValueSetsResponse valuesetResponse =
        vsacService.getValueSet(oid, stNumber, profile, includeDraft, release, version);
    return ResponseEntity.ok().body(valuesetResponse);
  }
}
