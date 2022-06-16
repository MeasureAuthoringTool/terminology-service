package cms.gov.madie.terminology.controller;

import ca.uhn.fhir.context.FhirContext;
import cms.gov.madie.terminology.dto.ValueSetsSearchCriteria;
import cms.gov.madie.terminology.helpers.TestHelpers;
import cms.gov.madie.terminology.models.UmlsUser;
import cms.gov.madie.terminology.service.VsacService;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VsacController.class)
public class VsacControllerMvcTest {
  private static final String TEST_USR = "FAKE";

  @MockBean private FhirContext fhirContext;

  @MockBean private VsacService vsacService;

  @Autowired private MockMvc mockMvc;

  private RetrieveMultipleValueSetsResponse svsValueSet;
  private ValueSet fhirValueSet;
  private static final String TEST = "test";
  private static final String TEST_USER = "test.user";

  @BeforeEach
  public void setup() throws JAXBException, IOException {
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
    when(mockUmlsUser.getTgt()).thenReturn(TEST);
    when(vsacService.findByHarpId(anyString())).thenReturn(optionalUmlsUser);

    when(vsacService.getValueSets(any(ValueSetsSearchCriteria.class), anyString()))
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
    verify(vsacService, times(1)).getValueSets(any(ValueSetsSearchCriteria.class), anyString());
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
    when(mockUmlsUser.getTgt()).thenReturn(TEST);
    when(vsacService.findByHarpId(anyString())).thenReturn(optionalUmlsUser);

    when(vsacService.getValueSets(any(ValueSetsSearchCriteria.class), anyString()))
        .thenReturn(List.of(svsValueSet));
    when(vsacService.convertToFHIRValueSets(List.of(svsValueSet)))
        .thenReturn(List.of(fhirValueSet));
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    doThrow(new WebClientResponseException(404, "Error", null, null, null))
        .when(vsacService)
        .getValueSets(any(ValueSetsSearchCriteria.class), anyString());

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
}
