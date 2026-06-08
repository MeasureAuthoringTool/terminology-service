package gov.cms.madie.terminology.util;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.models.CodeSystem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class FhirBundleUtil {

  public static Bundle createValueSetBundle(FhirContext context, String valueSet) {
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.SEARCHSET);
    bundle.setTotal(1);
    Bundle.BundleEntryComponent entry = bundle.addEntry();
    entry.setResource(
        context.newJsonParser().parseResource(org.hl7.fhir.r4.model.ValueSet.class, valueSet));

    return bundle;
  }

  public static Bundle createCodeSystemBundle(List<CodeSystem> codeSystems) {
    // Create a FHIR Bundle
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.SEARCHSET);
    bundle.setTotal(codeSystems.size());

    // Add each CodeSystem as a bundle entry
    for (CodeSystem codeSystem : codeSystems) {
      org.hl7.fhir.r4.model.CodeSystem fhirCodeSystem = convertToFhirCodeSystem(codeSystem);
      Bundle.BundleEntryComponent entry = bundle.addEntry();
      entry.setResource(fhirCodeSystem);
    }

    return bundle;
  }

  private static org.hl7.fhir.r4.model.CodeSystem convertToFhirCodeSystem(CodeSystem codeSystem) {
    org.hl7.fhir.r4.model.CodeSystem fhirCodeSystem = new org.hl7.fhir.r4.model.CodeSystem();
    fhirCodeSystem.setId(codeSystem.getId());
    fhirCodeSystem.setUrl(codeSystem.getFullUrl());
    fhirCodeSystem.setName(codeSystem.getName());
    fhirCodeSystem.setTitle(codeSystem.getTitle());
    fhirCodeSystem.setVersion(codeSystem.getVersionId());
    fhirCodeSystem.setIdentifier(List.of(new Identifier().setValue(codeSystem.getOid())));
    // Add other fields as needed
    return fhirCodeSystem;
  }
}
