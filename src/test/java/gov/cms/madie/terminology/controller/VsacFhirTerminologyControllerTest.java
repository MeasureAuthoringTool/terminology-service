package gov.cms.madie.terminology.controller;

import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.QdmValueSet;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.exceptions.VsacUnauthorizedException;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import gov.cms.madie.terminology.service.VsacService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VsacFhirTerminologyControllerTest {
  private CodeSystemRepository codeSystemRepository;

  @Mock private VsacService vsacService;
  @Mock FhirTerminologyService fhirTerminologyService;

  @InjectMocks private VsacFhirTerminologyController vsacFhirTerminologyController;
  private UmlsUser umlsUser;
  private static final String TEST_USER = "test.user";
  private static final String ADMIN_TEST_API_KEY_HEADER = "api-key";
  private static final String ADMIN_TEST_API_KEY_HEADER_VALUE = "0a51991c";
  private static final String TEST_HARP_ID = "te$tHarpId";
  private static final String TEST_API_KEY = "te$tKey";
  MockHttpServletRequest request;
  private final List<ManifestExpansion> mockManifests = new ArrayList<>();
  private final List<QdmValueSet> mockQdmValueSets = new ArrayList<>();


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
    mockQdmValueSets.add(
        QdmValueSet.builder()
            .oid("test-value-set-id-1234")
            .concepts(List.of(QdmValueSet.Concept.builder().code("test-code-052").build()))
            .version("20240101")
            .displayName("test-value-set-display-name")
            .build());
    request = new MockHttpServletRequest();
  }

  @Test
  void testGetManifestsSuccessfully() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    when(vsacService.findByHarpId(anyString())).thenReturn(Optional.ofNullable(umlsUser));
    when(fhirTerminologyService.getManifests(any(UmlsUser.class))).thenReturn(mockManifests);
    ResponseEntity<List<ManifestExpansion>> response =
        vsacFhirTerminologyController.getManifests(principal);
    assertEquals(response.getStatusCode(), HttpStatus.OK);
    assertEquals(response.getBody(), mockManifests);
  }

  @Test
  void testUnAuthorizedUmlsUserWhileFetchingManifests() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    when(vsacService.findByHarpId(anyString()))
        .thenReturn(Optional.ofNullable(UmlsUser.builder().build()));
    assertThrows(
        VsacUnauthorizedException.class,
        () -> vsacFhirTerminologyController.getManifests(principal));
  }

  @Test
  void testGetValueSetsExpansionsSuccessfully() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    ValueSetsSearchCriteria valueSetsSearchCriteria = ValueSetsSearchCriteria.builder().build();
    when(vsacService.findByHarpId(anyString())).thenReturn(Optional.ofNullable(umlsUser));
    when(fhirTerminologyService.getValueSetsExpansionsForQdm(
            any(ValueSetsSearchCriteria.class), any(UmlsUser.class)))
        .thenReturn(mockQdmValueSets);
    ResponseEntity<List<QdmValueSet>> response =
        vsacFhirTerminologyController.getValueSetsExpansions(principal, valueSetsSearchCriteria);
    assertEquals(response.getStatusCode(), HttpStatus.OK);
    assertEquals(response.getBody(), mockQdmValueSets);
  }

  @Test
  void retrieveAndUpdateCodeSystemsSuccessfully() {
    List<CodeSystem> mockCodeSystemsPage = new ArrayList<>();
    mockCodeSystemsPage.add(
            CodeSystem.builder()
                    .id("titleversion")
                    .title("title")
                    .name("name")
                    .version("version")
                    .versionId("vid")
                    .oid("urlval")
                    .lastUpdated(Instant.now())
                    .lastUpdatedUpstream(new Date())
                    .build());
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    when(vsacService.findByHarpId(anyString())).thenReturn(Optional.ofNullable(umlsUser));
    when(fhirTerminologyService.retrieveAllCodeSystems(any())).thenReturn(mockCodeSystemsPage);

    ResponseEntity<List<CodeSystem>> response =
            vsacFhirTerminologyController.retrieveAndUpdateCodeSystems(principal, request, TEST_API_KEY, TEST_USER);
    assertEquals(response.getStatusCode(), HttpStatus.OK);
    assertEquals(response.getBody(), mockCodeSystemsPage);
  }
  @Test
  void testUnAuthorizedUmlsUserWhileFetchingValueSetsExpansions() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    when(vsacService.findByHarpId(anyString()))
        .thenReturn(Optional.ofNullable(UmlsUser.builder().build()));
    assertThrows(
        VsacUnauthorizedException.class,
        () -> vsacFhirTerminologyController.getManifests(principal));
  }
  @Test
  void testUnAuthorizedUmlsUserWhileretrievingAndUpdatingCodeSystems() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    when(vsacService.findByHarpId(anyString()))
            .thenReturn(Optional.ofNullable(UmlsUser.builder().build()));
    assertThrows(
            VsacUnauthorizedException.class,
            () -> vsacFhirTerminologyController.retrieveAndUpdateCodeSystems(principal, request, TEST_API_KEY, TEST_USER));
  }

  @Test
  void testGetAllCodeSystemsSuccessfully() {
    List<CodeSystem> mockCodeSystemsPage = new ArrayList<>();
    mockCodeSystemsPage.add(
            CodeSystem.builder()
                    .id("titleversion")
                    .title("title")
                    .name("name")
                    .version("version")
                    .versionId("vid")
                    .oid("urlval")
                    .lastUpdated(Instant.now())
                    .lastUpdatedUpstream(new Date())
                    .build());
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    when(fhirTerminologyService.getAllCodeSystems()).thenReturn(mockCodeSystemsPage);

    ResponseEntity<List<CodeSystem>> response =
            vsacFhirTerminologyController.getAllCodeSystems(principal);
    assertEquals(response.getStatusCode(), HttpStatus.OK);
    assertEquals(response.getBody(), mockCodeSystemsPage);
  }

}
