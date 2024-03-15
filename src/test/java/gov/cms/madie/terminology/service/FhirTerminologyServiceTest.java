package gov.cms.madie.terminology.service;

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

    @Mock
    FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;

    @InjectMocks FhirTerminologyService fhirTerminologyService;
    private UmlsUser umlsUser;
    private static final String TEST_HARP_ID = "te$tHarpId";
    private static final String TEST_API_KEY = "te$tKey";

    private final String responseFromServer = """
            {
              "resourceType": "Bundle",
              "id": "library-search",
              "meta":
              {
                "lastUpdated": "2024-03-14T14:04:52.456-04:00"
              },
              "type": "searchset",
              "total": 25,
              "link":
              [
                {
                  "relation": "self",
                  "url": "https://uat-cts.nlm.nih.gov/fhir/Library"
                }
              ],
              "entry":
              [
                {
                  "fullUrl": "http://cts.nlm.nih.gov/fhir/Library/ecqm-update-4q2017-eh",
                  "resource":
                  {
                    "resourceType": "Library",
                    "id": "ecqm-update-4q2017-eh",
                    "meta":
                    {
                      "profile":
                      [
                        "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/publishable-library-cqfm",
                        "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/quality-program-cqfm"
                      ]
                    },
                    "url": "http://cts.nlm.nih.gov/fhir/Library/ecqm-update-4q2017-eh",
                    "version": "2017-09-15",
                    "status": "active"
                  }
                },
                {
                  "fullUrl": "http://cts.nlm.nih.gov/fhir/Library/mu2-update-2012-10-25",
                  "resource":
                  {
                    "resourceType": "Library",
                    "id": "mu2-update-2012-10-25",
                    "meta":
                    {
                      "profile":
                      [
                        "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/publishable-library-cqfm",
                        "http://hl7.org/fhir/us/cqfmeasures/StructureDefinition/quality-program-cqfm"
                      ]
                    },
                    "url": "http://cts.nlm.nih.gov/fhir/Library/mu2-update-2012-10-25",
                    "version": "2012-10-25",
                    "status": "active"
                  }
                }
              ]
            }""";

    @BeforeEach
    public void setUp() {
        umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    }
    @Test
    void getManifests() {
        when(fhirTerminologyServiceWebClient.getManifestBundle(anyString())).thenReturn(responseFromServer);
        var result = fhirTerminologyService.getManifests(umlsUser);
        assertEquals(2, result.size());
        assertEquals("ecqm-update-4q2017-eh", result.get(0).getId());
        assertEquals("http://cts.nlm.nih.gov/fhir/Library/ecqm-update-4q2017-eh", result.get(0).getFullUrl());
        assertEquals("mu2-update-2012-10-25", result.get(1).getId());
        assertEquals("http://cts.nlm.nih.gov/fhir/Library/mu2-update-2012-10-25", result.get(1).getFullUrl());
    }
}
