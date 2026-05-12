package gov.cms.madie.terminology.controller;

import gov.cms.madie.terminology.exceptions.ValueSetNotFoundException;
import gov.cms.madie.terminology.models.MadieValueSet;
import gov.cms.madie.terminology.service.ValueSetExpansionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValueSetExpansionAdminControllerTest {

  @Mock private ValueSetExpansionService vses;
  @InjectMocks private ValueSetExpansionAdminController controller;

  private static final String TEST_USER = "test.admin.user";
  private static final String MOCK_VALUE_SET_JSON =
      """
      {
        "resourceType": "ValueSet",
        "id": "test-vs",
        "url": "http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3",
        "version": "20230401",
        "status": "active",
        "expansion": {
          "contains": [
            { "system": "http://snomed.info/sct", "code": "123456789", "display": "Test Concept" }
          ]
        }
      }
      """;

  private Principal principal;
  private MadieValueSet madieValueSet;

  @BeforeEach
  void setUp() {
    principal = mock(Principal.class);

    madieValueSet =
        MadieValueSet.builder()
            .id("test-id")
            .url("http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.101.12.1001")
            .version("20240101")
            .valueSet(MOCK_VALUE_SET_JSON)
            .build();
  }

  @Test
  void testUpsertValueSetSuccessfully() {
    when(principal.getName()).thenReturn(TEST_USER);
    when(vses.upsertValueSet(any(MadieValueSet.class))).thenReturn(madieValueSet);

    ResponseEntity<MadieValueSet> response = controller.upsertValueSet(principal, madieValueSet);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(madieValueSet, response.getBody());
    verify(vses, times(1)).upsertValueSet(any(MadieValueSet.class));
  }

  @Test
  void testDeleteValueSetSuccessfully() {
    when(principal.getName()).thenReturn(TEST_USER);
    doNothing().when(vses).deleteValueSet(anyString());

    ResponseEntity<Void> response = controller.deleteValueSet(principal, madieValueSet.getId());

    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    verify(vses, times(1)).deleteValueSet(madieValueSet.getId());
  }

  @Test
  void testDeleteValueSetNotFound() {
    when(principal.getName()).thenReturn(TEST_USER);
    doThrow(new ValueSetNotFoundException("ValueSet not found for id: nonexistent"))
        .when(vses)
        .deleteValueSet(anyString());

    assertThrows(
        ValueSetNotFoundException.class, () -> controller.deleteValueSet(principal, "nonexistent"));
  }
}
