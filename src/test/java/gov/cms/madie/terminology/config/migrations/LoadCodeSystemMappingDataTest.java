package gov.cms.madie.terminology.config.migrations;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import gov.cms.madie.models.mapping.CodeSystemEntry;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.task.UpdateCodeSystemTask;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoadCodeSystemMappingDataTest {

  @Captor public ArgumentCaptor<List<CodeSystem>> codeSystemListCaptor;

  @InjectMocks private final LoadCodeSystemMappingData migration = new LoadCodeSystemMappingData();

  @Mock private MongoTemplate mongoTemplateMock;
  @Mock private CodeSystemRepository codeSystemRepositoryMock;
  @Mock ObjectMapper objectMapperMock;
  @Mock UpdateCodeSystemTask updateCodeSystemTaskMock;

  private CodeSystem existingCodeSystem;
  private CodeSystemEntry mappingDocEntry;

  @BeforeEach
  void init() {
    String testUrl = "http://example.com/fhir/CodeSystem/test";
    String testOid = "urn:oid:1.1.1.1";
    existingCodeSystem =
        CodeSystem.builder()
            .id(new ObjectId().toString())
            .oid(testOid)
            .name("ExistingCodeSystem")
            .title("Existing Code System")
            .fullUrl(testUrl)
            .version(CodeSystem.Version.builder().fhirVersion("1.0.0").build())
            .build();

    mappingDocEntry =
        CodeSystemEntry.builder()
            .oid(testOid)
            .url(testUrl)
            .name("MappingDocCodeSystem")
            .versions(
                List.of(CodeSystemEntry.Version.builder().fhir("1.0.0").vsac("1.0.0").build()))
            .build();

    lenient().doNothing().when(mongoTemplateMock).dropCollection(anyString());
    lenient().doNothing().when(updateCodeSystemTaskMock).updateCodeSystems();
  }

  @Test
  void testExistingCodeSystemWithMatchingFhirAndVsacVersions() throws JacksonException {
    when(codeSystemRepositoryMock.findAll()).thenReturn(List.of(existingCodeSystem));

    when(objectMapperMock.readValue(anyString(), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[] {mappingDocEntry});

    migration.apply(
        mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock, updateCodeSystemTaskMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    ArgumentCaptor<CodeSystem> codeSystemCaptor = ArgumentCaptor.forClass(CodeSystem.class);
    verify(codeSystemRepositoryMock, times(1)).save(codeSystemCaptor.capture());
    assertEquals(
        mappingDocEntry.getVersions().get(0).getVsac(),
        codeSystemCaptor.getValue().getVersion().getVsacVersion());
    assertEquals(
        mappingDocEntry.getVersions().get(0).getFhir(),
        codeSystemCaptor.getValue().getVersion().getFhirVersion());
    assertTrue(codeSystemCaptor.getValue().isQdm());
    assertTrue(codeSystemCaptor.getValue().isFhir());
    assertTrue(codeSystemCaptor.getValue().isLatestVersion());
    assertTrue(codeSystemCaptor.getValue().isVsacSearchable());
  }

  @Test
  void testExistingCodeSystemWithMatchingFhirAndDifferingVsacVersions() throws JacksonException {
    mappingDocEntry.setVersions(
        List.of(CodeSystemEntry.Version.builder().fhir("1.0.0").vsac("9.9.9").build()));

    when(codeSystemRepositoryMock.findAll()).thenReturn(List.of(existingCodeSystem));

    when(objectMapperMock.readValue(anyString(), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[] {mappingDocEntry});
    migration.apply(
        mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock, updateCodeSystemTaskMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    ArgumentCaptor<CodeSystem> codeSystemCaptor = ArgumentCaptor.forClass(CodeSystem.class);
    verify(codeSystemRepositoryMock, times(1)).save(codeSystemCaptor.capture());

    // Update Existing Code System Version entry
    assertEquals(existingCodeSystem.getId(), codeSystemCaptor.getValue().getId());
    assertTrue(codeSystemCaptor.getValue().isFhir());
    assertTrue(codeSystemCaptor.getValue().isQdm());
    assertEquals(existingCodeSystem.getVersion(), codeSystemCaptor.getValue().getVersion());
    assertEquals(
        mappingDocEntry.getVersions().get(0).getVsac(),
        codeSystemCaptor.getValue().getVersion().getVsacVersion());
  }

  @Test
  void testExistingCodeSystemWithMultipleVersions() throws JacksonException {
    mappingDocEntry.setVersions(
        List.of(
            // Latest first
            CodeSystemEntry.Version.builder().fhir("2.0.0").vsac("9.9.9").build(),
            CodeSystemEntry.Version.builder().fhir("1.0.0").vsac("8.8.8").build()));

    when(codeSystemRepositoryMock.findAll()).thenReturn(List.of(existingCodeSystem));

    when(objectMapperMock.readValue(anyString(), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[] {mappingDocEntry});
    migration.apply(
        mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock, updateCodeSystemTaskMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    ArgumentCaptor<CodeSystem> codeSystemCaptor = ArgumentCaptor.forClass(CodeSystem.class);
    verify(codeSystemRepositoryMock, times(2)).save(codeSystemCaptor.capture());

    List<CodeSystem> codeSystemSaves = codeSystemCaptor.getAllValues();
    assertEquals(
        mappingDocEntry.getVersions().get(0).getVsac(),
        codeSystemSaves.get(0).getVersion().getVsacVersion());
    assertEquals(
        mappingDocEntry.getVersions().get(0).getFhir(),
        codeSystemSaves.get(0).getVersion().getFhirVersion());
    assertNull(codeSystemSaves.get(0).getTitle());
    assertTrue(codeSystemSaves.get(0).isFhir());
    assertTrue(codeSystemSaves.get(0).isQdm());
    assertTrue(codeSystemSaves.get(0).isLatestVersion());

    assertEquals(existingCodeSystem.getTitle(), codeSystemSaves.get(1).getTitle());
    assertEquals(
        mappingDocEntry.getVersions().get(1).getVsac(),
        codeSystemSaves.get(1).getVersion().getVsacVersion());
    assertEquals(
        mappingDocEntry.getVersions().get(1).getFhir(),
        codeSystemSaves.get(1).getVersion().getFhirVersion());
    assertFalse(codeSystemSaves.get(1).isLatestVersion());
    assertTrue(codeSystemSaves.get(1).isFhir());
    assertTrue(codeSystemSaves.get(1).isQdm());
  }

  @Test
  void testExistingCodeSystemWithOnlyVsacVersions() throws JacksonException {
    mappingDocEntry.setVersions(List.of(CodeSystemEntry.Version.builder().vsac("9.9.9").build()));

    when(codeSystemRepositoryMock.findAll()).thenReturn(List.of(existingCodeSystem));

    when(objectMapperMock.readValue(anyString(), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[] {mappingDocEntry});
    migration.apply(
        mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock, updateCodeSystemTaskMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    ArgumentCaptor<CodeSystem> codeSystemCaptor = ArgumentCaptor.forClass(CodeSystem.class);
    verify(codeSystemRepositoryMock, times(1)).save(codeSystemCaptor.capture());

    assertFalse(codeSystemCaptor.getValue().isFhir());
    assertTrue(codeSystemCaptor.getValue().isQdm());
    assertEquals("urn:oid:1.1.1.1", codeSystemCaptor.getValue().getOid());
    assertEquals("9.9.9", codeSystemCaptor.getValue().getVersion().getVsacVersion());
  }

  @Test
  void testExistingCodeSystemWithOnlyFhirVersions() throws JacksonException {
    mappingDocEntry.setVersions(List.of(CodeSystemEntry.Version.builder().fhir("1.0.0").build()));
    when(codeSystemRepositoryMock.findAll()).thenReturn(List.of(existingCodeSystem));

    when(objectMapperMock.readValue(anyString(), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[] {mappingDocEntry});
    migration.apply(
        mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock, updateCodeSystemTaskMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    ArgumentCaptor<CodeSystem> codeSystemCaptor = ArgumentCaptor.forClass(CodeSystem.class);
    verify(codeSystemRepositoryMock, times(1)).save(codeSystemCaptor.capture());

    assertTrue(codeSystemCaptor.getValue().isFhir());
    assertFalse(codeSystemCaptor.getValue().isQdm());
    assertEquals(existingCodeSystem.getId(), codeSystemCaptor.getValue().getId());
    assertEquals(existingCodeSystem.getOid(), codeSystemCaptor.getValue().getOid());
    assertEquals(existingCodeSystem.getVersion(), codeSystemCaptor.getValue().getVersion());
    assertEquals(existingCodeSystem.getFullUrl(), codeSystemCaptor.getValue().getFullUrl());
  }

  @Test
  void testNewCodeSystemWithMatchingFhirAndVsacVersions() throws JacksonException {
    mappingDocEntry.setVersions(
        List.of(CodeSystemEntry.Version.builder().fhir("1.0.0").vsac("1.0.0").build()));
    when(codeSystemRepositoryMock.findAll()).thenReturn(Collections.emptyList());

    when(objectMapperMock.readValue(anyString(), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[] {mappingDocEntry});

    migration.apply(
        mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock, updateCodeSystemTaskMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    ArgumentCaptor<CodeSystem> codeSystemCaptor = ArgumentCaptor.forClass(CodeSystem.class);
    verify(codeSystemRepositoryMock, times(1)).save(codeSystemCaptor.capture());
    assertTrue(codeSystemCaptor.getValue().isQdm());
    assertTrue(codeSystemCaptor.getValue().isFhir());
    assertEquals(
        mappingDocEntry.getVersions().get(0).getFhir(),
        codeSystemCaptor.getValue().getVersion().getFhirVersion());
    assertEquals(
        mappingDocEntry.getVersions().get(0).getVsac(),
        codeSystemCaptor.getValue().getVersion().getVsacVersion());
    assertEquals(mappingDocEntry.getOid(), codeSystemCaptor.getValue().getOid());
  }

  @Test
  void testNewCodeSystemWithDifferingFhirAndVsacVersions() throws JacksonException {
    mappingDocEntry.setVersions(
        List.of(CodeSystemEntry.Version.builder().fhir("1.0.0").vsac("9.9.9").build()));

    when(codeSystemRepositoryMock.findAll()).thenReturn(Collections.emptyList());

    when(objectMapperMock.readValue(anyString(), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[] {mappingDocEntry});
    migration.apply(
        mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock, updateCodeSystemTaskMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    ArgumentCaptor<CodeSystem> codeSystemCaptor = ArgumentCaptor.forClass(CodeSystem.class);
    verify(codeSystemRepositoryMock, times(1)).save(codeSystemCaptor.capture());

    // New FHIR Code System
    assertEquals(mappingDocEntry.getOid(), codeSystemCaptor.getValue().getOid());
    assertTrue(codeSystemCaptor.getValue().isFhir());
    assertTrue(codeSystemCaptor.getValue().isQdm());
    assertEquals(
        mappingDocEntry.getVersions().get(0).getFhir(),
        codeSystemCaptor.getValue().getVersion().getFhirVersion());
    assertEquals(
        mappingDocEntry.getVersions().get(0).getVsac(),
        codeSystemCaptor.getValue().getVersion().getVsacVersion());
  }

  @Test
  void testNewCodeSystemWithOnlyVsacVersions() throws JacksonException {
    mappingDocEntry.setVersions(List.of(CodeSystemEntry.Version.builder().vsac("9.9.9").build()));

    when(codeSystemRepositoryMock.findAll()).thenReturn(Collections.emptyList());

    when(objectMapperMock.readValue(anyString(), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[] {mappingDocEntry});

    migration.apply(
        mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock, updateCodeSystemTaskMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    ArgumentCaptor<CodeSystem> codeSystemCaptor = ArgumentCaptor.forClass(CodeSystem.class);
    verify(codeSystemRepositoryMock, times(1)).save(codeSystemCaptor.capture());

    // New QDM Code System
    assertFalse(codeSystemCaptor.getValue().isFhir());
    assertTrue(codeSystemCaptor.getValue().isQdm());
    assertTrue(codeSystemCaptor.getValue().isLatestVersion());
    assertTrue(codeSystemCaptor.getValue().isVsacSearchable());
    assertEquals(mappingDocEntry.getOid(), codeSystemCaptor.getValue().getOid());
    assertEquals(
        mappingDocEntry.getVersions().get(0).getVsac(),
        codeSystemCaptor.getValue().getVersion().getVsacVersion());
  }

  @Test
  void testNewCodeSystemWithOnlyFhirVersions() throws JacksonException {
    mappingDocEntry.setVersions(List.of(CodeSystemEntry.Version.builder().fhir("1.0.0").build()));
    mappingDocEntry.setOid("NOT.IN.VSAC.TEST");
    when(codeSystemRepositoryMock.findAll()).thenReturn(Collections.emptyList());

    when(objectMapperMock.readValue(anyString(), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[] {mappingDocEntry});
    migration.apply(
        mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock, updateCodeSystemTaskMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    ArgumentCaptor<CodeSystem> codeSystemCaptor = ArgumentCaptor.forClass(CodeSystem.class);
    verify(codeSystemRepositoryMock, times(1)).save(codeSystemCaptor.capture());

    assertTrue(codeSystemCaptor.getValue().isFhir());
    assertFalse(codeSystemCaptor.getValue().isQdm());
    assertTrue(codeSystemCaptor.getValue().isLatestVersion());
    assertFalse(codeSystemCaptor.getValue().isVsacSearchable());
    assertEquals(mappingDocEntry.getOid(), codeSystemCaptor.getValue().getOid());
    assertEquals(
        mappingDocEntry.getVersions().get(0).getFhir(),
        codeSystemCaptor.getValue().getVersion().getFhirVersion());
  }

  @Test
  void testRollbackUsesOriginalCodeSystems() throws JacksonException {
    when(objectMapperMock.readValue(anyString(), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[] {mappingDocEntry});
    when(codeSystemRepositoryMock.findAll()).thenReturn(List.of(existingCodeSystem));

    // Deterministic values
    assertTrue(existingCodeSystem.isFhir());
    assertFalse(existingCodeSystem.isQdm());

    migration.apply(
        mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock, updateCodeSystemTaskMock);
    migration.rollback(mongoTemplateMock, codeSystemRepositoryMock);

    verify(codeSystemRepositoryMock, times(1)).saveAll(codeSystemListCaptor.capture());
    List<List<CodeSystem>> codeSystemSaves = codeSystemListCaptor.getAllValues();
    List<CodeSystem> rolledBackCodeSystems = codeSystemSaves.get(0);
    assertEquals(1, rolledBackCodeSystems.size());
    assertEquals(existingCodeSystem.getId(), rolledBackCodeSystems.get(0).getId());
    assertTrue(rolledBackCodeSystems.get(0).isFhir());
    assertFalse(rolledBackCodeSystems.get(0).isQdm());
    assertFalse(rolledBackCodeSystems.get(0).isLatestVersion());
    assertTrue(rolledBackCodeSystems.get(0).isVsacSearchable());
  }

  @Test
  void testRollbackDoesNothingIfMappingDocFailsParsing() throws JacksonException {
    when(objectMapperMock.readValue(anyString(), eq(CodeSystemEntry[].class)))
        .thenThrow(new JacksonException("Test Exception") {});
    assertThrows(
        RuntimeException.class,
        () ->
            migration.apply(
                mongoTemplateMock,
                codeSystemRepositoryMock,
                objectMapperMock,
                updateCodeSystemTaskMock));
    migration.rollback(mongoTemplateMock, codeSystemRepositoryMock);

    verify(codeSystemRepositoryMock, never()).saveAll(anyList());
  }

  /* test coverage for: deserializeFromFile() -> loadMappingDoc()
   * line 144: throw new UncheckedIOException
   */
  @Test
  void loadMappingDocThrowsUncheckedIOExceptionWhenResourceMissing() {
    LoadCodeSystemMappingData migration = new LoadCodeSystemMappingData();
    try {
      java.lang.reflect.Field filePathField =
          LoadCodeSystemMappingData.class.getDeclaredField("FILE_PATH");
      filePathField.setAccessible(true);
      filePathField.set(migration, "/not-found.json");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    ObjectMapper objectMapper = new ObjectMapper();
    // Use reflection to invoke private method
    assertThrows(
        UncheckedIOException.class,
        () -> {
          try {
            java.lang.reflect.Method method =
                LoadCodeSystemMappingData.class.getDeclaredMethod(
                    "deserializeFromFile", ObjectMapper.class);
            method.setAccessible(true);
            method.invoke(migration, objectMapper);
          } catch (Exception ex) {
            // UncheckedIOException is wrapped in InvocationTargetException
            Throwable cause = ex.getCause();
            if (cause instanceof UncheckedIOException) {
              throw (UncheckedIOException) cause;
            }
            throw new RuntimeException(ex);
          }
        });
  }

  /*
   * branch coverage for apply(), line 76:
   * Objects.equals(cs.getFullUrl(), entry.getUrl())
   */
  @Test
  void applyFullUrlNotEqualsEntryUrl() throws Exception {
    // Setup: create a CodeSystem with a different fullUrl than entry.getUrl()
    CodeSystem codeSystem =
        CodeSystem.builder()
            .id("test-id")
            .oid("urn:oid:1.1.1.1")
            .name("TestCodeSystem")
            .title("Test Code System")
            .fullUrl("http://example.com/fhir/CodeSystem/DIFFERENT") // Not equal to entry.getUrl()
            .version(CodeSystem.Version.builder().fhirVersion("1.0.0").build())
            .build();
    CodeSystemEntry entry =
        CodeSystemEntry.builder()
            .oid("urn:oid:1.1.1.1")
            .url("http://example.com/fhir/CodeSystem/ORIGINAL") // entry.getUrl()
            .name("TestCodeSystemEntry")
            .versions(
                List.of(CodeSystemEntry.Version.builder().fhir("1.0.0").vsac("1.0.0").build()))
            .build();
    // Mock repository to return the codeSystem
    when(codeSystemRepositoryMock.findAll()).thenReturn(List.of(codeSystem));
    // Mock objectMapper to return the entry
    when(objectMapperMock.readValue(anyString(), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[] {entry});
    // Run apply
    migration.apply(
        mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock, updateCodeSystemTaskMock);
    // Verify that the filter branch where cs.getFullUrl() != entry.getUrl() is covered
    // (No update to existing codeSystem, new codeSystem is created)
    verify(codeSystemRepositoryMock, times(1)).findAll();
    verify(codeSystemRepositoryMock, times(1)).save(any(CodeSystem.class));
  }

  /* branch coverage for deserializeFromFile(), line 128:
   * if (entries != null) {
   */
  @Test
  void deserializeFromFileEntriesNull() throws Exception {
    // Mock objectMapper to return null for entries
    when(objectMapperMock.readValue(anyString(), eq(CodeSystemEntry[].class))).thenReturn(null);
    // Use reflection to invoke private method
    java.lang.reflect.Method method =
        LoadCodeSystemMappingData.class.getDeclaredMethod(
            "deserializeFromFile", ObjectMapper.class);
    method.setAccessible(true);
    Object result = method.invoke(migration, objectMapperMock);
    assertTrue(result instanceof List);
    assertTrue(((List<?>) result).isEmpty());
  }
}
