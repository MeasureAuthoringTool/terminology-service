package gov.cms.madie.terminology.controller;

import gov.cms.madie.models.measure.ManifestExpansion;
import gov.cms.madie.terminology.exceptions.VsacUnauthorizedException;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import gov.cms.madie.terminology.service.VsacService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VsacFhirTerminologyControllerTest {

    @Mock
    private VsacService vsacService;
    @Mock private FhirTerminologyService fhirTerminologyService;

    @InjectMocks
    private VsacFhirTerminologyController vsacFhirTerminologyController;
    private UmlsUser umlsUser;
    private static final String TEST_USER = "test.user";

    private static final String TEST_HARP_ID = "te$tHarpId";
    private static final String TEST_API_KEY = "te$tKey";

    private final List<ManifestExpansion> mockManifests = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
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
    void testGetManifestsSuccessfully() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(TEST_USER);
        when(vsacService.findByHarpId(anyString())).thenReturn(Optional.ofNullable(umlsUser));
        when(fhirTerminologyService.getManifests(any(UmlsUser.class))).thenReturn(mockManifests);
        ResponseEntity<List<ManifestExpansion>> response = vsacFhirTerminologyController.getManifests(principal);
        assertEquals(response.getStatusCode(), HttpStatus.OK);
        assertEquals(response.getBody(), mockManifests);
    }

    @Test
    void testUnAuthorizedUmlsUserWhileFetchingManifests() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(TEST_USER);
        when(vsacService.findByHarpId(anyString()))
                .thenReturn(Optional.ofNullable(UmlsUser.builder().build()));
        assertThrows(VsacUnauthorizedException.class, () -> vsacFhirTerminologyController.getManifests(principal));
    }

}
