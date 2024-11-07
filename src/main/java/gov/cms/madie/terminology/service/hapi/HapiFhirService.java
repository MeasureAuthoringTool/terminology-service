package gov.cms.madie.terminology.service.hapi;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.*;
import gov.cms.madie.terminology.exceptions.HapiOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HapiFhirService {
  private final IGenericClient hapiClient;

  /**
   * This method fetches the ValueSet expansion by id from HAPI server
   *
   * @param id -> Value Set id
   * @return ValueSet -> Value Set with expansion details
   */
  public ValueSet getValueSetExpansionById(String id) {
    try {
      log.info("Fetching ValueSet expansion for {}", id);
      Parameters parameters =
          hapiClient
              .operation()
              .onInstance(new IdType("ValueSet", id))
              .named("$expand")
              .withNoParameters(Parameters.class)
              .execute();
      return (ValueSet) parameters.getParameter().get(0).getResource();
    } catch (BaseServerResponseException ex) {
      log.error("An error occurred while fetching expansion for the ValueSet[{}]", id, ex);
      OperationOutcome outcome = (OperationOutcome) ex.getOperationOutcome();
      if (outcome != null) {
        String errors =
            outcome.getIssue().stream()
                .map(OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics)
                .collect(Collectors.joining("\n"));
        throw new HapiOperationException(errors);
      } else {
        throw new HapiOperationException(ex.getMessage());
      }
    }
  }
}
