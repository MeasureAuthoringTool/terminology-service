package gov.cms.madie.terminology.controller;

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

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import gov.cms.madie.models.cql.terminology.CqlCode;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.VsacService;

@ExtendWith(MockitoExtension.class)
public class VsacControllerTest {
  @Mock private VsacService vsacService;

  @InjectMocks private VsacController vsacController;

  private static final String TEST = "test";
  private static final String TEST_USER = "test.user";
  private static final String FHIR_DATA_MODEL = "FHIR";

  @Test
  void testGetValueSetFailWhenGettingValueSetFailed() {

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    UmlsUser mockUmlsUser = mock(UmlsUser.class);
    Optional<UmlsUser> optionalUmlsUser = Optional.of(mockUmlsUser);
    when(vsacService.findByHarpId(anyString())).thenReturn(optionalUmlsUser);

    doThrow(new WebClientResponseException(401, "Error", null, null, null))
        .when(vsacService)
        .getValueSet(anyString(), any(), anyString(), anyString(), anyString(), anyString());
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
    when(mockUmlsUser.getApiKey()).thenReturn(TEST);
    when(vsacService.findByHarpId(anyString())).thenReturn(optionalUmlsUser);

    CqlCode cqlCode = CqlCode.builder().name("test-code").codeId("test-codeId").build();
    when(vsacService.validateCodes(any(), any(), anyString())).thenReturn(List.of(cqlCode));
    cqlCode.setValid(true);
    ResponseEntity<List<CqlCode>> response =
        vsacController.validateCodes(principal, List.of(cqlCode), FHIR_DATA_MODEL);
    assertEquals(1, Objects.requireNonNull(response.getBody()).size());
    assertEquals("test-code", response.getBody().get(0).getName());
    assertTrue(response.getBody().get(0).isValid());
  }

  @Test
  void testValidateCodesWhenUserIsNotLoggedIntoUmls() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    UmlsUser mockUmlsUser = mock(UmlsUser.class);
    when(vsacService.findByHarpId(anyString())).thenReturn(Optional.of(mockUmlsUser));
    when(mockUmlsUser.getApiKey()).thenReturn(null);
    var cqlCode = CqlCode.builder().name("test-code").codeId("test-codeId").build();
    ResponseEntity<List<CqlCode>> response =
        vsacController.validateCodes(principal, List.of(cqlCode), FHIR_DATA_MODEL);
    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
  }

  @Test
  void testRetrieveValueSetWhenUserIsNotLoggedIntoUmls() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    when(vsacService.findByHarpId(anyString())).thenReturn(Optional.empty());
    ResponseEntity<String> response = vsacController.getValueSet(principal, "oid", "", "", "", "");
    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
  }

  @Test
  void testUMLSLogin() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    UmlsUser mockUmlsUser = mock(UmlsUser.class);
    when(mockUmlsUser.getHarpId()).thenReturn(TEST_USER);
    when(vsacService.saveUmlsUser(anyString(), anyString())).thenReturn(mockUmlsUser);

    ResponseEntity<String> response = vsacController.umlsLogin(principal, TEST);

    assertEquals("User: " + TEST_USER + " is successfully logged in to UMLS.", response.getBody());
  }

  @Test
  void testValidUserUmlsLogin() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    when(vsacService.validateUmlsInformation(anyString())).thenReturn(true);
    ResponseEntity<Boolean> response = vsacController.checkUserLogin(principal);

    assertEquals(Boolean.TRUE, response.getBody());
  }

  @Test
  void testInvalidUserUmlsLogin() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    when(vsacService.validateUmlsInformation(anyString())).thenReturn(false);
    ResponseEntity<Boolean> response = vsacController.checkUserLogin(principal);

    assertEquals(response.getStatusCode(), HttpStatus.UNAUTHORIZED);
  }

  @Test
  void testUserUmlsLogout() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    when(vsacService.logoutUMLSUser(anyString())).thenReturn(true);
    ResponseEntity<Boolean> response = vsacController.umlsLogout(principal);

    assertEquals(Boolean.TRUE, response.getBody());
  }

  @Test
  void testUserUmlsLogoutFailed() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    when(vsacService.logoutUMLSUser(anyString())).thenReturn(false);
    ResponseEntity<Boolean> response = vsacController.umlsLogout(principal);

    assertEquals(response.getStatusCode(), HttpStatus.UNAUTHORIZED);
  }

  @Test
  void testGetValueSetSuccessCoversServiceChain() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    UmlsUser mockUmlsUser = mock(UmlsUser.class);
    Optional<UmlsUser> optionalUmlsUser = Optional.of(mockUmlsUser);
    when(vsacService.findByHarpId(anyString())).thenReturn(optionalUmlsUser);

    // Mock RetrieveMultipleValueSetsResponse
    generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse valuesetResponse =
        mock(generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse.class);
    when(vsacService.getValueSet(
            anyString(), any(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(valuesetResponse);

    // Mock FHIR ValueSet
    org.hl7.fhir.r4.model.ValueSet fhirValueSet = mock(org.hl7.fhir.r4.model.ValueSet.class);
    when(fhirValueSet.getId()).thenReturn("test-id");
    when(vsacService.convertToFHIRValueSet(valuesetResponse)).thenReturn(fhirValueSet);

    // Mock serialization
    VsacController controller =
        new VsacController(vsacService, ca.uhn.fhir.context.FhirContext.forR4());
    String expectedSerialized = "{\"resourceType\":\"ValueSet\",\"id\":\"test-id\"}";
    // Override serializeFhirValueset to return expectedSerialized
    VsacController spyController = org.mockito.Mockito.spy(controller);
    org.mockito.Mockito.doReturn(expectedSerialized)
        .when(spyController)
        .serializeFhirValueset(fhirValueSet);

    ResponseEntity<String> response =
        spyController.getValueSet(
            principal, "oid", "profile", "includeDraft", "release", "version");
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(expectedSerialized, response.getBody());
  }

  @Test
  void testSerializeFhirValuesetCoversJsonParser() {
    // Create a minimal ValueSet
    org.hl7.fhir.r4.model.ValueSet valueSet = new org.hl7.fhir.r4.model.ValueSet();
    valueSet.setId("test-id");
    // Use a real FhirContext for serialization
    ca.uhn.fhir.context.FhirContext fhirContext = ca.uhn.fhir.context.FhirContext.forR4();
    VsacController controller = new VsacController(vsacService, fhirContext);
    String json = controller.serializeFhirValueset(valueSet);
    // Assert output contains expected fields
    assertTrue(json.contains("\"resourceType\":\"ValueSet\""));
    assertTrue(json.contains("\"id\":\"test-id\""));
  }

  @Test
  void testValidateCodesWhenUmlsUserIsNotPresent() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    when(vsacService.findByHarpId(anyString())).thenReturn(Optional.empty());
    var cqlCode =
        gov.cms.madie.models.cql.terminology.CqlCode.builder()
            .name("test-code")
            .codeId("test-codeId")
            .build();
    ResponseEntity<List<CqlCode>> response =
        vsacController.validateCodes(principal, List.of(cqlCode), FHIR_DATA_MODEL);
    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
  }
}
