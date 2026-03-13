package gov.cms.madie.terminology.controller;

import gov.cms.madie.terminology.exceptions.CodeSystemNotFoundException;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

  @Mock private FhirTerminologyService fhirTerminologyService;
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
  void testCreateCodeSystemSuccessfully() {
    when(principal.getName()).thenReturn(TEST_USER);
    when(fhirTerminologyService.createCodeSystem(any(CodeSystem.class))).thenReturn(codeSystem);

    ResponseEntity<CodeSystem> response = adminController.createCodeSystem(principal, codeSystem);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals(codeSystem, response.getBody());
    verify(fhirTerminologyService, times(1)).createCodeSystem(any(CodeSystem.class));
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
}
