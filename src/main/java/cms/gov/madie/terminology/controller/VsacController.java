package cms.gov.madie.terminology.controller;

import ca.uhn.fhir.context.FhirContext;
import cms.gov.madie.terminology.dto.ValueSetsSearchCriteria;
import cms.gov.madie.terminology.service.VsacService;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import gov.cms.madiejavamodels.cql.terminology.CqlCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/vsac")
@Slf4j
@RequiredArgsConstructor
public class VsacController {

  private final VsacService vsacService;
  private final FhirContext fhirContext;

  @GetMapping(path = "/valueSet", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> getValueSet(
      @RequestParam(name = "tgt") String tgt,
      @RequestParam(name = "oid") String oid,
      @RequestParam(required = false, name = "profile") String profile,
      @RequestParam(required = false, name = "includeDraft") String includeDraft,
      @RequestParam(required = false, name = "release") String release,
      @RequestParam(required = false, name = "version") String version) {

    log.debug("Entering: getValueSet()");

    RetrieveMultipleValueSetsResponse valuesetResponse =
        vsacService.getValueSet(oid, tgt, profile, includeDraft, release, version);

    ValueSet fhirValueSet = vsacService.convertToFHIRValueSet(valuesetResponse);
    log.debug("valueset id = " + fhirValueSet.getId());

    String serialized = serializeFhirValueset(fhirValueSet);

    return ResponseEntity.ok().body(serialized);
  }

  @PutMapping(path = "/value-sets/searches", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> searchValueSets(
      @RequestBody ValueSetsSearchCriteria searchCriteria) {
    log.debug("VsacController::getValueSets");
    List<RetrieveMultipleValueSetsResponse> vsacValueSets =
        vsacService.getValueSets(searchCriteria);
    List<ValueSet> fhirValueSets = vsacService.convertToFHIRValueSets(vsacValueSets);
    String serializedValueSets =
        fhirValueSets.stream().map(this::serializeFhirValueset).collect(Collectors.joining(", "));

    return ResponseEntity.ok().body("[" + serializedValueSets + "]");
  }

  @PutMapping(path = "/validations/codes", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<CqlCode>> validateCodes(
      @RequestBody List<CqlCode> cqlCodes, @RequestParam String tgt) {
    return ResponseEntity.ok().body(vsacService.validateCodes(cqlCodes, tgt));
  }

  protected String serializeFhirValueset(ValueSet fhirValueSet) {
    return fhirContext.newJsonParser().encodeResourceToString(fhirValueSet);
  }
}
