package gov.cms.madie.terminology.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.terminology.clients.UserServiceClient;
import gov.cms.madie.terminology.config.SecurityConfig;
import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.models.MadieValueSet;
import gov.cms.madie.terminology.service.ValueSetExpansionService;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ValueSetExpansionController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ValueSetExpansionControllerMvcTest {

  private static final String TEST_USR = "FAKE";
  private static final String IG_NAME = "hl7.fhir.us.core";
  private static final String IG_VERSION = "6.1.0";

  @MockitoBean private ValueSetExpansionService valueSetExpansionService;
  @MockitoBean private UserServiceClient userServiceClient;
  @MockitoBean private FhirContext fhirContext;
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private MadieValueSet madieValueSet;

  @BeforeEach
  void setUp() {
    madieValueSet =
        MadieValueSet.builder()
            .id("test-id")
            .oid("2.16.840.1.113883.3.464.1003.101.12.1001")
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.101.12.1001")
            .version("20230401")
            .displayName("Office Visit")
            .build();
  }

  // ---------------------------------------------------------------------------
  // GET /terminology/admin/implementation-guide/{ig}/version/{version}/value-sets
  // ---------------------------------------------------------------------------

  @Test
  void getValueSetDependenciesByIgAndVersionReturnsDependencies() throws Exception {
    when(valueSetExpansionService.getValueSetDependencies(anyString(), anyString()))
        .thenReturn(Set.of(madieValueSet));

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(
                        "/terminology/admin/implementation-guide/{ig}/version/{version}/value-sets",
                        IG_NAME,
                        IG_VERSION)
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
            MockMvcRequestBuilders.get(
                    "/terminology/admin/implementation-guide/{ig}/version/{version}/value-sets",
                    IG_NAME,
                    IG_VERSION)
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
            MockMvcRequestBuilders.get(
                    "/terminology/admin/implementation-guide/{ig}/version/{version}/value-sets",
                    IG_NAME,
                    IG_VERSION)
                .accept(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isUnauthorized());

    verify(valueSetExpansionService, never()).getValueSetDependencies(anyString(), anyString());
  }

  @Test
  void getValueSetDependenciesByIgAndVersionReturnsEmptySet() throws Exception {
    when(valueSetExpansionService.getValueSetDependencies(anyString(), anyString()))
        .thenReturn(Set.of());

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(
                        "/terminology/admin/implementation-guide/{ig}/version/{version}/value-sets",
                        IG_NAME,
                        IG_VERSION)
                    .with(user(TEST_USR).roles("MADIE-ADMIN"))
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body, is(equalTo("[]")));
    verify(valueSetExpansionService, times(1)).getValueSetDependencies(IG_NAME, IG_VERSION);
  }

  // ---------------------------------------------------------------------------
  // GET /terminology/admin/implementation-guide/value-sets
  // ---------------------------------------------------------------------------

  @Test
  void getAllValueSetDependenciesReturnsNestedMap() throws Exception {
    Map<String, Map<String, Set<MadieValueSet>>> dependencyMap = new HashMap<>();
    Map<String, Set<MadieValueSet>> sdMap = new HashMap<>();
    sdMap.put("StructureDefinition-us-core-patient.json", Set.of(madieValueSet));
    dependencyMap.put("hl7.fhir.us.core6.1.0", sdMap);

    when(valueSetExpansionService.getValueSetDependencies()).thenReturn(dependencyMap);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/terminology/admin/implementation-guide/value-sets")
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
            MockMvcRequestBuilders.get("/terminology/admin/implementation-guide/value-sets")
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
            MockMvcRequestBuilders.get("/terminology/admin/implementation-guide/value-sets")
                .accept(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isUnauthorized());

    verify(valueSetExpansionService, never()).getValueSetDependencies();
  }

  @Test
  void getAllValueSetDependenciesReturnsEmptyMap() throws Exception {
    when(valueSetExpansionService.getValueSetDependencies()).thenReturn(new HashMap<>());

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/terminology/admin/implementation-guide/value-sets")
                    .with(user(TEST_USR).roles("MADIE-ADMIN"))
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body, is(equalTo("{}")));
    verify(valueSetExpansionService, times(1)).getValueSetDependencies();
  }

  // ---------------------------------------------------------------------------
  // GET /terminology/admin/implementation-guide/update-value-sets
  // ---------------------------------------------------------------------------

  @Test
  void updateValueSetDependenciesSuccessfully() throws Exception {
    doNothing().when(valueSetExpansionService).updateValueSetDependencies();

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/terminology/admin/implementation-guide/update-value-sets")
                .with(user(TEST_USR).roles("MADIE-ADMIN"))
                .with(csrf()))
        .andExpect(status().isOk());

    verify(valueSetExpansionService, times(1)).updateValueSetDependencies();
  }

  @Test
  void updateValueSetDependenciesReturnsForbiddenWithoutAdminRole() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/terminology/admin/implementation-guide/update-value-sets")
                .with(user(TEST_USR))
                .with(csrf()))
        .andExpect(status().isForbidden());

    verify(valueSetExpansionService, never()).updateValueSetDependencies();
  }

  @Test
  void updateValueSetDependenciesReturnsUnauthorizedWithoutAuthentication() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/terminology/admin/implementation-guide/update-value-sets"))
        .andExpect(status().isUnauthorized());

    verify(valueSetExpansionService, never()).updateValueSetDependencies();
  }
}
