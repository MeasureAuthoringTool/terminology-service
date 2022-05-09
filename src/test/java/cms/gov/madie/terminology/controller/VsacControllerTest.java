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

import cms.gov.madie.terminology.dto.MadieValueSet;
import cms.gov.madie.terminology.service.VsacService;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse.DescribedValueSet;

@ExtendWith(MockitoExtension.class)
public class VsacControllerTest {

  @Mock private VsacService vsacService;

  @InjectMocks private VsacController vsacController;

  @Mock RetrieveMultipleValueSetsResponse mockValueset;
  @Mock DescribedValueSet mockDescribedValueset;
  @Mock MadieValueSet mockMadieValueSet;
  private static final String TEST = "test";

  @Test
  void testGetValueSet() throws Exception {
    when(vsacService.getServiceTicket(anyString())).thenReturn(TEST);
    when(vsacService.getValueSet(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockValueset);
    when(mockMadieValueSet.getID()).thenReturn(TEST);
    when(vsacService.convertToMadieValueSet(mockValueset, TEST)).thenReturn(mockMadieValueSet);
    ResponseEntity<MadieValueSet> response =
        vsacController.getValueSet(TEST, TEST, TEST, TEST, TEST, TEST);
    assertEquals(response.getStatusCode(), HttpStatus.OK);
    assertEquals(response.getBody().getID(), TEST);
  }

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
