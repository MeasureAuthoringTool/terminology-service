package gov.cms.madie.terminology.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.JsonParser;
import gov.cms.madie.terminology.exceptions.CodeSystemNotFoundException;
import gov.cms.madie.terminology.exceptions.ValueSetNotFoundException;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.MadieValueSet;
import gov.cms.madie.terminology.service.ValueSetExpansionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VSESControllerTest {

  @Mock private ValueSetExpansionService vses;
  @Mock private FhirContext fhirContext;
  @Mock private JsonParser jsonParser;
  @InjectMocks private VSESController controller;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(controller, "allowedHosts", Arrays.asList(".com", ".gov", ".org"));
  }

  @Test
  void expandValueSetReturnsOkAndBodyWhenValueSetExists() {
    mockValueSetService("http://example.com/vs", "20240101", "{\"resourceType\":\"ValueSet\"}");
    var response = controller.expandValueSets("http://example.com/vs", "20240101");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(vses, times(1)).getValueSet(anyString(), any());
  }

  @Test
  void expandValueSetReturnsOkWhenVersionIsNull() {
    mockValueSetService("http://example.com/vs", null, "{\"resourceType\":\"ValueSet\"}");
    var response = controller.expandValueSets("http://example.com/vs", null);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(vses, times(1)).getValueSet(anyString(), any());
  }

  @Test
  void expandValueSetThrowsValueSetNotFoundExceptionWhenServiceDoesNotFindValueSet() {
    when(vses.getValueSet(anyString(), any()))
        .thenThrow(new ValueSetNotFoundException("ValueSet not found"));

    assertThrows(
        ValueSetNotFoundException.class,
        () -> controller.expandValueSets("http://example.com/cs", null));
    verify(vses, times(1)).getValueSet(anyString(), any());
  }

  @Test
  void expandValueSetThrowsValueSetNotFoundExceptionWhenUrlIsInvalid() {
    assertThrows(
        ValueSetNotFoundException.class, () -> controller.expandValueSets("not-a-url", null));
    verify(vses, never()).getValueSet(anyString(), any());
  }

  @Test
  void expandValueSetAcceptsHttpsUrl() {
    mockValueSetService("https://example.com/vs", "1.0", "{\"resourceType\":\"ValueSet\"}");
    var response = controller.expandValueSets("https://example.com/vs", "1.0");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(vses, times(1)).getValueSet("https://example.com/vs", "1.0");
  }

  @Test
  void expandValueSetThrowsValueSetNotFoundExceptionWhenUrlIsEmpty() {
    assertThrows(ValueSetNotFoundException.class, () -> controller.expandValueSets("", null));
    verify(vses, never()).getValueSet(anyString(), any());
  }

  @Test
  void expandValueSetThrowsValueSetNotFoundExceptionWhenUrlHasInvalidFormat() {
    assertThrows(
        ValueSetNotFoundException.class,
        () -> controller.expandValueSets("ht!tp://example.com/vs", null));
    verify(vses, never()).getValueSet(anyString(), any());
  }

  @Test
  void expandValueSetReturnsContentTypeApplicationFhirJson() {
    mockValueSetService("http://example.com/vs", null, "{\"resourceType\":\"ValueSet\"}");
    var response = controller.expandValueSets("http://example.com/vs", null);

    assertEquals("application/fhir+json", response.getHeaders().getContentType().toString());
  }

  @Test
  void expandValueSetReturnsBundledJson() {
    String expectedBundle = "{\"resourceType\":\"Bundle\"}";
    mockValueSetService("http://example.com/vs", null, "{\"resourceType\":\"ValueSet\"}");
    when(jsonParser.encodeResourceToString(any())).thenReturn(expectedBundle);

    var response = controller.expandValueSets("http://example.com/vs", null);

    assertEquals(expectedBundle, response.getBody());
  }

  @Test
  void expandCodeSystemReturnsOkAndBundledJsonWhenCodeSystemsExist() {
    mockCodeSystemService(
        List.of(
            buildCodeSystem("1", "http://example.com/CodeSystem/1"),
            buildCodeSystem("2", "http://example.com/CodeSystem/2")));

    var response = controller.retrieveCodeSystems("http://example.com/cs", 2);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    verify(vses, times(1)).getCodeSystem("http://example.com/cs", 2);
  }

  @Test
  void expandCodeSystemReturnsOkWithCountWhenCountIsProvided() {
    mockCodeSystemService(List.of(buildCodeSystem("1", "http://example.com/CodeSystem/1")));

    var response = controller.retrieveCodeSystems("http://example.com/cs", 5);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(vses, times(1)).getCodeSystem("http://example.com/cs", 5);
  }

  @Test
  void expandCodeSystemReturnsOkWithoutCountWhenCountIsNull() {
    mockCodeSystemService(List.of(buildCodeSystem("1", "http://example.com/CodeSystem/1")));

    var response = controller.retrieveCodeSystems("http://example.com/cs", null);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(vses, times(1)).getCodeSystem("http://example.com/cs", null);
  }

  @Test
  void expandCodeSystemReturnsContentTypeApplicationFhirJson() {
    mockCodeSystemService(List.of(buildCodeSystem("1", "http://example.com/CodeSystem/1")));

    var response = controller.retrieveCodeSystems("http://example.com/cs", null);

    assertEquals("application/fhir+json", response.getHeaders().getContentType().toString());
  }

  @Test
  void expandCodeSystemReturnsEncodedBundleAsBody() {
    String expectedBundle = "{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"total\":1}";
    mockCodeSystemService(List.of(buildCodeSystem("1", "http://example.com/CodeSystem/1")));
    when(jsonParser.encodeResourceToString(any())).thenReturn(expectedBundle);

    var response = controller.retrieveCodeSystems("http://example.com/cs", null);

    assertEquals(expectedBundle, response.getBody());
  }

  @Test
  void expandCodeSystemAcceptsHttpUrl() {
    mockCodeSystemService(List.of(buildCodeSystem("1", "http://example.com/CodeSystem/1")));

    var response = controller.retrieveCodeSystems("http://example.com/cs", null);

    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  @Test
  void expandCodeSystemAcceptsHttpsUrlWithAllowedHost() {
    mockCodeSystemService(List.of(buildCodeSystem("1", "http://example.com/CodeSystem/1")));

    var response = controller.retrieveCodeSystems("https://example.com/cs", null);

    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  @Test
  void expandCodeSystemThrowsCodeSystemNotFoundExceptionWhenUrlIsInvalid() {
    assertThrows(
        CodeSystemNotFoundException.class,
        () -> controller.retrieveCodeSystems("https://example.invalid/cs", null));
    verify(vses, never()).getCodeSystem(anyString(), any());
  }

  @Test
  void expandCodeSystemThrowsCodeSystemNotFoundExceptionWhenUrlIsEmpty() {
    assertThrows(CodeSystemNotFoundException.class, () -> controller.retrieveCodeSystems("", null));
    verify(vses, never()).getCodeSystem(anyString(), any());
  }

  @Test
  void expandCodeSystemThrowsCodeSystemNotFoundExceptionWhenUrlMissingProtocol() {
    assertThrows(
        CodeSystemNotFoundException.class,
        () -> controller.retrieveCodeSystems("example.com/cs", null));
    verify(vses, never()).getCodeSystem(anyString(), any());
  }

  @Test
  void expandCodeSystemThrowsCodeSystemNotFoundExceptionWhenUrlHasDisallowedHost() {
    assertThrows(
        CodeSystemNotFoundException.class,
        () -> controller.retrieveCodeSystems("https://example.xyz/cs", null));
    verify(vses, never()).getCodeSystem(anyString(), any());
  }

  @Test
  void expandCodeSystemThrowsCodeSystemNotFoundExceptionWhenUrlHasInvalidFormat() {
    assertThrows(
        CodeSystemNotFoundException.class,
        () -> controller.retrieveCodeSystems("ht!tp://example.com/cs", null));
    verify(vses, never()).getCodeSystem(anyString(), any());
  }

  private void mockValueSetService(String url, String version, String vsJson) {
    MadieValueSet madieValueSet =
        MadieValueSet.builder().url(url).version(version).valueSet(vsJson).build();

    when(vses.getValueSet(anyString(), any())).thenReturn(madieValueSet);
    when(fhirContext.newJsonParser()).thenReturn(jsonParser);
    when(jsonParser.encodeResourceToString(any())).thenReturn(vsJson);
  }

  private void mockCodeSystemService(List<CodeSystem> codeSystems) {
    when(vses.getCodeSystem(anyString(), any())).thenReturn(codeSystems);
    when(fhirContext.newJsonParser()).thenReturn(jsonParser);
    when(jsonParser.encodeResourceToString(any())).thenReturn("{\"resourceType\":\"Bundle\"}");
  }

  private CodeSystem buildCodeSystem(String id, String fullUrl) {
    return CodeSystem.builder()
        .id(id)
        .fullUrl(fullUrl)
        .name("codeSys" + id)
        .oid("2.16.840.1.1")
        .versionId("1.0")
        .version(CodeSystem.Version.builder().fhirVersion("4.0.1").build())
        .build();
  }
}
