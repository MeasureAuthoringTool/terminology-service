package gov.cms.madie.terminology.controller;

import gov.cms.madie.terminology.clients.UserServiceClient;
import gov.cms.madie.terminology.config.SecurityConfig;
import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.service.ValueSetExpansionService;
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

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ValueSetExpansionAdminController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ValueSetExpansionAdminControllerMvcTest {

  private static final String TEST_USR = "FAKE";
  private static final String IG_NAME = "hl7.fhir.us.core";
  private static final String IG_VERSION = "6.1.0";

  @MockitoBean private ValueSetExpansionService valueSetExpansionService;
  @MockitoBean private UserServiceClient userServiceClient;
  @MockitoBean private FhirContext fhirContext;
  @Autowired private MockMvc mockMvc;

  // ---------------------------------------------------------------------------
  // GET /terminology/admin/implementation-guides/value-sets
  // ---------------------------------------------------------------------------

  @Test
  void getValueSetDependenciesByIgAndVersionReturnsDependencies() throws Exception {
    when(valueSetExpansionService.getValueSetDependencies(anyString(), anyString()))
        .thenReturn(
            List.of(
                "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.101.12.1001|20230401"));

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/terminology/admin/implementation-guides/value-sets")
                    .param("ig", IG_NAME)
                    .param("version", IG_VERSION)
                    .with(user(TEST_USR).roles("MADIE-ADMIN"))
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn();

    assertThat(result.getResponse().getStatus(), is(equalTo(200)));
    verify(valueSetExpansionService, times(1)).getValueSetDependencies(IG_NAME, IG_VERSION);
  }

  @Test
  void getValueSetDependenciesByIgAndVersionReturnsForbiddenWithoutAdminRole() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/terminology/admin/implementation-guides/value-sets")
                .param("ig", IG_NAME)
                .param("version", IG_VERSION)
                .with(user(TEST_USR))
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isForbidden());

    verify(valueSetExpansionService, never()).getValueSetDependencies(anyString(), anyString());
  }

  @Test
  void getValueSetDependenciesByIgAndVersionReturnsUnauthorizedWithoutAuthentication()
      throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/terminology/admin/implementation-guides/value-sets")
                .param("ig", IG_NAME)
                .param("version", IG_VERSION)
                .accept(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isUnauthorized());

    verify(valueSetExpansionService, never()).getValueSetDependencies(anyString(), anyString());
  }

  @Test
  void getAllValueSetDependenciesReturnsNestedMap() throws Exception {
    when(valueSetExpansionService.getValueSetDependencies())
        .thenReturn(
            List.of(
                "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.101.12.1001|20230401"));

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/terminology/admin/implementation-guides/value-sets")
                    .with(user(TEST_USR).roles("MADIE-ADMIN"))
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn();

    assertThat(result.getResponse().getStatus(), is(equalTo(200)));
    verify(valueSetExpansionService, times(1)).getValueSetDependencies();
  }

  @Test
  void getAllValueSetDependenciesReturnsForbiddenWithoutAdminRole() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/terminology/admin/implementation-guides/value-sets")
                .with(user(TEST_USR))
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isForbidden());

    verify(valueSetExpansionService, never()).getValueSetDependencies();
  }

  @Test
  void getAllValueSetDependenciesReturnsUnauthorizedWithoutAuthentication() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/terminology/admin/implementation-guides/value-sets")
                .accept(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isUnauthorized());

    verify(valueSetExpansionService, never()).getValueSetDependencies();
  }

  @Test
  void getAllValueSetDependenciesReturnsEmptyMap() throws Exception {
    when(valueSetExpansionService.getValueSetDependencies()).thenReturn(List.of());

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/terminology/admin/implementation-guides/value-sets")
                    .with(user(TEST_USR).roles("MADIE-ADMIN"))
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body, is(equalTo("[]")));
    verify(valueSetExpansionService, times(1)).getValueSetDependencies();
  }

  // ---------------------------------------------------------------------------
  // GET /terminology/admin/implementation-guides/update-value-sets
  // ---------------------------------------------------------------------------

  @Test
  void updateAllValueSetDependenciesSuccessfully() throws Exception {
    doNothing().when(valueSetExpansionService).updateValueSetDependencies();

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/terminology/admin/implementation-guides/update-value-sets")
                .with(user(TEST_USR).roles("MADIE-ADMIN"))
                .with(csrf()))
        .andExpect(status().isAccepted());

    verify(valueSetExpansionService, times(1)).updateValueSetDependencies();
  }

  @Test
  void updateIgValueSetDependenciesSuccessfully() throws Exception {
    doNothing()
        .when(valueSetExpansionService)
        .updateIgValueSetDependencies(anyString(), anyString());

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/terminology/admin/implementation-guides/update-value-sets")
                .param("ig", IG_NAME)
                .param("version", IG_VERSION)
                .with(user(TEST_USR).roles("MADIE-ADMIN"))
                .with(csrf()))
        .andExpect(status().isAccepted());

    verify(valueSetExpansionService, times(1)).updateIgValueSetDependencies(IG_NAME, IG_VERSION);
  }

  @Test
  void updateValueSetDependenciesReturnsForbiddenWithoutAdminRole() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/terminology/admin/implementation-guides/update-value-sets")
                .with(user(TEST_USR))
                .with(csrf()))
        .andExpect(status().isForbidden());

    verify(valueSetExpansionService, never()).updateValueSetDependencies();
    verify(valueSetExpansionService, never())
        .updateIgValueSetDependencies(anyString(), anyString());
  }

  @Test
  void updateValueSetDependenciesReturnsUnauthorizedWithoutAuthentication() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get(
                "/terminology/admin/implementation-guides/update-value-sets"))
        .andExpect(status().isUnauthorized());

    verify(valueSetExpansionService, never()).updateValueSetDependencies();
    verify(valueSetExpansionService, never())
        .updateIgValueSetDependencies(anyString(), anyString());
  }

  // ---------------------------------------------------------------------------
  // GET /terminology/admin/implementation-guides
  // ---------------------------------------------------------------------------

  @Test
  void getImplementationGuidesReturnsListSuccessfully() throws Exception {
    when(valueSetExpansionService.getImplementationGuides())
        .thenReturn(List.of("hl7.fhir.us.core v6.1.0", "hl7.fhir.us.qicore v6.0.0"));

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/terminology/admin/implementation-guides")
                    .with(user(TEST_USR).roles("MADIE-ADMIN"))
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn();

    assertThat(result.getResponse().getStatus(), is(equalTo(200)));
    verify(valueSetExpansionService, times(1)).getImplementationGuides();
  }

  @Test
  void getImplementationGuidesReturnsEmptyList() throws Exception {
    when(valueSetExpansionService.getImplementationGuides()).thenReturn(List.of());

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/terminology/admin/implementation-guides")
                    .with(user(TEST_USR).roles("MADIE-ADMIN"))
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body, is(equalTo("[]")));
    verify(valueSetExpansionService, times(1)).getImplementationGuides();
  }
}
