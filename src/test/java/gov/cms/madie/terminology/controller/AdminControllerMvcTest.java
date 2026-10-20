package gov.cms.madie.terminology.controller;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.audit.AdminAuditService;
import gov.cms.madie.terminology.audit.AdminAuditService.Action;
import gov.cms.madie.terminology.audit.AuditResource;
import gov.cms.madie.terminology.audit.AuditResource.ResourceType;
import gov.cms.madie.terminology.service.CodeSystemExportService;
import tools.jackson.databind.ObjectMapper;
import gov.cms.madie.terminology.clients.UserServiceClient;
import gov.cms.madie.terminology.config.SecurityConfig;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.VsacService;
import gov.cms.madie.terminology.exceptions.CodeSystemNotFoundException;
import gov.cms.madie.terminology.exceptions.DuplicateCodeSystemException;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.service.CodeSystemService;
import gov.cms.madie.terminology.service.CodeSystemSyncService;
import gov.cms.madie.terminology.task.UpdateCodeSystemTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.security.Principal;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AdminControllerMvcTest {

  private static final String TEST_USR = "FAKE";

  @MockitoBean private CodeSystemService codeSystemService;
  @MockitoBean private CodeSystemSyncService codeSystemSyncService;
  @MockitoBean private VsacService vsacService;
  @MockitoBean private FhirContext fhirContext;
  @MockitoBean private UpdateCodeSystemTask updateCodeSystemTask;
  @MockitoBean private UserServiceClient userServiceClient;
  @MockitoBean private CodeSystemExportService codeSystemExportService;
  @MockitoBean private AdminAuditService adminAuditService;
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private CodeSystem codeSystem;

  @BeforeEach
  void setUp() {
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
  void testRetrieveAndUpdateCodeSystemsSuccessfully() throws Exception {
    UmlsUser umlsUser = UmlsUser.builder().apiKey("te$tKey").harpId(TEST_USR).build();
    when(vsacService.verifyUmlsAccess(anyString())).thenReturn(umlsUser);
    when(codeSystemSyncService.remoteRetrieveAllCodeSystems(any(UmlsUser.class)))
        .thenReturn(List.of(codeSystem));

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/terminology/admin/update-code-systems")
                .with(user(TEST_USR).roles("MADIE-ADMIN"))
                .with(csrf()))
        .andExpect(status().isOk());

    verify(codeSystemSyncService, times(1)).remoteRetrieveAllCodeSystems(any(UmlsUser.class));
  }

  @Test
  void testRetrieveAndUpdateCodeSystemsForbiddenWithoutAdminRole() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/terminology/admin/update-code-systems")
                .with(user(TEST_USR))
                .with(csrf()))
        .andExpect(status().isForbidden());

    verify(codeSystemSyncService, never()).remoteRetrieveAllCodeSystems(any());
  }

  @Test
  void testCreateCodeSystemSuccessfully() throws Exception {
    when(codeSystemService.createCodeSystem(any(CodeSystem.class))).thenReturn(codeSystem);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/terminology/admin/code-system")
                    .with(user(TEST_USR).roles("MADIE-ADMIN"))
                    .with(csrf())
                    .content(objectMapper.writeValueAsString(codeSystem))
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

    assertThat(result.getResponse().getStatus(), is(equalTo(201)));
    verify(codeSystemService, times(1)).createCodeSystem(any(CodeSystem.class));
    verify(adminAuditService)
        .logAdminAction(
            any(Principal.class), eq(Action.CREATE), eq(AuditResource.from(codeSystem)));
  }

  @Test
  void testCreateCodeSystemForbiddenWithoutAdminRole() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/terminology/admin/code-system")
                .with(user(TEST_USR))
                .with(csrf())
                .content(objectMapper.writeValueAsString(codeSystem))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isForbidden());

    verify(codeSystemService, never()).createCodeSystem(any());
    verifyNoInteractions(adminAuditService);
  }

  @Test
  void testCreateCodeSystemMissingRequiredFields() throws Exception {
    CodeSystem invalid = CodeSystem.builder().name("LOINC").build();

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/terminology/admin/code-system")
                .with(user(TEST_USR).roles("MADIE-ADMIN"))
                .with(csrf())
                .content(objectMapper.writeValueAsString(invalid))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest());

    verify(codeSystemService, never()).createCodeSystem(any());
  }

  @Test
  void testCreateCodeSystemDuplicate() throws Exception {
    when(codeSystemService.createCodeSystem(any(CodeSystem.class)))
        .thenThrow(
            new DuplicateCodeSystemException(
                "CodeSystem with oid [urn:oid:2.16.840.1.113883.6.1] and fhir version [2.40] already exists"));

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/terminology/admin/code-system")
                    .with(user(TEST_USR).roles("MADIE-ADMIN"))
                    .with(csrf())
                    .content(objectMapper.writeValueAsString(codeSystem))
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertThat(result.getResponse().getStatus(), is(equalTo(400)));
    verify(codeSystemService, times(1)).createCodeSystem(any(CodeSystem.class));
  }

  @Test
  void testUpdateCodeSystemMissingRequiredFields() throws Exception {
    CodeSystem invalid = CodeSystem.builder().name("LOINC").build();

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/terminology/admin/code-system/" + codeSystem.getId())
                .with(user(TEST_USR).roles("MADIE-ADMIN"))
                .with(csrf())
                .content(objectMapper.writeValueAsString(invalid))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest());

    verify(codeSystemService, never()).updateCodeSystem(anyString(), any());
  }

  @Test
  void testUpdateCodeSystemSuccessfully() throws Exception {
    when(codeSystemService.updateCodeSystem(anyString(), any(CodeSystem.class)))
        .thenReturn(codeSystem);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/terminology/admin/code-system/" + codeSystem.getId())
                    .with(user(TEST_USR).roles("MADIE-ADMIN"))
                    .with(csrf())
                    .content(objectMapper.writeValueAsString(codeSystem))
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getStatus(), is(equalTo(200)));
    verify(codeSystemService, times(1)).updateCodeSystem(anyString(), any(CodeSystem.class));
    verify(adminAuditService)
        .logAdminAction(
            any(Principal.class), eq(Action.UPDATE), eq(AuditResource.from(codeSystem)));
  }

  @Test
  void testUpdateCodeSystemForbiddenWithoutAdminRole() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/terminology/admin/code-system/" + codeSystem.getId())
                .with(user(TEST_USR))
                .with(csrf())
                .content(objectMapper.writeValueAsString(codeSystem))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isForbidden());

    verify(codeSystemService, never()).updateCodeSystem(anyString(), any());
    verifyNoInteractions(adminAuditService);
  }

  @Test
  void testUpdateCodeSystemNotFound() throws Exception {
    when(codeSystemService.updateCodeSystem(anyString(), any(CodeSystem.class)))
        .thenThrow(new CodeSystemNotFoundException("CodeSystem not found for id: nonexistent"));

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/terminology/admin/code-system/nonexistent")
                    .with(user(TEST_USR).roles("MADIE-ADMIN"))
                    .with(csrf())
                    .content(objectMapper.writeValueAsString(codeSystem))
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent())
            .andReturn();

    assertThat(result.getResponse().getStatus(), is(equalTo(204)));
  }

  @Test
  void testDeleteCodeSystemSuccessfully() throws Exception {
    when(codeSystemService.deleteCodeSystem(anyString())).thenReturn(codeSystem);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.delete(
                        "/terminology/admin/code-system/" + codeSystem.getId())
                    .with(user(TEST_USR).roles("MADIE-ADMIN"))
                    .with(csrf()))
            .andExpect(status().isNoContent())
            .andReturn();

    assertThat(result.getResponse().getStatus(), is(equalTo(204)));
    verify(codeSystemService, times(1)).deleteCodeSystem(codeSystem.getId());
    verify(adminAuditService)
        .logAdminAction(
            any(Principal.class), eq(Action.DELETE), eq(AuditResource.from(codeSystem)));
  }

  @Test
  void testDeleteCodeSystemForbiddenWithoutAdminRole() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/terminology/admin/code-system/" + codeSystem.getId())
                .with(user(TEST_USR))
                .with(csrf()))
        .andExpect(status().isForbidden());

    verify(codeSystemService, never()).deleteCodeSystem(anyString());
    verifyNoInteractions(adminAuditService);
  }

  @Test
  void testDeleteCodeSystemNotFound() throws Exception {
    doThrow(new CodeSystemNotFoundException("CodeSystem not found for id: nonexistent"))
        .when(codeSystemService)
        .deleteCodeSystem(anyString());

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.delete("/terminology/admin/code-system/nonexistent")
                    .with(user(TEST_USR).roles("MADIE-ADMIN"))
                    .with(csrf()))
            .andExpect(status().isNoContent())
            .andReturn();

    assertThat(result.getResponse().getStatus(), is(equalTo(204)));
    verify(codeSystemService, times(1)).deleteCodeSystem("nonexistent");
  }

  @Test
  void testTriggerCodeSystemRefreshAuditsWhenJobStarts() throws Exception {
    when(updateCodeSystemTask.startJob()).thenReturn(true);

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/terminology/admin/trigger-code-system-refresh")
                .with(user(TEST_USR).roles("MADIE-ADMIN"))
                .with(csrf()))
        .andExpect(status().isOk());

    verify(adminAuditService)
        .logAdminAction(
            any(Principal.class),
            eq(Action.UPDATE),
            eq(
                AuditResource.bulk(
                    ResourceType.CODE_SYSTEM,
                    "Update Code Systems: started job to refresh all code systems from VSAC")));
  }

  @Test
  void testTriggerCodeSystemRefreshForbiddenWithoutAdminRole() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/terminology/admin/trigger-code-system-refresh")
                .with(user(TEST_USR))
                .with(csrf()))
        .andExpect(status().isForbidden());

    verify(updateCodeSystemTask, never()).startJob();
    verifyNoInteractions(adminAuditService);
  }
}
