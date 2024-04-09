package gov.cms.madie.terminology.controller;

import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.terminology.dto.QdmValueSet;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import gov.cms.madie.terminology.service.VsacService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VsacFhirTerminologyController.class)
class VsacFhirTerminologyControllerMvcTest {

  private static final String TEST_USR = "FAKE";

  @MockBean private VsacService vsacService;

  @MockBean private FhirTerminologyService fhirTerminologyService;

  @Autowired private MockMvc mockMvc;

  private UmlsUser umlsUser;
  private final List<ManifestExpansion> mockManifests = new ArrayList<>();

  private final List<QdmValueSet> mockQdmValueSets = new ArrayList<>();
  private static final String TEST_USER = "test.user";
  private static final String TEST_API_KEY = "te$tKey";
  private static final String ADMIN_TEST_API_KEY_HEADER = "api-key";
  private static final String ADMIN_TEST_API_KEY_HEADER_VALUE = "0a51991c";
  private static final String TEST_TOKEN = "test-okta";

  @BeforeEach
  public void setup() {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_USER).build();
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
  }

  @Test
  void testGetManifestsSuccessfullyMvc() throws Exception {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    when(vsacService.findByHarpId(anyString())).thenReturn(Optional.ofNullable(umlsUser));
    when(fhirTerminologyService.getManifests(any(UmlsUser.class))).thenReturn(mockManifests);
    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/terminology/manifest-list")
                    .with(user(TEST_USR))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(result.getResponse().getStatus(), is(equalTo(200)));
    String content = result.getResponse().getContentAsString();
    verify(fhirTerminologyService, times(1)).getManifests(any(UmlsUser.class));
    assertThat(
        content,
        containsString(
            "[{\"fullUrl\":\"https://cts.nlm.nih.gov/fhir/Library/ecqm-update-4q2017-eh\",\"id\":\"ecqm-update-4q2017-eh\"},{\"fullUrl\":\"https://cts.nlm.nih.gov/fhir/Library/mu2-update-2012-10-25\",\"id\":\"mu2-update-2012-10-25\"}]"));
  }

  @Test
  void testUnAuthorizedUmlsUserWhileFetchingManifestsMvc() throws Exception {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    when(vsacService.findByHarpId(anyString())).thenReturn(Optional.empty());
    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/terminology/manifest-list")
                    .with(user(TEST_USR))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isUnauthorized())
            .andReturn();
    assertThat(result.getResponse().getStatus(), is(equalTo(401)));
  }

  @Test
  void testGetValueSetsExpansionsSuccessfullyMvc() throws Exception {
    String valueSetsSearchCriteria =
        "{\n"
            + "    \"profile\": \"\",\n"
            + "    \"includeDraft\": \"\",\n"
            + "    \"manifestExpansion\": {\n"
            + "        \"fullUrl\": \"https://cts.nlm.nih.gov/fhir/Library/ecqm-update-2022-05-05\",\n"
            + "        \"id\": \"ecqm-update-2022-05-05\"\n"
            + "    },\n"
            + "    \"valueSetParams\": [\n"
            + "        {\n"
            + "            \"oid\": \"2.16.840.1.113883.3.464.1003.113.11.1090\",\n"
            + "            \"release\": \"\",\n"
            + "            \"version\": \"\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    when(vsacService.findByHarpId(anyString())).thenReturn(Optional.ofNullable(umlsUser));
    when(fhirTerminologyService.getValueSetsExpansionsForQdm(
            any(ValueSetsSearchCriteria.class), any(UmlsUser.class)))
        .thenReturn(mockQdmValueSets);
    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/terminology/value-sets/expansion/qdm")
                    .with(user(TEST_USR))
                    .with(csrf())
                    .content(valueSetsSearchCriteria)
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(result.getResponse().getStatus(), is(equalTo(200)));
    String content = result.getResponse().getContentAsString();
    verify(fhirTerminologyService, times(1))
        .getValueSetsExpansionsForQdm(any(ValueSetsSearchCriteria.class), any(UmlsUser.class));
    assertThat(
        content,
        containsString(
            "[{\"oid\":\"test-value-set-id-1234\",\"version\":\"20240101\",\"concepts\":[{\"code\":\"test-code-052\",\"code_system_oid\":null,\"code_system_name\":null,\"code_system_version\":null,\"display_name\":null}],\"display_name\":\"test-value-set-display-name\"}]"));
  }

  @Test
  void testUnAuthorizedUmlsUserWhileGetValueSetsExpansionsMvc() throws Exception {
    String valueSetsSearchCriteria =
        "{\n"
            + "    \"profile\": \"\",\n"
            + "    \"includeDraft\": \"\",\n"
            + "    \"manifestExpansion\": {\n"
            + "        \"fullUrl\": \"https://cts.nlm.nih.gov/fhir/Library/ecqm-update-2022-05-05\",\n"
            + "        \"id\": \"ecqm-update-2022-05-05\"\n"
            + "    },\n"
            + "    \"valueSetParams\": [\n"
            + "        {\n"
            + "            \"oid\": \"2.16.840.1.113883.3.464.1003.113.11.1090\",\n"
            + "            \"release\": \"\",\n"
            + "            \"version\": \"\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    when(vsacService.findByHarpId(anyString())).thenReturn(Optional.empty());
    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/terminology/value-sets/expansion/qdm")
                    .with(user(TEST_USR))
                    .with(csrf())
                    .content(valueSetsSearchCriteria)
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isUnauthorized())
            .andReturn();
    assertThat(result.getResponse().getStatus(), is(equalTo(401)));
  }

  @Test
  public void testRetrieveAndUpdateCodeSystemsSuccessfully() throws Exception {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    when(vsacService.findByHarpId(anyString())).thenReturn(Optional.empty());
    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/terminology/update-code-systems")
                    .with(csrf())
                    .with(user(TEST_USR))
                    .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                    .header("Authorization", TEST_TOKEN))
            .andExpect(status().isUnauthorized())
            .andReturn();
    assertThat(result.getResponse().getStatus(), is(equalTo(401)));
  }

  @Test
  public void testRetrieveAndUpdateCodeSystemsUnauthorized() throws Exception {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    when(vsacService.findByHarpId(anyString())).thenReturn(Optional.ofNullable(umlsUser));
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/terminology/update-code-systems")
                .with(csrf())
                .with(user(TEST_USR))
                .header(ADMIN_TEST_API_KEY_HEADER, ADMIN_TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", TEST_TOKEN))
        .andExpect(status().isOk());
  }
}
