package gov.cms.madie.terminology.config.migrations;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.models.mapping.CodeSystemEntry;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import io.flamingock.springboot.testsupport.FlamingockSpringBootTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@FlamingockSpringBootTest
@ExtendWith(MockitoExtension.class)
@EnableFlamingock(stages = @Stage(location = "gov.cms.madie.terminology.config.migrations"))
class LoadCodeSystemMappingDataTest {

  @Captor public ArgumentCaptor<List<CodeSystem>> codeSystemListCaptor;

  @InjectMocks
  private final _0001__LoadCodeSystemMappingData migration = new _0001__LoadCodeSystemMappingData();

  @Mock private MongoTemplate mongoTemplateMock;
  @Mock private CodeSystemRepository codeSystemRepositoryMock;
  @Mock ObjectMapper objectMapperMock;

  private CodeSystem existingCodeSystem;
  private CodeSystemEntry mappingDocEntry;

  @BeforeEach
  void init() {
    existingCodeSystem =
        CodeSystem.builder()
            .id("Existing Code System1.0.0")
            .oid("urn:oid:1.1.1.1")
            .name("Existing")
            .title("Existing Code System")
            .fullUrl("http://example.com/fhir/CodeSystem/test")
            .version("1.0.0")
            .build();

    mappingDocEntry =
        CodeSystemEntry.builder()
            .oid("urn:oid:1.1.1.1")
            .url("http://example.com/fhir/CodeSystem/test")
            .name("MappingDocCodeSystem")
            .versions(
                List.of(CodeSystemEntry.Version.builder().fhir("1.0.0").vsac("1.0.0").build()))
            .build();
  }

