package gov.cms.madie.terminology.controller;

import gov.cms.madie.terminology.exceptions.CodeSystemNotFoundException;
import gov.cms.madie.terminology.exceptions.DuplicateCodeSystemException;
import gov.cms.madie.terminology.exceptions.VsacUnauthorizedException;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import gov.cms.madie.terminology.service.VsacService;
import gov.cms.madie.terminology.task.UpdateCodeSystemTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

  @Mock private FhirTerminologyService fhirTerminologyService;
  @Mock private UpdateCodeSystemTask updateCodeSystemTask;
  @Mock private VsacService vsacService;
  @Mock private CacheManager cacheManager;
  @Mock private Cache cache;
  @InjectMocks private AdminController adminController;

  private static final String TEST_USER = "test.admin.user";

  private Principal principal;
  private CodeSystem codeSystem;

  @BeforeEach
  void setUp() {
    principal = mock(Principal.class);

    codeSystem =
        CodeSystem.builder()
            .id("LOINCversion2.40")
            .title("LOINC")
            .name("LOINC")
            .version(CodeSystem.Version.builder().fhirVersion("2.40").build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .versionId("1")
            .lastUpdated(Instant.now())
            .lastUpdatedUpstream(new Date())
            .build();
  }

  @Test
  void testRetrieveAndUpdateCodeSystemsSuccessfully() {
    when(principal.getName()).thenReturn(TEST_USER);
    UmlsUser umlsUser = UmlsUser.builder().apiKey("te$tKey").harpId(TEST_USER).build();
    when(vsacService.verifyUmlsAccess(TEST_USER)).thenReturn(umlsUser);
    when(fhirTerminologyService.retrieveAllCodeSystems(umlsUser)).thenReturn(List.of(codeSystem));

    ResponseEntity<List<CodeSystem>> response =
        adminController.retrieveAndUpdateCodeSystems(principal);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(List.of(codeSystem), response.getBody());
  }

  @Test
  void testRetrieveAndUpdateCodeSystemsUnauthorizedUmlsUser() {
    when(principal.getName()).thenReturn(TEST_USER);
    doThrow(new VsacUnauthorizedException("Please login to UMLS before proceeding"))
        .when(vsacService)
        .verifyUmlsAccess(anyString());

    assertThrows(
        VsacUnauthorizedException.class,
        () -> adminController.retrieveAndUpdateCodeSystems(principal));
  }

  @Test
  void testCreateCodeSystemSuccessfully() {
    when(principal.getName()).thenReturn(TEST_USER);
    when(fhirTerminologyService.createCodeSystem(any(CodeSystem.class))).thenReturn(codeSystem);

    ResponseEntity<CodeSystem> response = adminController.createCodeSystem(principal, codeSystem);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals(codeSystem, response.getBody());
    verify(fhirTerminologyService, times(1)).createCodeSystem(any(CodeSystem.class));
  }

  @Test
  void testCreateCodeSystemDuplicate() {
    when(principal.getName()).thenReturn(TEST_USER);
    when(fhirTerminologyService.createCodeSystem(any(CodeSystem.class)))
        .thenThrow(
            new DuplicateCodeSystemException(
                "CodeSystem with oid [urn:oid:2.16.840.1.113883.6.1] and fhir version [2.40] already exists"));

    assertThrows(
        DuplicateCodeSystemException.class,
        () -> adminController.createCodeSystem(principal, codeSystem));
  }

  @Test
  void testUpdateCodeSystemSuccessfully() {
    when(principal.getName()).thenReturn(TEST_USER);
    when(fhirTerminologyService.updateCodeSystem(anyString(), any(CodeSystem.class)))
        .thenReturn(codeSystem);

    ResponseEntity<CodeSystem> response =
        adminController.updateCodeSystem(principal, codeSystem.getId(), codeSystem);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(codeSystem, response.getBody());
    verify(fhirTerminologyService, times(1)).updateCodeSystem(anyString(), any(CodeSystem.class));
  }

  @Test
  void testUpdateCodeSystemNotFound() {
    when(principal.getName()).thenReturn(TEST_USER);
    when(fhirTerminologyService.updateCodeSystem(anyString(), any(CodeSystem.class)))
        .thenThrow(new CodeSystemNotFoundException("CodeSystem not found for id: nonexistent"));

    assertThrows(
        CodeSystemNotFoundException.class,
        () -> adminController.updateCodeSystem(principal, "nonexistent", codeSystem));
  }

  @Test
  void testDeleteCodeSystemSuccessfully() {
    when(principal.getName()).thenReturn(TEST_USER);
    doNothing().when(fhirTerminologyService).deleteCodeSystem(anyString());

    ResponseEntity<Void> response = adminController.deleteCodeSystem(principal, codeSystem.getId());

    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    verify(fhirTerminologyService, times(1)).deleteCodeSystem(codeSystem.getId());
  }

  @Test
  void testDeleteCodeSystemNotFound() {
    when(principal.getName()).thenReturn(TEST_USER);
    doThrow(new CodeSystemNotFoundException("CodeSystem not found for id: nonexistent"))
        .when(fhirTerminologyService)
        .deleteCodeSystem(anyString());

    assertThrows(
        CodeSystemNotFoundException.class,
        () -> adminController.deleteCodeSystem(principal, "nonexistent"));
  }

  @Test
  void testEvictAllCachesReturnsOkWithCacheNames() {
    when(principal.getName()).thenReturn(TEST_USER);
    when(cacheManager.getCacheNames()).thenReturn(Set.of("manifest-list"));
    when(cacheManager.getCache(anyString())).thenReturn(cache);

    ResponseEntity<List<String>> response = adminController.evictAllCaches(principal);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().size());
    assertTrue(response.getBody().contains("manifest-list"));
    verify(cacheManager, times(1)).getCache(anyString());
    verify(cache, times(1)).clear();
  }

  @Test
  void testTriggerCodeSystemRefreshStarted() {
    when(updateCodeSystemTask.isRunning()).thenReturn(false);

    ResponseEntity<String> response = adminController.triggerCodeSystemRefresh();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("Code system refresh has been started", response.getBody());

    verify(updateCodeSystemTask, times(1)).isRunning();
  }

  @Test
  void testTriggerCodeSystemRefreshConflict() {
    when(updateCodeSystemTask.isRunning()).thenReturn(true);

    ResponseEntity<String> response = adminController.triggerCodeSystemRefresh();

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    assertEquals(
            "Update Code System is already running. We have NOT started the job again",
            response.getBody()
    );

    verify(updateCodeSystemTask, times(1)).isRunning();
  }

}
