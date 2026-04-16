package gov.cms.madie.terminology.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A unified ValueSet model that serves as an intermediary storage for value set expansions.
 * Designed for local database storage with easy conversion to both QdmValueSet and FHIR ValueSet.
 *
 * <p>This model stores expansion data retrieved from the VSAC FHIR Terminology Server and can be
 * converted to:
 *
 * <ul>
 *   <li>QdmValueSet - for the /value-sets/expansion/qdm endpoint
 *   <li>FHIR ValueSet (org.hl7.fhir.r4.model.ValueSet) - for the /value-sets/expansion/fhir
 *       endpoint
 * </ul>
 *
 * <p>Field mappings:
 *
 * <ul>
 *   <li>oid - FHIR: identifier[].value (e.g., "2.16.840.1.113883.3.464.1003.101.12.1001")
 *   <li>displayName - FHIR: name/title
 *   <li>version - FHIR: version
 *   <li>url - FHIR: url (canonical URL)
 *   <li>valueSet - FHIR: The FHIR ValueSet Resource
 * </ul>
 */
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor
@Document(collection = "valueSetExpansion")
@EqualsAndHashCode(of = {"url", "version"})
public class MadieValueSet {

  @Id private String id; // MongoDB internal ID

  // Core identifiers (required for both QDM and FHIR)
  private String oid; // Value set OID (e.g., "2.16.840.1.113883.3.464.1003.101.12.1001")
  private String url; // FHIR canonical URL (e.g., "http://cts.nlm.nih.gov/fhir/ValueSet/{oid}")
  private String version; // Value set version

  // Display information
  private String displayName; // Human-readable name (FHIR: name/title)

  // Expansion metadata
  private String status; // Publication status (e.g., "active", "draft")
  private String publisher; // Publisher/steward of the value set
  private Instant expansionTimestamp; // When the expansion was performed
  private Integer totalConcepts; // Total number of concepts in the expansion

  // Timestamps for cache management
  private Instant lastUpdated; // When this record was last fetched/updated locally

  // FHIR ValueSet
  private String valueSet;
}
