package cms.gov.madie.terminology.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.hl7.fhir.r4.model.ValueSet;

import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import cms.gov.madie.terminology.service.VsacService;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse.DescribedValueSet;

@ExtendWith(MockitoExtension.class)
public class VsacControllerTest {

  @Mock private VsacService vsacService;

  @InjectMocks private VsacController vsacController;

  @Mock RetrieveMultipleValueSetsResponse mockVsacValueset;
  @Mock DescribedValueSet mockDescribedValueset;
  @Mock ValueSet mockFhirValueSet;
  private static final String TEST = "test";

  @Test
  void testGetValueSetFailWhenGettingServiceTicketFailed() throws Exception {
    doThrow(new WebClientResponseException(401, "Error", null, null, null))
        .when(vsacService)
        .getServiceTicket(anyString());
    assertThrows(
        WebClientResponseException.class,
        () -> vsacController.getValueSet(TEST, TEST, TEST, TEST, TEST, TEST));
  }

  @Test
  void testGetValueSetFailWhenGettingValueSetFailed() throws Exception {
    when(vsacService.getServiceTicket(anyString())).thenReturn(TEST);
    doThrow(new WebClientResponseException(401, "Error", null, null, null))
        .when(vsacService)
        .getValueSet(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    assertThrows(
        WebClientResponseException.class,
        () -> vsacController.getValueSet(TEST, TEST, TEST, TEST, TEST, TEST));
  }
}
