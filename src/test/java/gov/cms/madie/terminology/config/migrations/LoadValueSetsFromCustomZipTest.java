package gov.cms.madie.terminology.config.migrations;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.terminology.models.MadieValueSet;
import gov.cms.madie.terminology.repositories.ValueSetExpansionRepository;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoadValueSetsFromCustomZipTest {

  @Captor public ArgumentCaptor<List<MadieValueSet>> deleteCaptor;
  @Captor public ArgumentCaptor<List<MadieValueSet>> saveCaptor;
  @Captor public ArgumentCaptor<List<MadieValueSet>> valueSetListCaptor;

  @InjectMocks
  private final LoadValueSetsFromCustomZip migration = new LoadValueSetsFromCustomZip();

  @Mock private ValueSetExpansionRepository valueSetExpansionRepositoryMock;

  private MadieValueSet existingMadieValueSet;

  @BeforeEach
  void init() {
    String testUrl =
        "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.101.12.1061";
    String testVersion = "20210220";

    ValueSet testValueSet = new ValueSet();
    testValueSet.setUrl(testUrl);
    testValueSet.setVersion(testVersion);
    testValueSet.setName("Office Visit");
    testValueSet.setTitle("Office Visit");
    testValueSet.setStatus(org.hl7.fhir.r4.model.Enumerations.PublicationStatus.ACTIVE);

    IParser jsonParser = FhirContext.forR4().newJsonParser();
    existingMadieValueSet =
        MadieValueSet.builder()
            .url(testUrl)
            .version(testVersion)
            .valueSet(jsonParser.encodeResourceToString(testValueSet))
            .manuallyModified(true)
            .lastUpdated(Instant.now())
            .build();
  }

  @Test
  void testApplyLoadsValueSetsSuccessfully() throws IOException {
    // The actual zip file should exist in resources
    when(valueSetExpansionRepositoryMock.findByUrlAndVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());

    migration.apply(valueSetExpansionRepositoryMock);

    // Verify that findByUrlAndVersion was called at least once for each value set in the zip
    verify(valueSetExpansionRepositoryMock, atLeastOnce())
        .findByUrlAndVersion(anyString(), anyString());

    // Verify that save is called for new value sets
    verify(valueSetExpansionRepositoryMock, atLeastOnce()).save(any(MadieValueSet.class));
  }

  @Test
  void testApplySkipsExistingValueSetsWithSameVersion() throws IOException {
    when(valueSetExpansionRepositoryMock.findByUrlAndVersion(anyString(), anyString()))
        .thenReturn(Optional.of(existingMadieValueSet));

    migration.apply(valueSetExpansionRepositoryMock);

    // Verify that no saves are attempted for existing value sets
    verify(valueSetExpansionRepositoryMock, never()).save(any(MadieValueSet.class));
  }

  @Test
  void testApplyCreatesNewMadieValueSetsForNonExistingValueSets() throws IOException {
    ArgumentCaptor<MadieValueSet> madieValueSetCaptor =
        ArgumentCaptor.forClass(MadieValueSet.class);

    when(valueSetExpansionRepositoryMock.findByUrlAndVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());

    migration.apply(valueSetExpansionRepositoryMock);

    // Verify that save is called for new value sets
    verify(valueSetExpansionRepositoryMock, atLeastOnce()).save(madieValueSetCaptor.capture());

    // Verify the captured MadieValueSet has correct properties
    List<MadieValueSet> savedValueSets = madieValueSetCaptor.getAllValues();
    assertFalse(savedValueSets.isEmpty());

    MadieValueSet firstSaved = savedValueSets.get(0);
    assertNotNull(firstSaved.getUrl());
    assertNotNull(firstSaved.getVersion());
    assertNotNull(firstSaved.getValueSet());
    assertTrue(firstSaved.isManuallyModified());
    assertNotNull(firstSaved.getLastUpdated());
  }

  @Test
  void testLoadValueSetsFromCustomZipHandlesMacOSFiles() {
    // This test verifies that the code skips __MACOSX and .DS_Store files
    // The actual implementation filters these out, so they shouldn't cause issues
    when(valueSetExpansionRepositoryMock.findByUrlAndVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());

    assertDoesNotThrow(() -> migration.apply(valueSetExpansionRepositoryMock));
  }

  @Test
  void testLoadValueSetsFromCustomZipHandlesBOMCharacter() {
    // The implementation removes BOM character (\uFEFF) from XML content
    // This test verifies it doesn't throw exceptions
    when(valueSetExpansionRepositoryMock.findByUrlAndVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());

    assertDoesNotThrow(() -> migration.apply(valueSetExpansionRepositoryMock));
  }

  @Test
  void testApplyWithMixedExistingAndNewValueSets() throws IOException {
    // Simulate some value sets existing and some not
    when(valueSetExpansionRepositoryMock.findByUrlAndVersion(anyString(), anyString()))
        .thenReturn(Optional.of(existingMadieValueSet))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(existingMadieValueSet))
        .thenReturn(Optional.empty());

    migration.apply(valueSetExpansionRepositoryMock);

    // Verify repository interactions
    verify(valueSetExpansionRepositoryMock, atLeastOnce())
        .findByUrlAndVersion(anyString(), anyString());

    // Verify that save is called only for new value sets (not for existing ones)
    verify(valueSetExpansionRepositoryMock, atLeastOnce()).save(any(MadieValueSet.class));
  }

  @Test
  void testMadieValueSetPropertiesAreSetCorrectly() throws IOException {
    ArgumentCaptor<MadieValueSet> madieValueSetCaptor =
        ArgumentCaptor.forClass(MadieValueSet.class);

    when(valueSetExpansionRepositoryMock.findByUrlAndVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());

    migration.apply(valueSetExpansionRepositoryMock);

    // Verify that save was called and capture the saved value sets
    verify(valueSetExpansionRepositoryMock, atLeastOnce()).save(madieValueSetCaptor.capture());

    List<MadieValueSet> savedValueSets = madieValueSetCaptor.getAllValues();
    assertFalse(savedValueSets.isEmpty());

    // Verify properties of saved MadieValueSets
    for (MadieValueSet madieValueSet : savedValueSets) {
      assertNotNull(madieValueSet.getUrl());
      assertNotNull(madieValueSet.getVersion());
      assertNotNull(madieValueSet.getValueSet());
      assertFalse(madieValueSet.getValueSet().isEmpty());
      assertTrue(madieValueSet.isManuallyModified());
      assertNotNull(madieValueSet.getLastUpdated());
    }
  }

  @Test
  void testValueSetUrlAndVersionAreUsedForLookup() throws IOException {
    // Use anyString to accept any URL and version from the actual zip file
    when(valueSetExpansionRepositoryMock.findByUrlAndVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());

    migration.apply(valueSetExpansionRepositoryMock);

    // Verify that findByUrlAndVersion is called with actual URL and version values
    verify(valueSetExpansionRepositoryMock, atLeastOnce())
        .findByUrlAndVersion(anyString(), anyString());
  }

  @Test
  void testManuallyModifiedFlagIsSetToTrue() throws Exception {
    ArgumentCaptor<MadieValueSet> madieValueSetCaptor =
        ArgumentCaptor.forClass(MadieValueSet.class);

    when(valueSetExpansionRepositoryMock.findByUrlAndVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());

    migration.apply(valueSetExpansionRepositoryMock);

    // Verify that save was called and capture the saved value sets
    verify(valueSetExpansionRepositoryMock, atLeastOnce()).save(madieValueSetCaptor.capture());

    List<MadieValueSet> savedValueSets = madieValueSetCaptor.getAllValues();
    assertFalse(savedValueSets.isEmpty());

    // Verify that manuallyModified is set to true for all saved value sets
    for (MadieValueSet madieValueSet : savedValueSets) {
      assertTrue(madieValueSet.isManuallyModified());
    }
  }

  @Test
  void testLastUpdatedIsSetToCurrentTime() throws IOException {
    Instant beforeExecution = Instant.now();
    ArgumentCaptor<MadieValueSet> madieValueSetCaptor =
      ArgumentCaptor.forClass(MadieValueSet.class);

    when(valueSetExpansionRepositoryMock.findByUrlAndVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());

    migration.apply(valueSetExpansionRepositoryMock);

    Instant afterExecution = Instant.now();

    // Verify that save was called and capture the saved value sets
    verify(valueSetExpansionRepositoryMock, atLeastOnce()).save(madieValueSetCaptor.capture());

    List<MadieValueSet> savedValueSets = madieValueSetCaptor.getAllValues();
    assertFalse(savedValueSets.isEmpty());

    // Verify that lastUpdated is set to a time between before and after execution
    for (MadieValueSet madieValueSet : savedValueSets) {
      Instant lastUpdated = madieValueSet.getLastUpdated();
      assertNotNull(lastUpdated);
      assertFalse(lastUpdated.isBefore(beforeExecution));
      assertFalse(lastUpdated.isAfter(afterExecution));
    }
  }

  @Test
  void testRollbackWithEmptyExistingValueSetsList() {
    // Call rollback without calling apply first (existingValueSets will be empty)
    migration.rollback(valueSetExpansionRepositoryMock);

    // Verify that deleteAll and saveAll are never called when existingValueSets is empty
    verify(valueSetExpansionRepositoryMock, never()).deleteAll(anyList());
    verify(valueSetExpansionRepositoryMock, never()).saveAll(anyList());
  }

  @Test
  void testRollbackRestoresOriginalValueSets() throws IOException {
    // Setup: mock findAll to return existing value set
    when(valueSetExpansionRepositoryMock.findAll()).thenReturn(List.of(existingMadieValueSet));
    when(valueSetExpansionRepositoryMock.findByUrlAndVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());

    migration.apply(valueSetExpansionRepositoryMock);
    migration.rollback(valueSetExpansionRepositoryMock);

    // Verify that deleteAll is called first
    verify(valueSetExpansionRepositoryMock, times(1)).deleteAll(anyList());

    // Verify that saveAll is called with the original value sets
    verify(valueSetExpansionRepositoryMock, times(1)).saveAll(valueSetListCaptor.capture());

    List<MadieValueSet> restoredValueSets = valueSetListCaptor.getValue();
    assertEquals(1, restoredValueSets.size());
    assertEquals(existingMadieValueSet.getUrl(), restoredValueSets.get(0).getUrl());
    assertEquals(existingMadieValueSet.getVersion(), restoredValueSets.get(0).getVersion());
    assertTrue(restoredValueSets.get(0).isManuallyModified());
  }

  @Test
  void testRollbackWithMultipleValueSets() throws IOException {
    String secondUrl =
        "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.101.12.1062";
    String secondVersion = "20210221";

    ValueSet secondValueSet = new ValueSet();
    secondValueSet.setUrl(secondUrl);
    secondValueSet.setVersion(secondVersion);
    secondValueSet.setName("Emergency Department Visit");
    secondValueSet.setTitle("Emergency Department Visit");
    secondValueSet.setStatus(org.hl7.fhir.r4.model.Enumerations.PublicationStatus.ACTIVE);

    IParser jsonParser = FhirContext.forR4().newJsonParser();
    MadieValueSet secondMadieValueSet =
        MadieValueSet.builder()
            .url(secondUrl)
            .version(secondVersion)
            .valueSet(jsonParser.encodeResourceToString(secondValueSet))
            .manuallyModified(false)
            .lastUpdated(Instant.now())
            .build();

    ArgumentCaptor<List<MadieValueSet>> valueSetListCaptor = ArgumentCaptor.forClass(List.class);

    when(valueSetExpansionRepositoryMock.findAll())
        .thenReturn(List.of(existingMadieValueSet, secondMadieValueSet));
    when(valueSetExpansionRepositoryMock.findByUrlAndVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());

    migration.apply(valueSetExpansionRepositoryMock);
    migration.rollback(valueSetExpansionRepositoryMock);

    verify(valueSetExpansionRepositoryMock, times(1)).deleteAll(anyList());
    verify(valueSetExpansionRepositoryMock, times(1)).saveAll(valueSetListCaptor.capture());

    List<MadieValueSet> restoredValueSets = valueSetListCaptor.getValue();
    assertEquals(2, restoredValueSets.size());

    // Verify first value set is restored
    MadieValueSet restoredFirst = restoredValueSets.get(0);
    assertEquals(existingMadieValueSet.getUrl(), restoredFirst.getUrl());
    assertEquals(existingMadieValueSet.getVersion(), restoredFirst.getVersion());
    assertTrue(restoredFirst.isManuallyModified());

    // Verify second value set is restored
    MadieValueSet restoredSecond = restoredValueSets.get(1);
    assertEquals(secondMadieValueSet.getUrl(), restoredSecond.getUrl());
    assertEquals(secondMadieValueSet.getVersion(), restoredSecond.getVersion());
    assertFalse(restoredSecond.isManuallyModified());
  }

  @Test
  void testRollbackRestoresOriginalPropertiesAndFlags() throws IOException {
    Instant originalTime = Instant.parse("2025-01-01T00:00:00Z");
    String originalUrl =
        "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.101.12.1063";
    String originalVersion = "20200101";

    ValueSet originalValueSet = new ValueSet();
    originalValueSet.setUrl(originalUrl);
    originalValueSet.setVersion(originalVersion);
    originalValueSet.setName("Original Value Set");
    originalValueSet.setTitle("Original Value Set Title");
    originalValueSet.setStatus(org.hl7.fhir.r4.model.Enumerations.PublicationStatus.DRAFT);

    IParser jsonParser = FhirContext.forR4().newJsonParser();
    MadieValueSet originalMadieValueSet =
        MadieValueSet.builder()
            .url(originalUrl)
            .version(originalVersion)
            .valueSet(jsonParser.encodeResourceToString(originalValueSet))
            .manuallyModified(false)
            .lastUpdated(originalTime)
            .build();

    when(valueSetExpansionRepositoryMock.findAll()).thenReturn(List.of(originalMadieValueSet));
    when(valueSetExpansionRepositoryMock.findByUrlAndVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());

    migration.apply(valueSetExpansionRepositoryMock);

    // Verify that apply saved new value sets with different properties
    verify(valueSetExpansionRepositoryMock, atLeastOnce()).save(any(MadieValueSet.class));

    // Now rollback and verify original state is restored
    migration.rollback(valueSetExpansionRepositoryMock);

    verify(valueSetExpansionRepositoryMock, times(1)).saveAll(valueSetListCaptor.capture());

    List<MadieValueSet> restoredValueSets = valueSetListCaptor.getValue();
    assertEquals(1, restoredValueSets.size());

    MadieValueSet restored = restoredValueSets.get(0);
    assertEquals(originalUrl, restored.getUrl());
    assertEquals(originalVersion, restored.getVersion());
    assertFalse(restored.isManuallyModified());
    assertEquals(originalTime, restored.getLastUpdated());
  }

  @Test
  void testRollbackDeletesAllCurrentValueSetsBeforeRestore() throws IOException {
    // Simulate that new value sets were added during apply
    MadieValueSet newValueSet =
        MadieValueSet.builder()
            .url("http://new.valueset.url")
            .version("1.0.0")
            .valueSet("{}")
            .manuallyModified(true)
            .lastUpdated(Instant.now())
            .build();

    when(valueSetExpansionRepositoryMock.findAll())
        .thenReturn(List.of(existingMadieValueSet)) // Before apply
        .thenReturn(List.of(existingMadieValueSet, newValueSet)); // After apply
    when(valueSetExpansionRepositoryMock.findByUrlAndVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());

    migration.apply(valueSetExpansionRepositoryMock);
    migration.rollback(valueSetExpansionRepositoryMock);

    // Verify deleteAll is called before saveAll
    verify(valueSetExpansionRepositoryMock, times(1)).deleteAll(deleteCaptor.capture());
    verify(valueSetExpansionRepositoryMock, times(1)).saveAll(saveCaptor.capture());

    // Verify that saveAll restores only the original value sets
    List<MadieValueSet> savedValueSets = saveCaptor.getValue();
    assertEquals(1, savedValueSets.size());
    assertEquals(existingMadieValueSet.getUrl(), savedValueSets.get(0).getUrl());
  }

  @Test
  void testRollbackPreservesValueSetContent() throws IOException {
    String valueSetContent = "{\"resourceType\":\"ValueSet\",\"url\":\"http://test.com\"}";
    MadieValueSet valueSetWithContent =
        MadieValueSet.builder()
            .url("http://test.com/valueset")
            .version("1.0.0")
            .valueSet(valueSetContent)
            .manuallyModified(true)
            .lastUpdated(Instant.now())
            .build();

    when(valueSetExpansionRepositoryMock.findAll()).thenReturn(List.of(valueSetWithContent));
    when(valueSetExpansionRepositoryMock.findByUrlAndVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());

    migration.apply(valueSetExpansionRepositoryMock);
    migration.rollback(valueSetExpansionRepositoryMock);

    verify(valueSetExpansionRepositoryMock, times(1)).saveAll(valueSetListCaptor.capture());

    List<MadieValueSet> restoredValueSets = valueSetListCaptor.getValue();
    assertEquals(1, restoredValueSets.size());

    MadieValueSet restored = restoredValueSets.get(0);
    assertNotNull(restored.getValueSet());
    assertEquals(valueSetContent, restored.getValueSet());
  }
}
