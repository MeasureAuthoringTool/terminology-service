package cms.gov.madie.terminology.controller;

import cms.gov.madie.terminology.exceptions.VsacUnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import cms.gov.madie.terminology.service.VsacService;
import cms.gov.madie.terminology.models.UmlsUser;
import gov.cms.madie.models.cql.terminology.CqlCode;

@ExtendWith(MockitoExtension.class)
public class VsacControllerTest {

  @Mock private VsacService vsacService;
  @InjectMocks private VsacController vsacController;

  private static final String TEST = "test";
  private static final String TEST_USER = "test.user";

  @Test
  void testGetValueSetFailWhenGettingServiceTicketFailed() {

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    UmlsUser umlsUser = mock(UmlsUser.class);
    Optional<UmlsUser> optionalUmlsUser = Optional.of(umlsUser);
    when(umlsUser.getTgt()).thenReturn(TEST);
    when(vsacService.findByHarpId(anyString())).thenReturn(optionalUmlsUser);

    doThrow(new VsacUnauthorizedException("Error while getting ST"))
        .when(vsacService)
        .getValueSet(TEST, TEST, TEST, TEST, TEST, TEST);
    assertThrows(
        VsacUnauthorizedException.class,
        () -> vsacController.getValueSet(principal, TEST, TEST, TEST, TEST, TEST));
  }

  @Test
  void testGetValueSetFailWhenGettingValueSetFailed() {

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    UmlsUser mockUmlsUser = mock(UmlsUser.class);
    Optional<UmlsUser> optionalUmlsUser = Optional.of(mockUmlsUser);
    when(mockUmlsUser.getTgt()).thenReturn(TEST);
    when(vsacService.findByHarpId(anyString())).thenReturn(optionalUmlsUser);

    doThrow(new WebClientResponseException(401, "Error", null, null, null))
        .when(vsacService)
        .getValueSet(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    assertThrows(
        WebClientResponseException.class,
        () -> vsacController.getValueSet(principal, TEST, TEST, TEST, TEST, TEST));
  }

  @Test
  void testValidateCodes() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    UmlsUser mockUmlsUser = mock(UmlsUser.class);
    Optional<UmlsUser> optionalUmlsUser = Optional.of(mockUmlsUser);
    when(mockUmlsUser.getTgt()).thenReturn(TEST);
    when(mockUmlsUser.getApiKey()).thenReturn(TEST);
    when(vsacService.findByHarpId(anyString())).thenReturn(optionalUmlsUser);

    CqlCode cqlCode = CqlCode.builder().name("test-code").codeId("test-codeId").build();
    when(vsacService.validateCodes(any(), anyString())).thenReturn(List.of(cqlCode));
    cqlCode.setValid(true);
    ResponseEntity<List<CqlCode>> response =
        vsacController.validateCodes(principal, List.of(cqlCode));
    assertEquals(1, Objects.requireNonNull(response.getBody()).size());
    assertEquals("test-code", response.getBody().get(0).getName());
    assertTrue(response.getBody().get(0).isValid());
  }

  @Test
  void testUMLSLogin() throws InterruptedException, ExecutionException {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    when(vsacService.getTgt(anyString())).thenReturn(TEST);

    UmlsUser mockUmlsUser = mock(UmlsUser.class);
    when(mockUmlsUser.getHarpId()).thenReturn(TEST_USER);
    when(vsacService.saveUmlsUser(anyString(), anyString(), anyString())).thenReturn(mockUmlsUser);

    ResponseEntity<String> response = vsacController.umlsLogin(principal, TEST);

    assertEquals(response.getBody(), "User: " + TEST_USER + " successfully loggin in to UMLS.");
  }

  @Test
  void testCheckUserLogin() throws InterruptedException, ExecutionException {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    UmlsUser mockUmlsUser = mock(UmlsUser.class);
    Optional<UmlsUser> optionalUmlsUser = Optional.of(mockUmlsUser);

    when(mockUmlsUser.getTgt()).thenReturn(TEST);
    when(mockUmlsUser.getApiKey()).thenReturn(TEST);
    when(vsacService.findByHarpId(anyString())).thenReturn(optionalUmlsUser);

    ResponseEntity<Boolean> response = vsacController.checkUserLogin(principal);

    assertEquals(response.getBody(), Boolean.TRUE);
  }
}
