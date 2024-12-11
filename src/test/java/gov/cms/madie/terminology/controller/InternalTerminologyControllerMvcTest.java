package gov.cms.madie.terminology.controller;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.exceptions.HapiOperationException;
import gov.cms.madie.terminology.service.InternalTerminologyService;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.security.Principal;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionContainsComponent;
import static org.hl7.fhir.r4.model.ValueSet.ValueSetExpansionComponent;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalTerminologyController.class)
class InternalTerminologyControllerMvcTest {

  @MockBean private InternalTerminologyService internalTerminologyService;
  @MockBean private FhirContext fhirContext;

  @Autowired private MockMvc mockMvc;
  private static final String TEST_USER = "test.user";
  private static final String VALUE_SET_URL = "ValueSet/us-core-vaccines-cvx-1";

  @Test
  void testGetValueSetExpansion() throws Exception {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    var contains = new ValueSetExpansionContainsComponent();
    contains.setCode("02").setDisplay("trivalent poliovirus vaccine, live, oral").setSystem("CVX");
    var expansion = new ValueSetExpansionComponent();
    expansion.addContains(contains);
    ValueSet valueSet = new ValueSet();
    valueSet.setId("us-core-vaccines-cvx-1");
    valueSet.setExpansion(expansion);

    when(internalTerminologyService.getValueSetExpansionByUrl(anyString())).thenReturn(valueSet);
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(
                        "/internal-terminology/ValueSet/expand?url=" + VALUE_SET_URL)
                    .with(user(TEST_USER))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(result.getResponse().getStatus(), is(equalTo(200)));
    String content = result.getResponse().getContentAsString();
    verify(internalTerminologyService, times(1)).getValueSetExpansionByUrl(anyString());
    assertThat(
        content,
        containsString(
            "{\"resourceType\":\"ValueSet\",\"id\":\"us-core-vaccines-cvx-1\",\"expansion\":{\"contains\":[{\"system\":\"CVX\",\"code\":\"02\",\"display\":\"trivalent poliovirus vaccine, live, oral\"}]}}"));
  }

  @Test
  void testGetValueSetExpansionIfNotFound() throws Exception {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(TEST_USER);

    doThrow(new HapiOperationException("Value set not found"))
        .when(internalTerminologyService)
        .getValueSetExpansionByUrl(anyString());
    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(
                        "/internal-terminology/ValueSet/expand?url=" + VALUE_SET_URL)
                    .with(user(TEST_USER))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();
    String content = result.getResponse().getContentAsString();
    verify(internalTerminologyService, times(1)).getValueSetExpansionByUrl(anyString());
    assertThat(content, containsString("Value set not found"));
  }
}
