package gov.cms.madie.terminology.controller;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.service.InternalTerminologyService;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/internal-terminology")
@RequiredArgsConstructor
public class InternalTerminologyController {

  private final FhirContext fhirContext;
  private final InternalTerminologyService internalTerminologyService;

  @GetMapping(path = "ValueSet/expand", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> valueSetExpansion(@RequestParam String url) {
    ValueSet valueSet = internalTerminologyService.getValueSetExpansionByUrl(url);
    return ResponseEntity.ok().body(fhirContext.newJsonParser().encodeResourceToString(valueSet));
  }
}
