package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.anyString;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FhirTerminologyServiceTest {

  @Mock FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;
  @Mock FhirContext fhirContext;
  @InjectMocks FhirTerminologyService fhirTerminologyService;
  private UmlsUser umlsUser;
  private static final String TEST_HARP_ID = "te$tHarpId";
  private static final String TEST_API_KEY = "te$tKey";

  private final String responseFromServer =
      "{\n"
          + "              \"resourceType\": \"Bundle\",\n"
          + "              \"id\": \"library-search\",\n"
          + "              \"meta\":\n"
          + "              {\n"
          + "                \"lastUpdated\": \"2024-03-14T14:04:52.456-04:00\"\n"
          + "              },\n"
          + "              \"type\": \"searchset\",\n"
          + "              \"total\": 25,\n"
          + "              \"link\":\n"
          + "              [\n"
          + "                {\n"
          + "                  \"relation\": \"self\",\n"
          + "                  \"url\": \"https://uat-cts.nlm.nih.gov/fhir/Library\"\n"
          + "                }\n"
          + "              ],\n"
          + "              \"entry\":\n"
          + "              [\n"
          + "                {\n"
          + "                  \"fullUrl\": \"http://cts.nlm.nih.gov/fhir/Library/ecqm-update-4q2017-eh\",\n"
          + "                  \"resource\":\n"
          + "                  {\n"
          + "                    \"resourceType\": \"Library\",\n"
          + "                    \"id\": \"ecqm-update-4q2017-eh\",\n"
          + "                    \"meta\":\n"
          + "                    {\n"
          + "                      \"profile\":\n"
          + "                      [\n"
          + "                        \"http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/publishable-library-cqfm\",\n"
          + "                        \"http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/quality-program-cqfm\"\n"
          + "                      ]\n"
          + "                    },\n"
          + "                    \"url\": \"http://cts.nlm.nih.gov/fhir/Library/ecqm-update-4q2017-eh\",\n"
          + "                    \"version\": \"2017-09-15\",\n"
          + "                    \"status\": \"active\"\n"
          + "                  }\n"
          + "                },\n"
          + "                {\n"
          + "                  \"fullUrl\": \"http://cts.nlm.nih.gov/fhir/Library/mu2-update-2012-10-25\",\n"
          + "                  \"resource\":\n"
          + "                  {\n"
          + "                    \"resourceType\": \"Library\",\n"
          + "                    \"id\": \"mu2-update-2012-10-25\",\n"
          + "                    \"meta\":\n"
          + "                    {\n"
          + "                      \"profile\":\n"
          + "                      [\n"
          + "                        \"http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/publishable-library-cqfm\",\n"
          + "                        \"http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/quality-program-cqfm\"\n"
          + "                      ]\n"
          + "                    },\n"
          + "                    \"url\": \"http://cts.nlm.nih.gov/fhir/Library/mu2-update-2012-10-25\",\n"
          + "                    \"version\": \"2012-10-25\",\n"
          + "                    \"status\": \"active\"\n"
          + "                  }\n"
          + "                }\n"
          + "              ]\n"
          + "            }";

  @BeforeEach
  public void setUp() {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
  }

  @Test
  void getManifests() {
    when(fhirContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(fhirTerminologyServiceWebClient.getManifestBundle(anyString()))
        .thenReturn(responseFromServer);
    var result = fhirTerminologyService.getManifests(umlsUser);
    assertEquals(2, result.size());
    assertEquals("ecqm-update-4q2017-eh", result.get(0).getId());
    assertEquals(
        "http://cts.nlm.nih.gov/fhir/Library/ecqm-update-4q2017-eh", result.get(0).getFullUrl());
    assertEquals("mu2-update-2012-10-25", result.get(1).getId());
    assertEquals(
        "http://cts.nlm.nih.gov/fhir/Library/mu2-update-2012-10-25", result.get(1).getFullUrl());
  }
}
