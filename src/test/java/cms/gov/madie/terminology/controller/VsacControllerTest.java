package cms.gov.madie.terminology.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import cms.gov.madie.terminology.service.VsacService;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse.DescribedValueSet;

@ExtendWith(MockitoExtension.class)
public class VsacControllerTest {

  @Mock private VsacService vsacService;

  @InjectMocks private VsacController vsacController;

  @Mock RetrieveMultipleValueSetsResponse mockValueset;
  @Mock DescribedValueSet mockDescribedValueset;
  private static final String TEST = "test";

  @Test
  void testGetServiceTicket() throws Exception {
    when(vsacService.getServiceTicket(anyString())).thenReturn(anyString());
    ResponseEntity<String> response = vsacController.getServiceTicket(TEST);
    assertEquals(response.getStatusCode(), HttpStatus.OK);
  }

  @Test
  void testGetServiceTicketFail() throws Exception {
    doThrow(new WebClientResponseException(401, "Error", null, null, null))
        .when(vsacService)
        .getServiceTicket(anyString());
    assertThrows(WebClientResponseException.class, () -> vsacController.getServiceTicket(TEST));
  }

  @Test
  void testGetValueSet() throws Exception {
    when(mockValueset.getDescribedValueSet()).thenReturn(mockDescribedValueset);
    when(mockDescribedValueset.getDisplayName()).thenReturn(TEST);
    when(vsacService.getValueSet(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockValueset);
    ResponseEntity<RetrieveMultipleValueSetsResponse> response =
        vsacController.getValueSet(TEST, TEST, TEST, TEST, TEST, TEST);
    assertEquals(response.getStatusCode(), HttpStatus.OK);
    assertEquals(response.getBody().getDescribedValueSet().getDisplayName(), TEST);
  }

  @Test
  void testGetValueSetFail() throws Exception {
    doThrow(new WebClientResponseException(401, "Error", null, null, null))
        .when(vsacService)
        .getValueSet(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    assertThrows(
        WebClientResponseException.class,
        () -> vsacController.getValueSet(TEST, TEST, TEST, TEST, TEST, TEST));
  }
}
