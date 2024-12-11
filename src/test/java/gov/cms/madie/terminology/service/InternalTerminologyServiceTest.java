package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.*;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.util.OperationOutcomeUtil;
import gov.cms.madie.terminology.exceptions.HapiOperationException;
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InternalTerminologyServiceTest {

  @Mock private IGenericClient hapiClient;

  @InjectMocks private InternalTerminologyService service;

  private IOperation operation;
  private IOperationUnnamed operationUnnamed;
  private IOperationUntyped operationUntyped;
  private IOperationUntypedWithInput operationUntypedWithInput;

  @BeforeEach
  public void setUp() {
    operation = mock(IOperation.class);
    operationUnnamed = mock(IOperationUnnamed.class);
    operationUntyped = mock(IOperationUntyped.class);
    operationUntypedWithInput =
        (IOperationUntypedWithInput<Parameters>) mock(IOperationUntypedWithInput.class);
  }

  @Test
  void testGetValueSetExpansionById() {
    String valueSetId = "us-core-vaccines-cvx-1";
    var idType = new IdType("ValueSet", valueSetId);

    // mock hapi client operation
    when(hapiClient.operation()).thenReturn(operation);
    when(operation.onInstance(idType)).thenReturn(operationUnnamed);
    when(operationUnnamed.named("$expand")).thenReturn(operationUntyped);
    when(operationUntyped.withNoParameters(Parameters.class)).thenReturn(operationUntypedWithInput);

    // mock response
    ValueSet valueSet = new ValueSet();
    Parameters parameters = new Parameters();
    parameters.addParameter().setResource(valueSet);
    when(operationUntypedWithInput.execute()).thenReturn(parameters);

    ValueSet expansion = service.getValueSetExpansionById(valueSetId);
    assertThat(expansion, is(not(nullValue())));
  }

  @Test
  void testGetValueSetExpansionByIdIfValueSetNotFound() {
    String valueSetId = "us-core-vaccines-cvx-1";
    var idType = new IdType("ValueSet", valueSetId);
    FhirContext fhirContext = FhirContext.forR4();
    // mock hapi client operation
    when(hapiClient.operation()).thenReturn(operation);
    when(operation.onInstance(idType)).thenReturn(operationUnnamed);
    when(operationUnnamed.named("$expand")).thenReturn(operationUntyped);
    when(operationUntyped.withNoParameters(Parameters.class)).thenReturn(operationUntypedWithInput);

    // mock response
    IBaseOperationOutcome operationOutcome = OperationOutcomeUtil.newInstance(fhirContext);
    OperationOutcomeUtil.addIssue(
        fhirContext, operationOutcome, "warning", "Resource not found", null, null);
    ResourceNotFoundException resourceNotFoundException = new ResourceNotFoundException(idType);
    resourceNotFoundException.setOperationOutcome(operationOutcome);
    doThrow(resourceNotFoundException).when(operationUntypedWithInput).execute();

    Exception ex =
        Assertions.assertThrows(
            HapiOperationException.class, () -> service.getValueSetExpansionById(valueSetId));
    assertThat(ex.getMessage(), is(equalTo("Resource not found")));
  }
}
