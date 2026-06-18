package gov.cms.madie.terminology.controller;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.terminology.clients.UserServiceClient;
import gov.cms.madie.terminology.config.SecurityConfig;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.VsacService;
import gov.cms.madie.terminology.exceptions.CodeSystemNotFoundException;
import gov.cms.madie.terminology.exceptions.DuplicateCodeSystemException;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

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

  @MockitoBean private FhirTerminologyService fhirTerminologyService;
  @MockitoBean private VsacService vsacService;
  @MockitoBean private FhirContext fhirContext;
  @MockitoBean private UserServiceClient userServiceClient;
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
    when(fhirTerminologyService.retrieveAllCodeSystems(any(UmlsUser.class)))
        .thenReturn(List.of(codeSystem));

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/terminology/admin/update-code-systems")
                .with(user(TEST_USR).roles("MADIE-ADMIN"))
                .with(csrf()))
        .andExpect(status().isOk());

    verify(fhirTerminologyService, times(1)).retrieveAllCodeSystems(any(UmlsUser.class));
  }

  @Test
  void testRetrieveAndUpdateCodeSystemsForbiddenWithoutAdminRole() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/terminology/admin/update-code-systems")
                .with(user(TEST_USR))
                .with(csrf()))
        .andExpect(status().isForbidden());

    verify(fhirTerminologyService, never()).retrieveAllCodeSystems(any());
  }

  @Test
  void testCreateCodeSystemSuccessfully() throws Exception {
    when(fhirTerminologyService.createCodeSystem(any(CodeSystem.class))).thenReturn(codeSystem);

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
    verify(fhirTerminologyService, times(1)).createCodeSystem(any(CodeSystem.class));
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

    verify(fhirTerminologyService, never()).createCodeSystem(any());
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

    verify(fhirTerminologyService, never()).createCodeSystem(any());
  }

  @Test
  void testCreateCodeSystemDuplicate() throws Exception {
    when(fhirTerminologyService.createCodeSystem(any(CodeSystem.class)))
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
    verify(fhirTerminologyService, times(1)).createCodeSystem(any(CodeSystem.class));
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

    verify(fhirTerminologyService, never()).updateCodeSystem(anyString(), any());
  }

  @Test
  void testUpdateCodeSystemSuccessfully() throws Exception {
    when(fhirTerminologyService.updateCodeSystem(anyString(), any(CodeSystem.class)))
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
    verify(fhirTerminologyService, times(1)).updateCodeSystem(anyString(), any(CodeSystem.class));
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

    verify(fhirTerminologyService, never()).updateCodeSystem(anyString(), any());
  }

  @Test
  void testUpdateCodeSystemNotFound() throws Exception {
    when(fhirTerminologyService.updateCodeSystem(anyString(), any(CodeSystem.class)))
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
    doNothing().when(fhirTerminologyService).deleteCodeSystem(anyString());

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
    verify(fhirTerminologyService, times(1)).deleteCodeSystem(codeSystem.getId());
  }

  @Test
  void testDeleteCodeSystemForbiddenWithoutAdminRole() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/terminology/admin/code-system/" + codeSystem.getId())
                .with(user(TEST_USR))
                .with(csrf()))
        .andExpect(status().isForbidden());

    verify(fhirTerminologyService, never()).deleteCodeSystem(anyString());
  }

  @Test
  void testDeleteCodeSystemNotFound() throws Exception {
    doThrow(new CodeSystemNotFoundException("CodeSystem not found for id: nonexistent"))
        .when(fhirTerminologyService)
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
    verify(fhirTerminologyService, times(1)).deleteCodeSystem("nonexistent");
  }
}
