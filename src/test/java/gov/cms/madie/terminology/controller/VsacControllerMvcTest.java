package gov.cms.madie.terminology.controller;

import ca.uhn.fhir.context.FhirContext;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.dto.QdmValueSet;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.helpers.TestHelpers;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import gov.cms.madie.terminology.service.VsacService;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VsacController.class)
public class VsacControllerMvcTest {
  private static final String TEST_USR = "FAKE";

  @MockBean private FhirContext fhirContext;

  @MockBean private VsacService vsacService;

  @MockBean private FhirTerminologyService fhirTerminologyService;

  @Autowired private MockMvc mockMvc;

  private UmlsUser umlsUser;
  private final List<ManifestExpansion> mockManifests = new ArrayList<>();

  private RetrieveMultipleValueSetsResponse svsValueSet;
  private ValueSet fhirValueSet;
  private static final String TEST_USER = "test.user";
  private static final String TEST_API_KEY = "te$tKey";

  @BeforeEach
  public void setup() throws JAXBException, IOException {
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

    File file = TestHelpers.getTestResourceFile("/value-sets/svs_office_visit.xml");
    JAXBContext jaxbContext = JAXBContext.newInstance(RetrieveMultipleValueSetsResponse.class);
    Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
    svsValueSet = (RetrieveMultipleValueSetsResponse) jaxbUnmarshaller.unmarshal(file);

    fhirValueSet =
        TestHelpers.getFhirTestResource("/value-sets/fhir_office_visit.json", ValueSet.class);
  }

  @Test
  void testSearchValueSets() throws Exception {
    String searchCriteria =
        "{\n"
            + "    \"profile\": \"eCQM Update 2030-05-05\",\n"
            + "    \"includeDraft\": true,\n"
            + "    \"valueSetParams\": [\n"
            + "        {\n"
            + "            \"oid\": \"2.16.840.1.113883.3.464.1003.101.12.1001\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    UmlsUser mockUmlsUser = mock(UmlsUser.class);
    Optional<UmlsUser> optionalUmlsUser = Optional.of(mockUmlsUser);
    when(vsacService.findByHarpId(anyString())).thenReturn(optionalUmlsUser);

    when(vsacService.getValueSets(any(ValueSetsSearchCriteria.class), any()))
        .thenReturn(List.of(svsValueSet));
    when(vsacService.convertToFHIRValueSets(List.of(svsValueSet)))
        .thenReturn(List.of(fhirValueSet));
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/vsac/value-sets/searches")
                    .with(user(TEST_USR))
                    .with(csrf())
                    .content(searchCriteria)
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();
    String content = result.getResponse().getContentAsString();
    verify(vsacService, times(1)).getValueSets(any(ValueSetsSearchCriteria.class), any());
    assertThat(content, containsString("\"resourceType\":\"ValueSet\""));
    assertThat(content, containsString("\"name\":\"Office Visit\""));
    assertThat(content, containsString("\"system\":\"2.16.840.1.113883.6.12\""));
    assertThat(content, containsString("\"system\":\"2.16.840.1.113883.6.96\""));
    assertThat(content, containsString("\"id\":\"2.16.840.1.113883.3.464.1003.101.12.1001\""));
  }

  @Test
  void testSearchValueSetsWhenNoValueSetFound() throws Exception {
    String searchCriteria =
        "{\n"
            + "    \"profile\": \"eCQM Update 2030-05-05\",\n"
            + "    \"includeDraft\": true,\n"
            + "    \"valueSetParams\": [\n"
            + "        {\n"
            + "            \"oid\": \"2.16.840.1.113883.3.464.1003.101.12.1001\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";

    UmlsUser mockUmlsUser = mock(UmlsUser.class);
    Optional<UmlsUser> optionalUmlsUser = Optional.of(mockUmlsUser);
    when(vsacService.findByHarpId(anyString())).thenReturn(optionalUmlsUser);

    when(vsacService.getValueSets(any(ValueSetsSearchCriteria.class), any()))
        .thenReturn(List.of(svsValueSet));
    when(vsacService.convertToFHIRValueSets(List.of(svsValueSet)))
        .thenReturn(List.of(fhirValueSet));
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    doThrow(new WebClientResponseException(404, "Error", null, null, null))
        .when(vsacService)
        .getValueSets(any(ValueSetsSearchCriteria.class), any());

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/vsac/value-sets/searches")
                    .with(user(TEST_USR))
                    .with(csrf())
                    .content(searchCriteria)
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andReturn();
    assertThat(
        result.getResponse().getContentAsString(), containsString("\"message\":\"404 Error\""));
  }

