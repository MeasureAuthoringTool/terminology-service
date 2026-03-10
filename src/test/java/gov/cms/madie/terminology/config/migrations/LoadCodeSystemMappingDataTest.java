package gov.cms.madie.terminology.config.migrations;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.models.mapping.CodeSystemEntry;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.task.UpdateCodeSystemTask;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoadCodeSystemMappingDataTest {

  @Captor public ArgumentCaptor<List<CodeSystem>> codeSystemListCaptor;

  @InjectMocks
  private final LoadCodeSystemMappingData migration = new LoadCodeSystemMappingData();

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

    doNothing().when(mongoTemplateMock).dropCollection(anyString());
    doNothing().when(updateCodeSystemTaskMock).updateCodeSystems();
  }

  @Test
  void testExistingCodeSystemWithMatchingFhirAndVsacVersions() throws IOException {

    when(codeSystemRepositoryMock.findAll()).thenReturn(List.of(existingCodeSystem));

    when(objectMapperMock.readValue(any(File.class), eq(CodeSystemEntry[].class)))
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
  void testExistingCodeSystemWithMatchingFhirAndDifferingVsacVersions() throws IOException {
    mappingDocEntry.setVersions(
        List.of(CodeSystemEntry.Version.builder().fhir("1.0.0").vsac("9.9.9").build()));

    when(codeSystemRepositoryMock.findAll()).thenReturn(List.of(existingCodeSystem));

    when(objectMapperMock.readValue(any(File.class), eq(CodeSystemEntry[].class)))
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
  void testExistingCodeSystemWithMultipleVersions() throws IOException {
    mappingDocEntry.setVersions(
        List.of(
            // Latest first
            CodeSystemEntry.Version.builder().fhir("2.0.0").vsac("9.9.9").build(),
            CodeSystemEntry.Version.builder().fhir("1.0.0").vsac("8.8.8").build()));

    when(codeSystemRepositoryMock.findAll()).thenReturn(List.of(existingCodeSystem));

    when(objectMapperMock.readValue(any(File.class), eq(CodeSystemEntry[].class)))
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

    assertEquals(existingCodeSystem.getTitle(),
      codeSystemSaves.get(1).getTitle());
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
  void testExistingCodeSystemWithOnlyVsacVersions() throws IOException {
    mappingDocEntry.setVersions(List.of(CodeSystemEntry.Version.builder().vsac("9.9.9").build()));

    when(codeSystemRepositoryMock.findAll()).thenReturn(List.of(existingCodeSystem));

    when(objectMapperMock.readValue(any(File.class), eq(CodeSystemEntry[].class)))
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
  void testExistingCodeSystemWithOnlyFhirVersions() throws IOException {
    mappingDocEntry.setVersions(List.of(CodeSystemEntry.Version.builder().fhir("1.0.0").build()));
    when(codeSystemRepositoryMock.findAll()).thenReturn(List.of(existingCodeSystem));

    when(objectMapperMock.readValue(any(File.class), eq(CodeSystemEntry[].class)))
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
  void testNewCodeSystemWithMatchingFhirAndVsacVersions() throws IOException {
    mappingDocEntry.setVersions(
        List.of(CodeSystemEntry.Version.builder().fhir("1.0.0").vsac("1.0.0").build()));
    when(codeSystemRepositoryMock.findAll()).thenReturn(Collections.emptyList());

    when(objectMapperMock.readValue(any(File.class), eq(CodeSystemEntry[].class)))
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
  void testNewCodeSystemWithDifferingFhirAndVsacVersions() throws IOException {
    mappingDocEntry.setVersions(
        List.of(CodeSystemEntry.Version.builder().fhir("1.0.0").vsac("9.9.9").build()));

    when(codeSystemRepositoryMock.findAll()).thenReturn(Collections.emptyList());

    when(objectMapperMock.readValue(any(File.class), eq(CodeSystemEntry[].class)))
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
  void testNewCodeSystemWithOnlyVsacVersions() throws IOException {
    mappingDocEntry.setVersions(List.of(CodeSystemEntry.Version.builder().vsac("9.9.9").build()));

    when(codeSystemRepositoryMock.findAll()).thenReturn(Collections.emptyList());

    when(objectMapperMock.readValue(any(File.class), eq(CodeSystemEntry[].class)))
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
  void testNewCodeSystemWithOnlyFhirVersions() throws IOException {
    mappingDocEntry.setVersions(List.of(CodeSystemEntry.Version.builder().fhir("1.0.0").build()));
    mappingDocEntry.setOid("NOT.IN.VSAC.TEST");
    when(codeSystemRepositoryMock.findAll()).thenReturn(Collections.emptyList());

    when(objectMapperMock.readValue(any(File.class), eq(CodeSystemEntry[].class)))
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
  void testRollbackUsesOriginalCodeSystems() {
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
}