  @Test
  void testSetFhirOnAllExistingCodeSystems() throws IOException {
    when(codeSystemRepositoryMock.findAll())
        .thenReturn(
            List.of(
                CodeSystem.builder()
                    .id("Test Code System1.0.0")
                    .oid("urn:oid:1.1.1.1")
                    .name("Test Code System")
                    .version("1.0.0")
                    .build(),
                CodeSystem.builder()
                    .id("testCodeSystem22021-03-01")
                    .oid("urn:oid:2.2.2.2")
                    .name("Test Code System 2")
                    .version("2021-03-01")
                    .build()));
    when(objectMapperMock.readValue(any(File.class), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[0]);

    migration.apply(mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    verify(codeSystemRepositoryMock, times(1)).saveAll(codeSystemListCaptor.capture());

    List<CodeSystem> savedCodeSystems = codeSystemListCaptor.getValue();
    assertEquals("Test Code System1.0.0", savedCodeSystems.get(0).getId());
    assertTrue(savedCodeSystems.get(0).isFhir());
    assertFalse(savedCodeSystems.get(0).isQdm());

    assertEquals("testCodeSystem22021-03-01", savedCodeSystems.get(1).getId());
    assertTrue(savedCodeSystems.get(1).isFhir());
    assertFalse(savedCodeSystems.get(1).isQdm());
  }

  @Test
  void testExistingCodeSystemWithMatchingFhirAndVsacVersions() throws IOException {

    when(codeSystemRepositoryMock.findAll()).thenReturn(List.of(existingCodeSystem));

    when(objectMapperMock.readValue(any(File.class), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[] {mappingDocEntry});

    migration.apply(mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    ArgumentCaptor<CodeSystem> codeSystemCaptor = ArgumentCaptor.forClass(CodeSystem.class);
    verify(codeSystemRepositoryMock, times(1)).save(codeSystemCaptor.capture());
    assertEquals(
        mappingDocEntry.getVersions().get(0).getVsac(),
        codeSystemCaptor.getValue().getQdmDisplayVersion());
    assertEquals(
        mappingDocEntry.getVersions().get(0).getVsac(), codeSystemCaptor.getValue().getVersion());
    assertEquals(
        mappingDocEntry.getVersions().get(0).getFhir(), codeSystemCaptor.getValue().getVersion());
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
    migration.apply(mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    ArgumentCaptor<CodeSystem> codeSystemCaptor = ArgumentCaptor.forClass(CodeSystem.class);
    verify(codeSystemRepositoryMock, times(2)).save(codeSystemCaptor.capture());

    // Existing FHIR Code System
    List<CodeSystem> capturedSaves = codeSystemCaptor.getAllValues();
    assertEquals(existingCodeSystem.getId(), capturedSaves.get(0).getId());
    assertTrue(capturedSaves.get(0).isFhir());
    assertFalse(capturedSaves.get(0).isQdm());
    assertEquals(existingCodeSystem.getVersion(), capturedSaves.get(0).getVersion());

    // New QDM Code System
    assertEquals(existingCodeSystem.getOid(), capturedSaves.get(1).getOid());
    String expectedQdmId = mappingDocEntry.getName() + mappingDocEntry.getVersions().get(0).getVsac();
    assertEquals(expectedQdmId, capturedSaves.get(1).getId());
    assertFalse(capturedSaves.get(1).isFhir());
    assertTrue(capturedSaves.get(1).isQdm());
    assertEquals(mappingDocEntry.getVersions().get(0).getVsac(), capturedSaves.get(1).getVersion());
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
    migration.apply(mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    ArgumentCaptor<CodeSystem> codeSystemCaptor = ArgumentCaptor.forClass(CodeSystem.class);
    verify(codeSystemRepositoryMock, times(4)).save(codeSystemCaptor.capture());

    List<CodeSystem> codeSystemSaves = codeSystemCaptor.getAllValues();
    assertEquals("MappingDocCodeSystem2.0.0", codeSystemSaves.get(0).getId());
    assertNull(codeSystemSaves.get(0).getTitle());
    assertTrue(codeSystemSaves.get(0).isFhir());
    assertFalse(codeSystemSaves.get(0).isQdm());
    assertTrue(codeSystemSaves.get(0).isLatestVersion());

    assertEquals("MappingDocCodeSystem9.9.9", codeSystemSaves.get(1).getId());
    assertNull(codeSystemSaves.get(1).getTitle());
    assertTrue(codeSystemSaves.get(1).isLatestVersion());
    assertFalse(codeSystemSaves.get(1).isFhir());
    assertTrue(codeSystemSaves.get(1).isQdm());

    assertEquals(
        existingCodeSystem.getTitle() + existingCodeSystem.getVersion(),
        codeSystemSaves.get(2).getId());
    assertEquals(existingCodeSystem.getTitle(), codeSystemSaves.get(2).getTitle());
    assertEquals(existingCodeSystem.getName(), codeSystemSaves.get(2).getName());
    assertEquals(existingCodeSystem.getFullUrl(), codeSystemSaves.get(2).getFullUrl());
    assertFalse(codeSystemSaves.get(2).isLatestVersion());
    assertTrue(codeSystemSaves.get(2).isFhir());
    assertFalse(codeSystemSaves.get(2).isQdm());

    assertEquals("MappingDocCodeSystem8.8.8", codeSystemSaves.get(3).getId());
    assertNull(codeSystemSaves.get(3).getTitle());
    assertFalse(codeSystemSaves.get(3).isLatestVersion());
    assertFalse(codeSystemSaves.get(3).isFhir());
    assertTrue(codeSystemSaves.get(3).isQdm());
  }

  @Test
  void testExistingCodeSystemWithOnlyVsacVersions() throws IOException {
    mappingDocEntry.setVersions(List.of(CodeSystemEntry.Version.builder().vsac("9.9.9").build()));

    when(codeSystemRepositoryMock.findAll()).thenReturn(List.of(existingCodeSystem));

    when(objectMapperMock.readValue(any(File.class), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[] {mappingDocEntry});
    migration.apply(mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    ArgumentCaptor<CodeSystem> codeSystemCaptor = ArgumentCaptor.forClass(CodeSystem.class);
    verify(codeSystemRepositoryMock, times(1)).save(codeSystemCaptor.capture());

    assertFalse(codeSystemCaptor.getValue().isFhir());
    assertTrue(codeSystemCaptor.getValue().isQdm());
    assertEquals("MappingDocCodeSystem9.9.9", codeSystemCaptor.getValue().getId());
    assertEquals("urn:oid:1.1.1.1", codeSystemCaptor.getValue().getOid());
    assertEquals("9.9.9", codeSystemCaptor.getValue().getVersion());
  }

  @Test
  void testExistingCodeSystemWithOnlyFhirVersions() throws IOException {
    mappingDocEntry.setVersions(List.of(CodeSystemEntry.Version.builder().fhir("1.0.0").build()));
    when(codeSystemRepositoryMock.findAll()).thenReturn(List.of(existingCodeSystem));

    when(objectMapperMock.readValue(any(File.class), eq(CodeSystemEntry[].class)))
        .thenReturn(
            new CodeSystemEntry[] {
              mappingDocEntry
            });
    migration.apply(mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock);

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
    mappingDocEntry.setVersions( List.of(
      CodeSystemEntry.Version.builder().fhir("1.0.0").vsac("1.0.0").build()));
    when(codeSystemRepositoryMock.findAll()).thenReturn(Collections.emptyList());

    when(objectMapperMock.readValue(any(File.class), eq(CodeSystemEntry[].class)))
        .thenReturn(
            new CodeSystemEntry[] {
              mappingDocEntry
            });

    migration.apply(mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    ArgumentCaptor<CodeSystem> codeSystemCaptor = ArgumentCaptor.forClass(CodeSystem.class);
    verify(codeSystemRepositoryMock, times(1)).save(codeSystemCaptor.capture());
    assertTrue(codeSystemCaptor.getValue().isQdm());
    assertTrue(codeSystemCaptor.getValue().isFhir());
    assertEquals(mappingDocEntry.getVersions().get(0).getFhir(), codeSystemCaptor.getValue().getVersion());
    assertEquals(mappingDocEntry.getVersions().get(0).getVsac(), codeSystemCaptor.getValue().getVersion());
    assertEquals(mappingDocEntry.getOid(), codeSystemCaptor.getValue().getOid());

    String expectedId = mappingDocEntry.getName() + mappingDocEntry.getVersions().get(0).getFhir();
    assertEquals(expectedId, codeSystemCaptor.getValue().getId());
  }

  @Test
  void testNewCodeSystemWithDifferingFhirAndVsacVersions() throws IOException {
    mappingDocEntry.setVersions(
        List.of(CodeSystemEntry.Version.builder().fhir("1.0.0").vsac("9.9.9").build()));

    when(codeSystemRepositoryMock.findAll()).thenReturn(Collections.emptyList());

    when(objectMapperMock.readValue(any(File.class), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[] {mappingDocEntry});
    migration.apply(mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    ArgumentCaptor<CodeSystem> codeSystemCaptor = ArgumentCaptor.forClass(CodeSystem.class);
    verify(codeSystemRepositoryMock, times(2)).save(codeSystemCaptor.capture());

    List<CodeSystem> capturedSaves = codeSystemCaptor.getAllValues();

    // New FHIR Code System
    assertEquals(mappingDocEntry.getOid(), capturedSaves.get(0).getOid());

    String expectedFhirId = mappingDocEntry.getName() + mappingDocEntry.getVersions().get(0).getFhir();
    assertEquals(expectedFhirId, capturedSaves.get(0).getId());
    assertTrue(capturedSaves.get(0).isFhir());
    assertFalse(capturedSaves.get(0).isQdm());
    assertEquals(mappingDocEntry.getVersions().get(0).getFhir(), capturedSaves.get(0).getVersion());

    // New QDM Code System
    assertEquals(mappingDocEntry.getOid(), capturedSaves.get(1).getOid());

    String expectedQdmId = mappingDocEntry.getName() + mappingDocEntry.getVersions().get(0).getVsac();
    assertEquals(expectedQdmId, capturedSaves.get(1).getId());
    assertFalse(capturedSaves.get(1).isFhir());
    assertTrue(capturedSaves.get(1).isQdm());
    assertEquals(mappingDocEntry.getVersions().get(0).getVsac(), capturedSaves.get(1).getVersion());
  }

  @Test
  void testNewCodeSystemWithOnlyVsacVersions() throws IOException {
    mappingDocEntry.setVersions(List.of(CodeSystemEntry.Version.builder().vsac("9.9.9").build()));

    when(codeSystemRepositoryMock.findAll()).thenReturn(Collections.emptyList());

    when(objectMapperMock.readValue(any(File.class), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[] {mappingDocEntry});
    migration.apply(mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    ArgumentCaptor<CodeSystem> codeSystemCaptor = ArgumentCaptor.forClass(CodeSystem.class);
    verify(codeSystemRepositoryMock, times(1)).save(codeSystemCaptor.capture());

    // New QDM Code System
    assertFalse(codeSystemCaptor.getValue().isFhir());
    assertTrue(codeSystemCaptor.getValue().isQdm());
    assertTrue(codeSystemCaptor.getValue().isLatestVersion());
    assertTrue(codeSystemCaptor.getValue().isVsacSearchable());
    String expectedQdmId = mappingDocEntry.getName() + mappingDocEntry.getVersions().get(0).getVsac();
    assertEquals(expectedQdmId, codeSystemCaptor.getValue().getId());
    assertEquals(mappingDocEntry.getOid(), codeSystemCaptor.getValue().getOid());
    assertEquals(mappingDocEntry.getVersions().get(0).getVsac(), codeSystemCaptor.getValue().getVersion());
    assertEquals(mappingDocEntry.getVersions().get(0).getVsac(), codeSystemCaptor.getValue().getQdmDisplayVersion());
  }

  @Test
  void testNewCodeSystemWithOnlyFhirVersions() throws IOException {
    mappingDocEntry.setVersions(List.of(CodeSystemEntry.Version.builder().fhir("1.0.0").build()));
    when(codeSystemRepositoryMock.findAll()).thenReturn(Collections.emptyList());

    when(objectMapperMock.readValue(any(File.class), eq(CodeSystemEntry[].class)))
        .thenReturn(new CodeSystemEntry[] {mappingDocEntry});
    migration.apply(mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock);

    verify(codeSystemRepositoryMock, times(1)).findAll();

    ArgumentCaptor<CodeSystem> codeSystemCaptor = ArgumentCaptor.forClass(CodeSystem.class);
    verify(codeSystemRepositoryMock, times(1)).save(codeSystemCaptor.capture());

    assertTrue(codeSystemCaptor.getValue().isFhir());
    assertFalse(codeSystemCaptor.getValue().isQdm());
    assertTrue(codeSystemCaptor.getValue().isLatestVersion());
    assertFalse(codeSystemCaptor.getValue().isVsacSearchable());
    String expectedFhirId = mappingDocEntry.getName() + mappingDocEntry.getVersions().get(0).getFhir();
    assertEquals(expectedFhirId, codeSystemCaptor.getValue().getId());
    assertEquals(mappingDocEntry.getOid(), codeSystemCaptor.getValue().getOid());
    assertEquals(mappingDocEntry.getVersions().get(0).getFhir(), codeSystemCaptor.getValue().getVersion());
  }

  @Test
  void testRollbackUsesOriginalCodeSystems() {
    when(codeSystemRepositoryMock.findAll()).thenReturn(List.of(existingCodeSystem));

    migration.apply(mongoTemplateMock, codeSystemRepositoryMock, objectMapperMock);
    migration.rollback(mongoTemplateMock, codeSystemRepositoryMock);

    verify(codeSystemRepositoryMock, times(2)).saveAll(codeSystemListCaptor.capture());
    List<List<CodeSystem>> codeSystemSaves = codeSystemListCaptor.getAllValues();
    List<CodeSystem> rolledBackCodeSystems = codeSystemSaves.get(1);
    assertEquals(1, rolledBackCodeSystems.size());
    assertEquals(existingCodeSystem.getId(), rolledBackCodeSystems.get(0).getId());
    assertFalse(rolledBackCodeSystems.get(0).isFhir());
    assertFalse(rolledBackCodeSystems.get(0).isQdm());
    assertFalse(rolledBackCodeSystems.get(0).isLatestVersion());
    assertFalse(rolledBackCodeSystems.get(0).isVsacSearchable());
  }
}
