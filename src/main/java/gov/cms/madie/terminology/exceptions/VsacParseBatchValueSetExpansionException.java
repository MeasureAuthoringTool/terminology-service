package gov.cms.madie.terminology.exceptions;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.OperationOutcome;

@Getter
@Setter
public class VsacParseBatchValueSetExpansionException extends RuntimeException {

  private String message;
  private OperationOutcome operationOutcome;
  private String manifestExpansionFullUrl;
  private String oid;

  public VsacParseBatchValueSetExpansionException(
      String message,
      OperationOutcome operationOutcome,
      String manifestExpansionFullUrl,
      String oid) {
    this.message = message;
    this.operationOutcome = operationOutcome;
    this.manifestExpansionFullUrl = manifestExpansionFullUrl;
    this.oid = oid;
  }
}
