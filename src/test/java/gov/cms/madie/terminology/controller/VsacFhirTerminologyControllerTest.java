package gov.cms.madie.terminology.controller;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.*;
import gov.cms.madie.terminology.exceptions.VsacUnauthorizedException;
import gov.cms.madie.terminology.helpers.TestHelpers;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import gov.cms.madie.terminology.service.VsacService;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.util.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VsacFhirTerminologyControllerTest {
  private CodeSystemRepository codeSystemRepository;

  @Mock private VsacService vsacService;
  @Mock private FhirTerminologyService fhirTerminologyService;
  @Mock private FhirContext fhirContext;
  @InjectMocks private VsacFhirTerminologyController vsacFhirTerminologyController;
  private UmlsUser umlsUser;
  private static final String TEST_USER = "test.user";
  private static final String TEST_HARP_ID = "te$tHarpId";
  private static final String TEST_API_KEY = "te$tKey";
  private final List<ManifestExpansion> mockManifests = new ArrayList<>();
  private final List<QdmValueSet> mockQdmValueSets = new ArrayList<>();
  private Principal principal;

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
    principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
  }

  @Test
  void testGetManifestsSuccessfully() {
    when(vsacService.verifyUmlsAccess(anyString())).thenReturn(umlsUser);
    when(fhirTerminologyService.getManifests(any(UmlsUser.class))).thenReturn(mockManifests);
    ResponseEntity<List<ManifestExpansion>> response =
        vsacFhirTerminologyController.getManifests(principal);
    assertEquals(response.getStatusCode(), HttpStatus.OK);
    assertEquals(response.getBody(), mockManifests);
  }

  @Test
  void testUnAuthorizedUmlsUserWhileFetchingManifests() {
    doThrow(new VsacUnauthorizedException("Please login to UMLS before proceeding"))
        .when(vsacService)
        .verifyUmlsAccess(anyString());
    assertThrows(
        VsacUnauthorizedException.class,
        () -> vsacFhirTerminologyController.getManifests(principal));
  }

  @Test
  void testGetValueSetsExpansionsSuccessfully() {
    ValueSetsSearchCriteria valueSetsSearchCriteria = ValueSetsSearchCriteria.builder().build();
    when(vsacService.verifyUmlsAccess(anyString())).thenReturn(umlsUser);
    when(fhirTerminologyService.getValueSetsExpansionsForQdm(
            any(ValueSetsSearchCriteria.class), any(UmlsUser.class)))
        .thenReturn(mockQdmValueSets);
    ResponseEntity<List<QdmValueSet>> response =
        vsacFhirTerminologyController.getQdmValueSetsExpansions(principal, valueSetsSearchCriteria);
    assertEquals(response.getStatusCode(), HttpStatus.OK);
    assertEquals(response.getBody(), mockQdmValueSets);
  }

  @Test
  void testUnAuthorizedUmlsUserWhileFetchingValueSetsExpansions() {
    doThrow(new VsacUnauthorizedException("Please login to UMLS before proceeding"))
        .when(vsacService)
        .verifyUmlsAccess(anyString());
    assertThrows(
        VsacUnauthorizedException.class,
        () -> vsacFhirTerminologyController.getManifests(principal));
  }

  @Test
  void testGetAllCodeSystemsSuccessfully() {
    List<CodeSystem> mockCodeSystemsPage = new ArrayList<>();
    mockCodeSystemsPage.add(
        CodeSystem.builder()
            .id("titleversion")
            .title("title")
            .name("name")
            .version(CodeSystem.Version.builder().fhirVersion("version").build())
            .versionId("vid")
            .oid("urlval")
            .lastUpdated(Instant.now())
            .lastUpdatedUpstream(new Date())
            .build());
    when(fhirTerminologyService.getAllCodeSystems()).thenReturn(mockCodeSystemsPage);

    ResponseEntity<List<CodeSystem>> response =
        vsacFhirTerminologyController.getAllCodeSystems(principal);
    assertEquals(response.getStatusCode(), HttpStatus.OK);
    assertEquals(response.getBody(), mockCodeSystemsPage);
  }

  @Test
  void testGetCode() {
    String codeName = "1963-8";
    String codeSystem = "LOINC";
    String version = "2.40";
    Code code =
        Code.builder()
            .name(codeName)
            .codeSystem(codeSystem)
            .svsVersion(version)
            .fhirVersion(version)
            .display("Bicarbonate [Moles/volume] in Serum")
            .codeSystemOid("2.16.840.1.113883.6.1")
            .build();
    when(vsacService.verifyUmlsAccess(anyString())).thenReturn(umlsUser);
    when(fhirTerminologyService.retrieveCode(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(code);

    ResponseEntity<Code> response =
        vsacFhirTerminologyController.getCode(codeName, codeSystem, version, principal);
    assertEquals(response.getStatusCode(), HttpStatus.OK);
    assertEquals(response.getBody(), code);
  }

  @Test
  void testGetCodeIfNoUmlsUserFound() {
    String codeName = "1963-8";
    String codeSystem = "LOINC";
    String version = "2.40";
    doThrow(new VsacUnauthorizedException("Please login to UMLS before proceeding"))
        .when(vsacService)
        .verifyUmlsAccess(anyString());

    Exception ex =
        assertThrows(
            VsacUnauthorizedException.class,
            () -> vsacFhirTerminologyController.getCode(codeName, codeSystem, version, principal));
    assertEquals(ex.getMessage(), "Please login to UMLS before proceeding");
  }

  @Test
  void testGetCodesList() {
    List<Map<String, String>> codeList =
        List.of(
            Map.of(
                "code", "1963-8", "codeSystem", "LOINC", "oid", "'urn:oid:2.16.840.1.113883.6.1'"),
            Map.of(
                "code", "8462-4", "codeSystem", "LOINC", "oid", "'urn:oid:2.16.840.1.113883.6.1'"));
    Code code =
        Code.builder()
            .name("1963-8")
            .codeSystem("LOINC")
            .svsVersion("2.72")
            .fhirVersion("2.72")
            .display("Bicarbonate [Moles/volume] in Serum")
            .codeSystemOid("2.16.840.1.113883.6.1")
            .codeSystemUrl("https://loinc.org")
            .status(CodeStatus.valueOf("ACTIVE"))
            .build();
    when(vsacService.verifyUmlsAccess(anyString())).thenReturn(umlsUser);
    when(fhirTerminologyService.retrieveCodesAndCodeSystems(any(), anyString()))
        .thenReturn(List.of(code));
    ResponseEntity<List<Code>> response =
        vsacFhirTerminologyController.getCodesAndCodeSystems(codeList, principal);
    assertEquals(response.getStatusCode(), HttpStatus.OK);
    assertEquals(response.getBody().get(0), code);
  }

  @Test
  void testSearchValueSets() {
    List<ValueSetForSearch> mockValueSets = new ArrayList<>();
    ValueSetForSearch v1 =
        ValueSetForSearch.builder()
            .title("title 1")
            .name("title1")
            .url("url")
            .oid("oid")
            .steward("steward")
            .version("version")
            .codeSystem("cs")
            .build();
    ValueSetForSearch v2 =
        ValueSetForSearch.builder()
            .title("title 2")
            .name("title2")
            .url("url")
            .oid("oid")
            .steward("steward")
            .version("version")
            .codeSystem("cs")
            .build();
    mockValueSets.add(v1);
    mockValueSets.add(v2);
    when(vsacService.verifyUmlsAccess(anyString())).thenReturn(umlsUser);
    when(fhirTerminologyService.searchValueSets(any(), any()))
        .thenReturn(ValueSetSearchResult.builder().valueSets(mockValueSets).build());
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("param1", "value1");
    queryParams.put("param2", "value2");
    ResponseEntity<ValueSetSearchResult> response =
        vsacFhirTerminologyController.searchValueSets(principal, queryParams);
    assertEquals(response.getStatusCode(), HttpStatus.OK);
  }

  @Test
  void testGetFhirValueSetsExpansions() throws IOException {
    Bundle bundle =
        TestHelpers.getFhirTestResource(
            "/value-sets/value_set_with_expansion_codes.json", Bundle.class);
    List<ValueSet> valueSet =
        bundle.getEntry().stream()
            .map(bundleEntryComponent -> (ValueSet) bundleEntryComponent.getResource())
            .toList();
    when(vsacService.verifyUmlsAccess(anyString())).thenReturn(umlsUser);
    when(fhirTerminologyService.getFhirValueSetsExpansion(
            any(ValueSetsSearchCriteria.class), any(UmlsUser.class)))
        .thenReturn(
            Collections.singletonList(
                FhirContext.forR4().newJsonParser().encodeResourceToString(valueSet.get(0))));

    ResponseEntity<String> response =
        vsacFhirTerminologyController.getFhirValueSetsExpansions(
            principal, ValueSetsSearchCriteria.builder().build());
    assertThat(response.getStatusCode(), is(equalTo(HttpStatus.OK)));
    assertThat(response.getBody().contains("\"resourceType\":\"ValueSet\""), is(true));
    assertThat(
        response.getBody().contains("\"url\":\"" + valueSet.get(0).getUrl() + "\""), is(true));
  }
}
