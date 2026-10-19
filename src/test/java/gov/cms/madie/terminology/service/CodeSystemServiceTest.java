package gov.cms.madie.terminology.service;

import gov.cms.madie.terminology.exceptions.CodeSystemNotFoundException;
import gov.cms.madie.terminology.exceptions.DuplicateCodeSystemException;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeSystemServiceTest {
  @Mock private CodeSystemRepository codeSystemRepository;
  @InjectMocks private CodeSystemService codeSystemService;

  @Test
  void testCreateCodeSystem() {
    CodeSystem codeSystem =
        CodeSystem.builder()
            .title("LOINC")
            .name("LOINC")
            .version(
                CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isInVsac(false)
            .build();
    CodeSystem saved =
        codeSystem.toBuilder().id("LOINCversion2.40").build();

    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(codeSystemRepository.save(any(CodeSystem.class)))
        .thenReturn(saved);

    ArgumentCaptor<CodeSystem> captor =
        ArgumentCaptor.forClass(CodeSystem.class);

    CodeSystem result =
        codeSystemService.createCodeSystem(codeSystem);

    assertNotNull(result);
    assertEquals("LOINCversion2.40", result.getId());
    assertEquals("LOINC", result.getName());
    assertEquals("urn:oid:2.16.840.1.113883.6.1", result.getOid());
    assertEquals("https://loinc.org", result.getFullUrl());
    assertEquals("2.40", result.getVersion().getFhirVersion());
    verify(codeSystemRepository, times(1)).save(captor.capture());
    assertNotNull(captor.getValue().getLastUpdated());
    verify(codeSystemRepository, never()).findAllByOid(anyString());
  }

  @Test
  void testCreateCodeSystemDemotesExistingLatestVersionsWhenMarkedAsLatest() {
    CodeSystem incoming =
        CodeSystem.builder()
            .name("LOINC")
            .version(
                CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(true)
            .isInVsac(false)
            .build();

    CodeSystem existingLatest =
        CodeSystem.builder()
            .id("LOINCversion2.39")
            .name("LOINC")
            .version(
                CodeSystem.Version.builder()
                    .fhirVersion("2.39")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(true)
            .isInVsac(false)
            .build();

    CodeSystem existingNonLatest =
        CodeSystem.builder()
            .id("LOINCversion2.38")
            .name("LOINC")
            .version(
                CodeSystem.Version.builder()
                    .fhirVersion("2.38")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(false)
            .isInVsac(false)
            .build();

    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(codeSystemRepository.findAllByFullUrl(anyString()))
        .thenReturn(List.of(existingLatest, existingNonLatest));
    when(codeSystemRepository.save(any(CodeSystem.class)))
        .thenReturn(incoming.toBuilder().id("LOINCversion2.40").build());

    ArgumentCaptor<CodeSystem> captor =
        ArgumentCaptor.forClass(CodeSystem.class);

    codeSystemService.createCodeSystem(incoming);

    verify(codeSystemRepository, times(1)).saveAll(anyList());
    verify(codeSystemRepository, times(1)).save(captor.capture());

    assertTrue(captor.getValue().isLatestVersion());
    assertFalse(existingLatest.isLatestVersion());
    assertFalse(existingNonLatest.isLatestVersion());
  }

  @Test
  void testCreateCodeSystemThrowsDuplicateException() {
    CodeSystem codeSystem =
        CodeSystem.builder()
            .title("LOINC")
            .name("LOINC")
            .version(
                CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isInVsac(false)
            .build();

    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.of(codeSystem));

    assertThrows(
        DuplicateCodeSystemException.class, () -> codeSystemService.createCodeSystem(codeSystem));
    verify(codeSystemRepository, never())
        .save(any(CodeSystem.class));
  }

  @Test
  void testUpdateCodeSystem() {
    String id = "LOINCversion2.40";
    Date existingLastUpdatedUpstream = new Date(0);
    Date newLastUpdatedUpstream = new Date(existingLastUpdatedUpstream.getTime() + 1);

    CodeSystem existing =
        CodeSystem.builder()
            .id(id)
            .title("Old Title")
            .name("Old Name")
            .version(
                CodeSystem.Version.builder()
                    .fhirVersion("2.39")
                    .vsacVersion("2.39")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .versionId("1")
            .lastUpdatedUpstream(existingLastUpdatedUpstream)
            .isInVsac(true)
            .build();

    CodeSystem incoming =
        CodeSystem.builder()
            .title("New Title")
            .name("LOINC")
            .version(
                CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .vsacVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .versionId("2")
            .lastUpdatedUpstream(newLastUpdatedUpstream)
            .isInVsac(true)
            .build();

    when(codeSystemRepository.findById(id)).thenReturn(Optional.of(existing));
    when(codeSystemRepository.save(any(CodeSystem.class)))
        .thenReturn(existing);

    ArgumentCaptor<CodeSystem> captor =
        ArgumentCaptor.forClass(CodeSystem.class);

    codeSystemService.updateCodeSystem(id, incoming);

    verify(codeSystemRepository, times(1)).save(captor.capture());
    verify(codeSystemRepository, never()).findAllByOid(anyString());
    CodeSystem saved = captor.getValue();

    assertNotNull(saved.getLastUpdated());
    assertEquals("New Title", saved.getTitle());
    assertEquals("LOINC", saved.getName());
    assertEquals("urn:oid:2.16.840.1.113883.6.1", saved.getOid());
    assertEquals("https://loinc.org", saved.getFullUrl());
    assertEquals("2.40", saved.getVersion().getFhirVersion());
    assertEquals("2.40", saved.getVersion().getVsacVersion());
    assertEquals("2", saved.getVersionId());
    assertEquals(newLastUpdatedUpstream, saved.getLastUpdatedUpstream());
    assertTrue(saved.isInVsac());
  }

  @Test
  void testUpdateCodeSystemDemotesExistingLatestVersionsWhenMarkedAsLatest() {
    String id = "LOINCversion2.40";

    CodeSystem existing =
        CodeSystem.builder()
            .id(id)
            .name("LOINC")
            .version(
                CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(false)
            .isInVsac(false)
            .build();

    CodeSystem incoming =
        CodeSystem.builder()
            .name("LOINC")
            .version(
                CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(true)
            .isInVsac(false)
            .build();

    CodeSystem existingLatest =
        CodeSystem.builder()
            .id("LOINCversion2.39")
            .name("LOINC")
            .version(
                CodeSystem.Version.builder()
                    .fhirVersion("2.39")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(true)
            .isInVsac(false)
            .build();

    CodeSystem existingNonLatest =
        CodeSystem.builder()
            .id("LOINCversion2.38")
            .name("LOINC")
            .version(
                CodeSystem.Version.builder()
                    .fhirVersion("2.38")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(false)
            .isInVsac(false)
            .build();

    when(codeSystemRepository.findById(id)).thenReturn(Optional.of(existing));
    when(codeSystemRepository.findAllByFullUrl(anyString()))
        .thenReturn(List.of(existingLatest, existingNonLatest, existing));
    when(codeSystemRepository.save(any(CodeSystem.class)))
        .thenReturn(existing);

    ArgumentCaptor<CodeSystem> captor =
        ArgumentCaptor.forClass(CodeSystem.class);

    codeSystemService.updateCodeSystem(id, incoming);

    verify(codeSystemRepository, times(1)).saveAll(anyList());
    verify(codeSystemRepository, times(1)).save(captor.capture());

    assertTrue(captor.getValue().isLatestVersion());
    assertFalse(existingLatest.isLatestVersion());
    assertFalse(existingNonLatest.isLatestVersion());
  }

  @Test
  void testUpdateCodeSystemSetsLatestVersionFalseWhenNotMarkedAsLatest() {
    String id = "LOINCversion2.40";

    CodeSystem existing =
        CodeSystem.builder()
            .id(id)
            .name("LOINC")
            .version(
                CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(true)
            .isInVsac(false)
            .build();

    CodeSystem incoming =
        CodeSystem.builder()
            .name("LOINC")
            .version(
                CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isLatestVersion(false)
            .isInVsac(false)
            .build();

    when(codeSystemRepository.findById(id)).thenReturn(Optional.of(existing));
    when(codeSystemRepository.save(any(CodeSystem.class)))
        .thenReturn(existing);

    ArgumentCaptor<CodeSystem> captor =
        ArgumentCaptor.forClass(CodeSystem.class);

    codeSystemService.updateCodeSystem(id, incoming);

    verify(codeSystemRepository, never()).findAllByOid(anyString());
    verify(codeSystemRepository, times(1)).save(captor.capture());
    assertFalse(captor.getValue().isLatestVersion());
  }

  @Test
  void testUpdateCodeSystemPreservesOptionalFieldsWhenNotProvided() {
    String id = "LOINCversion2.40";
    Date existingLastUpdatedUpstream = new Date();
    CodeSystem existing =
        CodeSystem.builder()
            .id(id)
            .title("Old Title")
            .name("LOINC")
            .version(
                CodeSystem.Version.builder()
                    .fhirVersion("2.39")
                    .vsacVersion("2.39")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .versionId("1")
            .lastUpdatedUpstream(existingLastUpdatedUpstream)
            .isInVsac(true)
            .build();

    // title, vsacVersion, versionId, lastUpdatedUpstream omitted — existing values should be
    // preserved
    CodeSystem incoming =
        CodeSystem.builder()
            .name("LOINC")
            .version(
                CodeSystem.Version.builder()
                    .fhirVersion("2.40")
                    .build())
            .oid("urn:oid:2.16.840.1.113883.6.1")
            .fullUrl("https://loinc.org")
            .isInVsac(false)
            .build();

    when(codeSystemRepository.findById(id)).thenReturn(Optional.of(existing));
    when(codeSystemRepository.save(any(CodeSystem.class)))
        .thenReturn(existing);

    ArgumentCaptor<CodeSystem> captor =
        ArgumentCaptor.forClass(CodeSystem.class);

    codeSystemService.updateCodeSystem(id, incoming);

    verify(codeSystemRepository, times(1)).save(captor.capture());
    CodeSystem saved = captor.getValue();
    assertEquals("Old Title", saved.getTitle());
    assertEquals("2.39", saved.getVersion().getVsacVersion());
    assertEquals("1", saved.getVersionId());
    assertEquals(existingLastUpdatedUpstream, saved.getLastUpdatedUpstream());
  }

  @Test
  void testUpdateCodeSystemThrowsNotFoundException() {
    String id = "nonexistent";
    CodeSystem codeSystem =
        CodeSystem.builder().isInVsac(false).build();

    when(codeSystemRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(
        CodeSystemNotFoundException.class,
        () -> codeSystemService.updateCodeSystem(id, codeSystem));
    verify(codeSystemRepository, never())
        .save(any(CodeSystem.class));
  }

  @Test
  void testDeleteCodeSystem() {
    String id = "LOINCversion2.40";
    when(codeSystemRepository.existsById(id)).thenReturn(true);

    codeSystemService.deleteCodeSystem(id);

    verify(codeSystemRepository, times(1)).deleteById(id);
  }

  @Test
  void testDeleteCodeSystemThrowsNotFoundException() {
    String id = "nonexistent";
    when(codeSystemRepository.existsById(id)).thenReturn(false);

    assertThrows(CodeSystemNotFoundException.class, () -> codeSystemService.deleteCodeSystem(id));
    verify(codeSystemRepository, never()).deleteById(anyString());
  }

  @Test
  void testGetCodeSystemsWithBlankFilterAndSearchText() {
    Page<CodeSystem> mockPage = new PageImpl<>(List.of());
    PageRequest pageable = PageRequest.of(0, 10);
    when(codeSystemRepository.findAll(pageable)).thenReturn(mockPage);

    Page<CodeSystem> result =
        codeSystemService.getCodeSystems(pageable, "", "");

    verify(codeSystemRepository).findAll(pageable);
    assertEquals(mockPage, result);
  }

  @Test
  void testGetCodeSystemsFilterByTitle() {
    String searchText = "LOINC";
    Page<CodeSystem> mockPage = new PageImpl<>(List.of());
    PageRequest pageable = PageRequest.of(0, 10);
    when(codeSystemRepository.findAllByTitleContainingIgnoreCase(searchText, pageable))
        .thenReturn(mockPage);

    Page<CodeSystem> result =
        codeSystemService.getCodeSystems(pageable, "title", searchText);

    verify(codeSystemRepository).findAllByTitleContainingIgnoreCase(searchText, pageable);
    assertEquals(mockPage, result);
  }

  @Test
  void testGetCodeSystemsFilterByName() {
    String searchText = "LOINC";
    Page<CodeSystem> mockPage = new PageImpl<>(List.of());
    PageRequest pageable = PageRequest.of(0, 10);
    when(codeSystemRepository.findAllByNameContainingIgnoreCase(searchText, pageable))
        .thenReturn(mockPage);

    Page<CodeSystem> result =
        codeSystemService.getCodeSystems(pageable, "name", searchText);

    verify(codeSystemRepository).findAllByNameContainingIgnoreCase(searchText, pageable);
    assertEquals(mockPage, result);
  }

  @Test
  void testGetCodeSystemsFilterByVersion() {
    String searchText = "2.74";
    Page<CodeSystem> mockPage = new PageImpl<>(List.of());
    PageRequest pageable = PageRequest.of(0, 10);
    when(codeSystemRepository.findAllByVersionFhirVersionContainingIgnoreCase(searchText, pageable))
        .thenReturn(mockPage);

    Page<CodeSystem> result =
        codeSystemService.getCodeSystems(pageable, "version", searchText);

    verify(codeSystemRepository)
        .findAllByVersionFhirVersionContainingIgnoreCase(searchText, pageable);
    assertEquals(mockPage, result);
  }

  @Test
  void testGetCodeSystemsFilterByFullUrl() {
    String searchText = "http://loinc.org";
    Page<CodeSystem> mockPage = new PageImpl<>(List.of());
    PageRequest pageable = PageRequest.of(0, 10);
    when(codeSystemRepository.findAllByFullUrlContainingIgnoreCase(searchText, pageable))
        .thenReturn(mockPage);

    Page<CodeSystem> result =
        codeSystemService.getCodeSystems(pageable, "fullurl", searchText);

    verify(codeSystemRepository).findAllByFullUrlContainingIgnoreCase(searchText, pageable);
    assertEquals(mockPage, result);
  }

  @Test
  void testGetCodeSystemsFilterByFullUrlCaseInsensitive() {
    String searchText = "http://loinc.org";
    Page<CodeSystem> mockPage = new PageImpl<>(List.of());
    PageRequest pageable = PageRequest.of(0, 10);
    when(codeSystemRepository.findAllByFullUrlContainingIgnoreCase(searchText, pageable))
        .thenReturn(mockPage);

    Page<CodeSystem> result =
        codeSystemService.getCodeSystems(pageable, "FULLURL", searchText);

    verify(codeSystemRepository).findAllByFullUrlContainingIgnoreCase(searchText, pageable);
    assertEquals(mockPage, result);
  }

  @Test
  void testGetCodeSystemsFilterByDefaultAnyField() {
    String searchText = "SNOMEDCTcoding";
    Page<CodeSystem> mockPage = new PageImpl<>(List.of());
    PageRequest pageable = PageRequest.of(0, 10);
    when(codeSystemRepository.findAllByAnyFieldContainingIgnoreCase(searchText, pageable))
        .thenReturn(mockPage);

    Page<CodeSystem> result =
        codeSystemService.getCodeSystems(pageable, "unknownField", searchText);

    verify(codeSystemRepository).findAllByAnyFieldContainingIgnoreCase(searchText, pageable);
    assertEquals(mockPage, result);
  }

  @Test
  void testGetCodeSystemsWithNullFilterField() {
    String searchText = "search";
    Page<CodeSystem> mockPage = new PageImpl<>(List.of());
    PageRequest pageable = PageRequest.of(0, 10);
    when(codeSystemRepository.findAllByAnyFieldContainingIgnoreCase(searchText, pageable))
        .thenReturn(mockPage);

    Page<CodeSystem> result =
        codeSystemService.getCodeSystems(pageable, null, searchText);

    verify(codeSystemRepository).findAllByAnyFieldContainingIgnoreCase(searchText, pageable);
    assertEquals(mockPage, result);
  }

  @Test
  void testGetCodeSystemsWithNullSearchText() {
    Page<CodeSystem> mockPage = new PageImpl<>(List.of());
    PageRequest pageable = PageRequest.of(0, 10);
    when(codeSystemRepository.findAll(pageable)).thenReturn(mockPage);

    Page<CodeSystem> result =
        codeSystemService.getCodeSystems(pageable, "title", null);

    verify(codeSystemRepository).findAll(pageable);
    assertEquals(mockPage, result);
  }

  @Test
  void testGetCodeSystemsWithWhitespaceFilterField() {
    String searchText = "search";
    Page<CodeSystem> mockPage = new PageImpl<>(List.of());
    PageRequest pageable = PageRequest.of(0, 10);
    when(codeSystemRepository.findAllByAnyFieldContainingIgnoreCase(searchText, pageable))
        .thenReturn(mockPage);

    Page<CodeSystem> result =
        codeSystemService.getCodeSystems(pageable, "   ", searchText);

    verify(codeSystemRepository).findAllByAnyFieldContainingIgnoreCase(searchText, pageable);
    assertEquals(mockPage, result);
  }

  @Test
  void testGetCodeSystemsWithWhitespaceSearchText() {
    Page<CodeSystem> mockPage = new PageImpl<>(List.of());
    PageRequest pageable = PageRequest.of(0, 10);
    when(codeSystemRepository.findAll(pageable)).thenReturn(mockPage);

    Page<CodeSystem> result =
        codeSystemService.getCodeSystems(pageable, "title", "   ");

    verify(codeSystemRepository).findAll(pageable);
    assertEquals(mockPage, result);
  }

  @Test
  void getAllCodeSystemsReturnsOnlyVsacSystems() {
    CodeSystem inVsac = codeSystem("https://loinc.org", "2.40", true);
    CodeSystem notInVsac = codeSystem("https://example.org", "1", false);
    when(codeSystemRepository.findAll()).thenReturn(List.of(inVsac, notInVsac, inVsac));

    List<CodeSystem> result = codeSystemService.getAllCodeSystems();

    assertEquals(List.of(inVsac), result);
    verify(codeSystemRepository).findAll();
  }

  @Test
  void createCodeSystemDemotesLatestRecordsAndSavesNewRecord() {
    CodeSystem incoming = codeSystem("https://loinc.org", "2.40", false);
    incoming.setLatestVersion(true);
    CodeSystem existingLatest = codeSystem("https://loinc.org", "2.39", false);
    existingLatest.setLatestVersion(true);
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion("https://loinc.org", "2.40"))
        .thenReturn(Optional.empty());
    when(codeSystemRepository.findAllByFullUrl("https://loinc.org"))
        .thenReturn(List.of(existingLatest));
    when(codeSystemRepository.save(incoming)).thenReturn(incoming);

    CodeSystem result = codeSystemService.createCodeSystem(incoming);

    assertSame(incoming, result);
    assertNotNull(incoming.getLastUpdated());
    assertFalse(existingLatest.isLatestVersion());
    verify(codeSystemRepository).saveAll(List.of(existingLatest));
    verify(codeSystemRepository).save(incoming);
  }

  @Test
  void updateCodeSystemPreservesOptionalFieldsAndSetsLatestFlag() {
    CodeSystem existing = codeSystem("https://loinc.org", "2.39", true);
    existing.setId("loinc-2.39");
    existing.setTitle("Existing title");
    existing.setVersionId("existing-version");
    existing.setLastUpdatedUpstream(new Date(Instant.now().toEpochMilli()));
    CodeSystem incoming = codeSystem("https://loinc.org", "2.40", false);
    incoming.setLatestVersion(false);
    when(codeSystemRepository.findById("loinc-2.39")).thenReturn(Optional.of(existing));
    when(codeSystemRepository.save(any(CodeSystem.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ArgumentCaptor<CodeSystem> captor = ArgumentCaptor.forClass(CodeSystem.class);
    CodeSystem result = codeSystemService.updateCodeSystem("loinc-2.39", incoming);

    verify(codeSystemRepository).save(captor.capture());
    assertSame(existing, result);
    assertEquals("Existing title", captor.getValue().getTitle());
    assertEquals("existing-version", captor.getValue().getVersionId());
    assertNotNull(captor.getValue().getLastUpdatedUpstream());
    assertFalse(captor.getValue().isLatestVersion());
    assertFalse(captor.getValue().isInVsac());
  }

  private CodeSystem codeSystem(String url, String fhirVersion, boolean inVsac) {
    return CodeSystem.builder()
        .fullUrl(url)
        .name("LOINC")
        .version(CodeSystem.Version.builder().fhirVersion(fhirVersion).build())
        .isInVsac(inVsac)
        .build();
  }
}
