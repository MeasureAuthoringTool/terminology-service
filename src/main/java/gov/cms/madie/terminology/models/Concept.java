package gov.cms.madie.terminology.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a concept/code within a ValueSet expansion. Maps to FHIR ValueSet.expansion.contains
 * and can be converted to QdmValueSet.Concept.
 *
 * <p>Field mappings:
 *
 * <ul>
 *   <li>code - Both: the code value
 *   <li>codeSystemOid - QDM: code_system_oid, derived from FHIR system URL via CodeSystem lookup
 *   <li>codeSystemName - QDM: code_system_name, FHIR: from CodeSystem resource
 *   <li>codeSystemVersion - Both: version of the code system
 *   <li>displayName - Both: human-readable display name for the code
 *   <li>codeSystemUri - FHIR: system (the full URI, e.g., "http://snomed.info/sct")
 * </ul>
 */
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor
public class Concept {
  private String code; // The code value
  private String codeSystemOid; // Code system OID (e.g., "2.16.840.1.113883.6.96")
  private String codeSystemName; // Human-readable name (e.g., "SNOMEDCT")
  private String codeSystemVersion; // Version of the code system
  private String displayName; // Human-readable display name for the code
  private String codeSystemUri; // FHIR system URI (e.g., "http://snomed.info/sct")
}
