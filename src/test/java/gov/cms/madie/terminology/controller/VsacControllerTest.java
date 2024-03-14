package gov.cms.madie.terminology.controller;

import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.exceptions.VsacUnauthorizedException;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.ArrayList;
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
  @Mock private FhirTerminologyService fhirTerminologyService;
  @InjectMocks private VsacController vsacController;

  private static final String TEST = "test";
  private static final String TEST_USER = "test.user";

  private UmlsUser umlsUser;

  private static final String TEST_HARP_ID = "te$tHarpId";
  private static final String TEST_API_KEY = "te$tKey";
  private static final String FHIR_DATA_MODEL = "FHIR";

  private final List<ManifestExpansion> mockManifests = new ArrayList<>();

  @BeforeEach
  public void setUp() {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    mockManifests.add(
        ManifestExpansion.builder()
            .fullUrl("https://cts.nlm.nih.gov/fhir/Library/ecqm-update-4q2017-eh")
            .id("ecqm-update-4q2017-eh")
            .build());
    mockManifests.add(
        ManifestExpansion.builder()
            .fullUrl("https://cts.nlm.nih.gov/fhir/Library/mu2-update-2012-10-25")
            .id("mu2-update-2012-10-25")
            .build());
  }

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

    assertEquals(response.getBody(), "User: " + TEST_USER + " is successfully logged in to UMLS.");
  }

  @Test
  void testValidUserUmlsLogin() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    when(vsacService.validateUmlsInformation(anyString())).thenReturn(true);
    ResponseEntity<Boolean> response = vsacController.checkUserLogin(principal);

    assertEquals(response.getBody(), Boolean.TRUE);
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
  void testGetManifestsSuccessfully() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    when(vsacService.findByHarpId(anyString())).thenReturn(Optional.ofNullable(umlsUser));
    when(fhirTerminologyService.getManifests(any(UmlsUser.class))).thenReturn(mockManifests);
    ResponseEntity<List<ManifestExpansion>> response = vsacController.getManifests(principal);
    assertEquals(response.getStatusCode(), HttpStatus.OK);
    assertEquals(response.getBody(), mockManifests);
  }

  @Test
  void testUnAuthorizedUmlsUserWhileFetchingManifests() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    when(vsacService.findByHarpId(anyString()))
        .thenReturn(Optional.ofNullable(UmlsUser.builder().build()));
    assertThrows(VsacUnauthorizedException.class, () -> vsacController.getManifests(principal));
  }
}
