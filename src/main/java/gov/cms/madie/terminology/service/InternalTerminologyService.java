package gov.cms.madie.terminology.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.*;
import gov.cms.madie.terminology.exceptions.HapiOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalTerminologyService {
  private final IGenericClient hapiClient;

  /**
   * This method fetches the ValueSet expansion by id from HAPI server
   *
   * @param url -> Value Set url
   * @return ValueSet -> Value Set with expansion details
   */
  public ValueSet getValueSetExpansionByUrl(String url) {
    try {
      log.info("Fetching ValueSet expansion for {}", url);
      Parameters parameters =
          hapiClient
              .operation()
              .onInstance(new IdType(new UriType(url)))
              .named("$expand")
              .withNoParameters(Parameters.class)
              .execute();
      return (ValueSet) parameters.getParameter().get(0).getResource();
    } catch (BaseServerResponseException ex) {
      log.error("An error occurred while fetching expansion for the ValueSet[{}]", url, ex);
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
