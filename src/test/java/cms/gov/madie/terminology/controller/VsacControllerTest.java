package cms.gov.madie.terminology.controller;

import cms.gov.madie.terminology.exceptions.VsacGenericException;
import cms.gov.madie.terminology.service.VsacService;
import gov.cms.madiejavamodels.cql.terminology.CqlCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class VsacControllerTest {

  @Mock private VsacService vsacService;
  @InjectMocks private VsacController vsacController;

  private static final String TEST = "test";

  @Test
  void testGetValueSetFailWhenGettingServiceTicketFailed() {
    doThrow(new VsacGenericException("Error while getting ST"))
        .when(vsacService)
        .getValueSet(TEST, TEST, TEST, TEST, TEST, TEST);
    assertThrows(
        VsacGenericException.class,
        () -> vsacController.getValueSet(TEST, TEST, TEST, TEST, TEST, TEST));
  }

  @Test
  void testGetValueSetFailWhenGettingValueSetFailed() {
    doThrow(new WebClientResponseException(401, "Error", null, null, null))
        .when(vsacService)
        .getValueSet(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    assertThrows(
        WebClientResponseException.class,
        () -> vsacController.getValueSet(TEST, TEST, TEST, TEST, TEST, TEST));
  }

  @Test
  void testValidateCodes() {
    CqlCode cqlCode = CqlCode.builder().name("test-code").codeId("test-codeId").build();
    when(vsacService.validateCodes(any(), anyString())).thenReturn(List.of(cqlCode));
    cqlCode.setValid(true);
    ResponseEntity<List<CqlCode>> response =
        vsacController.validateCodes(List.of(cqlCode), "TGT-Token");
    assertEquals(1, Objects.requireNonNull(response.getBody()).size());
    assertEquals("test-code", response.getBody().get(0).getName());
    assertTrue(response.getBody().get(0).isValid());
  }
}