  @Test
  void testSearchQdmValueSets() throws Exception {
    String searchCriteria =
        "{\n"
            + "    \"profile\": \"eCQM Update 2030-05-05\",\n"
            + "    \"includeDraft\": true,\n"
            + "    \"valueSetParams\": [\n"
            + "        {\n"
            + "            \"oid\": \"2.16.840.1.113883.3.464.1003.101.12.1001\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";
    var concept =
        QdmValueSet.Concept.builder()
            .code("185463005")
            .codeSystemName("SNOMEDCT")
            .codeSystemOid("2.16.840.1.113883.6.96")
            .codeSystemVersion("2023-03")
            .displayName("Visit out of hours (procedure)")
            .build();
    QdmValueSet vs =
        QdmValueSet.builder()
            .oid("2.16.840.1.113883.3.464.1003.101.12.1001")
            .displayName("Encounter Inpatient")
            .concepts(List.of(concept))
            .build();
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    UmlsUser mockUmlsUser = mock(UmlsUser.class);
    Optional<UmlsUser> optionalUmlsUser = Optional.of(mockUmlsUser);
    when(vsacService.findByHarpId(anyString())).thenReturn(optionalUmlsUser);

    when(vsacService.getValueSetsInQdmFormat(any(ValueSetsSearchCriteria.class), any()))
        .thenReturn(List.of(vs));

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/vsac/qdm/value-sets/searches")
                    .with(user(TEST_USR))
                    .with(csrf())
                    .content(searchCriteria)
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();
    String content = result.getResponse().getContentAsString();
    verify(vsacService, times(1))
        .getValueSetsInQdmFormat(any(ValueSetsSearchCriteria.class), any());

    assertThat(content, containsString("\"display_name\":\"Encounter Inpatient\""));
    assertThat(content, containsString("\"code\":\"185463005\""));
    assertThat(content, containsString("\"code_system_name\":\"SNOMEDCT\""));
    assertThat(content, containsString("\"code_system_oid\":\"2.16.840.1.113883.6.96\""));
    assertThat(content, containsString("\"oid\":\"2.16.840.1.113883.3.464.1003.101.12.1001\""));
  }

  @Test
  void testSearchQdmValueSetsWhenUnauthorized() throws Exception {
    String searchCriteria =
        "{\n"
            + "    \"profile\": \"eCQM Update 2030-05-05\",\n"
            + "    \"includeDraft\": true,\n"
            + "    \"valueSetParams\": [\n"
            + "        {\n"
            + "            \"oid\": \"2.16.840.1.113883.3.464.1003.101.12.1001\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);
    when(vsacService.findByHarpId(anyString())).thenReturn(Optional.empty());
    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/vsac/qdm/value-sets/searches")
                    .with(user(TEST_USR))
                    .with(csrf())
                    .content(searchCriteria)
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isUnauthorized())
            .andReturn();
    assertThat(result.getResponse().getStatus(), is(equalTo(401)));
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
                MockMvcRequestBuilders.get("/vsac/manifest-list")
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
                MockMvcRequestBuilders.get("/vsac/manifest-list")
                    .with(user(TEST_USR))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isUnauthorized())
            .andReturn();
    assertThat(result.getResponse().getStatus(), is(equalTo(401)));
  }
}
