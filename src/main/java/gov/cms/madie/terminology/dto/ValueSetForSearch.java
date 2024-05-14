package gov.cms.madie.terminology.dto;

import lombok.Builder;
import lombok.Data;
import org.hl7.fhir.r4.model.Enumerations;

@Data
@Builder
public class ValueSetForSearch {
  private String title;
  private String name;
  private String author;
  private String composedOf;
  private String effectiveDate;
  private String lastReviewDate;
  private String lastUpdated;
  private String publisher;
  private String purpose;
  private String url;
  private String oid;
  private String steward;
  private String version;
  private String codeSystem;
  private Enumerations.PublicationStatus status;
}
