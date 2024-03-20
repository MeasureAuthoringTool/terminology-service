package gov.cms.madie.terminology.controller;

import gov.cms.madie.models.measure.ManifestExpansion;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VsacFhirTerminologyController.class)
class VsacFhirTerminologyControllerMvcTest {

  private static final String TEST_USR = "FAKE";

  @MockBean private VsacService vsacService;

  @MockBean private FhirTerminologyService fhirTerminologyService;

  @Autowired private MockMvc mockMvc;

  private UmlsUser umlsUser;
  private final List<ManifestExpansion> mockManifests = new ArrayList<>();
  private static final String TEST_USER = "test.user";
  private static final String TEST_API_KEY = "te$tKey";

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
                MockMvcRequestBuilders.get("/terminology/fhir/manifest-list")
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
                MockMvcRequestBuilders.get("/terminology/fhir/manifest-list")
                    .with(user(TEST_USR))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isUnauthorized())
            .andReturn();
    assertThat(result.getResponse().getStatus(), is(equalTo(401)));
  }
}
