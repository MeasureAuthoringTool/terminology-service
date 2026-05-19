package gov.cms.madie.terminology.controller;

import gov.cms.madie.terminology.exceptions.ValueSetNotFoundException;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.MadieValueSet;
import gov.cms.madie.terminology.service.ValueSetExpansionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValueSetExpansionControllerTest {

  @Mock private ValueSetExpansionService vses;
  @InjectMocks private ValueSetExpansionController controller;

  @Test
  void expandValueSetReturnsOkAndBodyWhenValueSetExists() {
    String url = "http://example.com/vs";
    String version = "20240101";
    String vsJson = "{\"resourceType\":\"ValueSet\",\"id\":\"vs1\"}";
    MadieValueSet madieValueSet =
        MadieValueSet.builder().id("id1").url(url).version(version).valueSet(vsJson).build();

    when(vses.getValueSet(anyString(), any())).thenReturn(madieValueSet);

    ResponseEntity<String> response = controller.expandValueSet(url, version);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(vsJson, response.getBody());
    verify(vses, times(1)).getValueSet(anyString(), any());
  }

  @Test
  void expandValueSetReturnsOkWhenVersionIsNull() {
    String url = "http://example.com/vs";
    String vsJson = "{\"resourceType\":\"ValueSet\",\"id\":\"vs2\"}";
    MadieValueSet madieValueSet =
        MadieValueSet.builder().id("id2").url(url).version(null).valueSet(vsJson).build();

    when(vses.getValueSet(anyString(), any())).thenReturn(madieValueSet);

    ResponseEntity<String> response = controller.expandValueSet(url, null);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(vsJson, response.getBody());
    verify(vses, times(1)).getValueSet(anyString(), any());
  }

  @Test
  void expandValueSetThrowsValueSetNotFoundExceptionWhenServiceDoesNotFindValueSet() {
    when(vses.getValueSet(anyString(), any()))
        .thenThrow(new ValueSetNotFoundException("ValueSet not found"));

    assertThrows(
        ValueSetNotFoundException.class, () -> controller.expandValueSet("nonexistent", null));
    verify(vses, times(1)).getValueSet(anyString(), any());
  }

  @Test
  void expandCodeSystemReturnsOkAndListWhenCodeSystemsExistWithCount() {
    String url = "http://example.com/cs";
    Integer count = 2;
    CodeSystem cs1 = mock(CodeSystem.class);
    CodeSystem cs2 = mock(CodeSystem.class);
    List<CodeSystem> csList = List.of(cs1, cs2);

    when(vses.getCodeSystem(anyString(), any())).thenReturn(csList);

    ResponseEntity<List<CodeSystem>> response = controller.expandCodeSystem(url, count);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(csList, response.getBody());
    verify(vses, times(1)).getCodeSystem(anyString(), any());
  }

  @Test
  void expandCodeSystemReturnsEmptyListWhenNoCodeSystemsFound() {
    String url = "http://example.com/cs-empty";

    when(vses.getCodeSystem(anyString(), any())).thenReturn(Collections.emptyList());

    ResponseEntity<List<CodeSystem>> response = controller.expandCodeSystem(url, null);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue(response.getBody().isEmpty());
    verify(vses, times(1)).getCodeSystem(anyString(), any());
  }
}
