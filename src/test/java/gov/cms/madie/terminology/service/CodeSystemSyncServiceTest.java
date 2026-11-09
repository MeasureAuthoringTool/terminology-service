package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeSystemSyncServiceTest {
  @Mock private FhirContext mockContext;
  @Mock private FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;
  @Mock private CodeSystemRepository codeSystemRepository;
  @Mock private CodeSystemService codeSystemService;
  @InjectMocks private CodeSystemSyncService codeSystemSyncService;

  private UmlsUser umlsUser;
  private static final String TEST_HARP_ID = "te$tHarpId";
  private static final String TEST_API_KEY = "te$tKey";

  private final Bundle mockCodeSystemResponseBundle = createMockCodeSystemsResource();
  private final String mockCodeSystemResponseJson =
      FhirContext.forR4().newJsonParser().encodeResourceToString(mockCodeSystemResponseBundle);

  @BeforeEach
  public void setUp() throws IOException {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
  }

  @Test
  void testRemoteRetrieveAllCodeSystems() {
    // Given
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();

    when(mockContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(mockCodeSystemResponseJson);
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(mockCodeSystemResponseJson);
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(codeSystemService.createCodeSystem(any(CodeSystem.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    List<CodeSystem> resultList = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    // Then
    assertEquals(2, resultList.size());
    assertTrue(
        resultList.stream()
            .anyMatch(
                cs ->
                    cs.getFullUrl()
                        .equals(mockCodeSystemResponseBundle.getEntryFirstRep().getFullUrl())));
    assertTrue(
        resultList.stream()
            .anyMatch(
                cs ->
                    cs.getFullUrl()
                        .equals(mockCodeSystemResponseBundle.getEntry().get(1).getFullUrl())));
    assertTrue(resultList.get(0).isLatestVersion());
    assertTrue(resultList.get(1).isLatestVersion());

    verify(codeSystemService, times(2)).createCodeSystem(any(CodeSystem.class));
  }

  @Test
  void testRemoteRetrieveAllCodeSystemsWithNewAndExisting() {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();

    var existingCodeSystem =
        CodeSystem.builder()
            .id("titleversion")
            .title("title")
            .name("name1")
            .fullUrl("http://test")
            .version(CodeSystem.Version.builder().fhirVersion("version").build())
            .versionId("vid")
            .oid("codeUrl")
            .lastUpdated(Instant.now())
            .lastUpdatedUpstream(new Date())
            .isLatestVersion(true)
            .isInVsac(false)
            .build();

    when(mockContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(mockCodeSystemResponseJson);
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(mockCodeSystemResponseJson);
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(codeSystemService.createCodeSystem(any(CodeSystem.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    List<CodeSystem> codeSystems = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    verify(codeSystemService, times(2)).createCodeSystem(any(CodeSystem.class));
    assertFalse(
        codeSystems.stream()
            .anyMatch(cs -> cs.getFullUrl().equals(existingCodeSystem.getFullUrl())));
    assertTrue(
        codeSystems.stream()
            .anyMatch(
                cs ->
                    cs.getFullUrl()
                        .equals(mockCodeSystemResponseBundle.getEntryFirstRep().getFullUrl())));
    assertTrue(
        codeSystems.stream()
            .anyMatch(
                cs ->
                    cs.getFullUrl()
                        .equals(mockCodeSystemResponseBundle.getEntry().get(1).getFullUrl())));
    assertTrue(codeSystems.stream().allMatch(CodeSystem::isLatestVersion));
  }

  @Test
  void testRemoteRetrieveAllCodeSystemsWithUpdatedCodeSystem() {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    org.hl7.fhir.r4.model.CodeSystem csResource =
        (org.hl7.fhir.r4.model.CodeSystem)
            mockCodeSystemResponseBundle.getEntryFirstRep().getResource();
    CodeSystem existingCodeSystem =
        CodeSystem.builder()
            .fullUrl(mockCodeSystemResponseBundle.getEntryFirstRep().getFullUrl())
            .name(csResource.getName())
            .oid("mockOid")
            .version(CodeSystem.Version.builder().fhirVersion(csResource.getVersion()).build())
            .lastUpdatedUpstream(
                Date.from(
                    csResource.getMeta().getLastUpdated().toInstant().minus(1, ChronoUnit.DAYS)))
            .isInVsac(true)
            .isLatestVersion(false)
            .build();

    when(mockContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(mockCodeSystemResponseJson);
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(mockCodeSystemResponseJson);
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.of(existingCodeSystem));

    List<CodeSystem> retrievedCodeSystems =
        codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    assertEquals(2, retrievedCodeSystems.size());
    Optional<CodeSystem> updatedCodeSystem =
        retrievedCodeSystems.stream()
            .filter(
                cs ->
                    cs.getFullUrl()
                        .equals(mockCodeSystemResponseBundle.getEntryFirstRep().getFullUrl()))
            .findFirst();
    assertTrue(updatedCodeSystem.isPresent());
    assertThat(updatedCodeSystem.get().getLastUpdatedUpstream())
        .isEqualTo(Date.from(csResource.getMeta().getLastUpdated().toInstant()));
    assertTrue(updatedCodeSystem.get().isLatestVersion());
  }

  @Test
  void testRemoteRetrieveAllCodeSystemsPromotesLatest() {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    org.hl7.fhir.r4.model.CodeSystem csResource =
        (org.hl7.fhir.r4.model.CodeSystem)
            mockCodeSystemResponseBundle.getEntryFirstRep().getResource();
    CodeSystem existingCodeSystem =
        CodeSystem.builder()
            .fullUrl(mockCodeSystemResponseBundle.getEntryFirstRep().getFullUrl())
            .name(csResource.getName())
            .oid("mockOid")
            .version(CodeSystem.Version.builder().fhirVersion(csResource.getVersion()).build())
            .lastUpdatedUpstream(
                Date.from(
                    csResource.getMeta().getLastUpdated().toInstant().minus(1, ChronoUnit.DAYS)))
            .isInVsac(true)
            .isLatestVersion(true)
            .build();

    when(mockContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(mockCodeSystemResponseJson);
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(mockCodeSystemResponseJson);
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.of(existingCodeSystem));

    List<CodeSystem> retrievedCodeSystems =
        codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    assertEquals(2, retrievedCodeSystems.size());
    Optional<CodeSystem> updatedCodeSystem =
        retrievedCodeSystems.stream()
            .filter(
                cs ->
                    cs.getFullUrl()
                        .equals(mockCodeSystemResponseBundle.getEntryFirstRep().getFullUrl()))
            .findFirst();
    assertTrue(updatedCodeSystem.isPresent());
    assertThat(updatedCodeSystem.get().getLastUpdatedUpstream())
        .isEqualTo(Date.from(csResource.getMeta().getLastUpdated().toInstant()));
    assertTrue(updatedCodeSystem.get().isLatestVersion());
  }

  @Test
  void testRemoteRetrieveAllCodeSystemsSkipsNullStringFhirVersion() {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    when(mockContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    // Bundle with a single entry whose version is the literal string "null"
    String nullVersionBundle =
        "{\"resourceType\":\"Bundle\",\"id\":\"cs-search\",\"meta\":{\"lastUpdated\":\"2024-03-28T15:04:59.375-04:00\"},"
            + "\"type\":\"searchset\",\"total\":1,\"link\":[{\"relation\":\"self\","
            + "\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=0&_count=50\"}],"
            + "\"entry\":[{\"fullUrl\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\","
            + "\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"ObservationInterpretation\","
            + "\"meta\":{\"versionId\":\"1710382394\",\"lastUpdated\":\"2019-04-25T00:00:00.000-04:00\"},"
            + "\"url\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\","
            + "\"identifier\":[{\"system\":\"urn:ietf:rfc:3986\",\"value\":\"urn:oid:2.16.840.1.113883.5.83\"}],"
            + "\"version\":\"null\",\"name\":\"ObservationInterpretation\","
            + "\"title\":\"ObservationInterpretation\",\"status\":\"active\"}}]}";

    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(nullVersionBundle);
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(nullVersionBundle);

    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    assertEquals(1, result.size());
    assertEquals(
        "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
        result.get(0).getFullUrl());
    assertEquals("null", result.get(0).getVersion().getFhirVersion());
    verify(codeSystemService, never()).createCodeSystem(any(CodeSystem.class));
    verify(codeSystemRepository, never())
        .findByFullUrlAndVersionFhirVersion(anyString(), anyString());
  }

  @Test
  void testRemoteRetrieveAllCodeSystemsSkipsBlankFhirVersion() {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    when(mockContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    // Bundle with a single entry whose version is missing.
    String blankVersionBundle =
        "{\"resourceType\":\"Bundle\",\"id\":\"cs-search\",\"meta\":{\"lastUpdated\":\"2024-03-28T15:04:59.375-04:00\"},"
            + "\"type\":\"searchset\",\"total\":1,\"link\":[{\"relation\":\"self\","
            + "\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=0&_count=50\"}],"
            + "\"entry\":[{\"fullUrl\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\","
            + "\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"ObservationInterpretation\","
            + "\"meta\":{\"versionId\":\"1710382394\",\"lastUpdated\":\"2019-04-25T00:00:00.000-04:00\"},"
            + "\"url\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\","
            + "\"identifier\":[{\"system\":\"urn:ietf:rfc:3986\",\"value\":\"urn:oid:2.16.840.1.113883.5.83\"}],"
            // missing version field.
            + " \"name\":\"ObservationInterpretation\","
            + "\"title\":\"ObservationInterpretation\",\"status\":\"active\"}}]}";

    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(blankVersionBundle);
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(blankVersionBundle);

    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    assertEquals(1, result.size());
    assertEquals(
        "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
        result.get(0).getFullUrl());
    assertNull(result.get(0).getVersion().getFhirVersion());
    verify(codeSystemService, never()).createCodeSystem(any(CodeSystem.class));
    verify(codeSystemRepository, never())
        .findByFullUrlAndVersionFhirVersion(anyString(), anyString());
  }

  @Test
  void testRemoteRetrieveAllCodeSystemsSkipsNullFullUrl() {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    when(mockContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    // Bundle with a single entry whose resource has no url (fullUrl will be null)
    String nullStringUrlBundle =
        "{\"resourceType\":\"Bundle\",\"id\":\"cs-search\",\"meta\":{\"lastUpdated\":\"2024-03-28T15:04:59.375-04:00\"},"
            + "\"type\":\"searchset\",\"total\":1,\"link\":[{\"relation\":\"self\","
            + "\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=0&_count=50\"}],"
            + "\"entry\":[{\"fullUrl\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\","
            + "\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"ObservationInterpretation\","
            + "\"meta\":{\"versionId\":\"1710382394\",\"lastUpdated\":\"2019-04-25T00:00:00.000-04:00\"},"
            + "\"url\":\"null\","
            + "\"identifier\":[{\"system\":\"urn:ietf:rfc:3986\",\"value\":\"urn:oid:2.16.840.1.113883.5.83\"}],"
            + "\"version\":\"2019-03-01\",\"name\":\"ObservationInterpretation\","
            + "\"title\":\"ObservationInterpretation\",\"status\":\"active\"}}]}";

    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(nullStringUrlBundle);
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(nullStringUrlBundle);

    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    assertEquals(1, result.size());
    assertEquals("null", result.get(0).getFullUrl());
    verify(codeSystemService, never()).createCodeSystem(any(CodeSystem.class));
    verify(codeSystemRepository, never())
        .findByFullUrlAndVersionFhirVersion(anyString(), anyString());
  }

  @Test
  void testRemoteRetrieveAllCodeSystemsSkipsBlankFullUrl() {
    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    when(mockContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    // Bundle with a single entry whose resource has no url (fullUrl will be null)
    String noUrlBundle =
        "{\"resourceType\":\"Bundle\",\"id\":\"cs-search\",\"meta\":{\"lastUpdated\":\"2024-03-28T15:04:59.375-04:00\"},"
            + "\"type\":\"searchset\",\"total\":1,\"link\":[{\"relation\":\"self\","
            + "\"url\":\"http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=0&_count=50\"}],"
            + "\"entry\":[{\"fullUrl\":\"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\","
            + "\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"ObservationInterpretation\","
            + "\"meta\":{\"versionId\":\"1710382394\",\"lastUpdated\":\"2019-04-25T00:00:00.000-04:00\"},"
            + "\"identifier\":[{\"system\":\"urn:ietf:rfc:3986\",\"value\":\"urn:oid:2.16.840.1.113883.5.83\"}],"
            + "\"version\":\"2019-03-01\",\"name\":\"ObservationInterpretation\","
            + "\"title\":\"ObservationInterpretation\",\"status\":\"active\"}}]}";

    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(noUrlBundle);
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(noUrlBundle);

    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    assertEquals(1, result.size());
    assertNull(result.get(0).getFullUrl());
    verify(codeSystemService, never()).createCodeSystem(any(CodeSystem.class));
    verify(codeSystemRepository, never())
        .findByFullUrlAndVersionFhirVersion(anyString(), anyString());
  }

  @Test
  void retrieveAllCodeSystemsPagesThroughSearchAndFetchesHistoryPerId() {
    when(mockContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    // build first bundle with one CodeSystem and a next link containing _offset and _count
    org.hl7.fhir.r4.model.CodeSystem cs1 = new org.hl7.fhir.r4.model.CodeSystem();
    cs1.setTitle("title1");
    cs1.setName("name1");
    cs1.setVersion("v1");
    cs1.setId("title1v1");
    cs1.setUrl("http://example.com/cs1");
    var id1 = new ArrayList<Identifier>();
    id1.add(new Identifier().setValue("codeUrl1"));
    Meta m1 = new Meta();
    m1.setVersionId("vid1");
    m1.setLastUpdated(new Date());
    cs1.setMeta(m1);
    cs1.setIdentifier(id1);

    Bundle bundle1 = new Bundle();
    bundle1.addEntry(new Bundle.BundleEntryComponent().setResource(cs1));
    bundle1.addLink(
        new Bundle.BundleLinkComponent()
            .setRelation("next")
            .setUrl("http://example.com/res/CodeSystem?_offset=50&_count=50"));

    // Second search page discovers another logical id.
    org.hl7.fhir.r4.model.CodeSystem cs2 = new org.hl7.fhir.r4.model.CodeSystem();
    cs2.setTitle("title2");
    cs2.setName("name2");
    cs2.setVersion("v2");
    cs2.setId("title2v2");
    cs2.setUrl("http://example.com/cs2");
    var id2 = new ArrayList<Identifier>();
    id2.add(new Identifier().setValue("codeUrl2"));
    Meta m2 = new Meta();
    m2.setVersionId("vid2");
    m2.setLastUpdated(new Date());
    cs2.setMeta(m2);
    cs2.setIdentifier(id2);

    Bundle bundle2 = new Bundle();
    bundle2.addEntry(new Bundle.BundleEntryComponent().setResource(cs2));

    IParser parser = FhirContext.forR4().newJsonParser();
    String json1 = parser.encodeResourceToString(bundle1);
    String json2 = parser.encodeResourceToString(bundle2);

    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(eq(0), eq(50), anyString()))
        .thenReturn(json1);
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(eq(50), eq(50), anyString()))
        .thenReturn(json2);
    Bundle history1 = new Bundle().setType(Bundle.BundleType.HISTORY);
    history1.addEntry().setResource(cs1);
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage("title1v1", 0, 50, TEST_API_KEY))
        .thenReturn(parser.encodeResourceToString(history1));
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage("title2v2", 0, 50, TEST_API_KEY))
        .thenReturn(json2);
    when(codeSystemService.createCodeSystem(any(CodeSystem.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    var result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    assertEquals(2, result.size());
    verify(fhirTerminologyServiceWebClient).getCodeSystemsPage(50, 50, TEST_API_KEY);
    verify(fhirTerminologyServiceWebClient)
        .getCodeSystemHistoryPage("title1v1", 0, 50, TEST_API_KEY);
    verify(fhirTerminologyServiceWebClient)
        .getCodeSystemHistoryPage("title2v2", 0, 50, TEST_API_KEY);
  }

  @Test
  void retrieveAllCodeSystemsHandlesDataAccessException() {
    UmlsUser umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    // Mock repository to throw DataAccessException when save is called
    doThrow(new DataAccessException("Simulated DB error") {})
        .when(codeSystemService)
        .createCodeSystem(any());
    when(mockContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(
            "{\"resourceType\":\"Bundle\",\"entry\":[{\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"cs1\",\"url\":\"http://lonic.org\",\"name\":\"LOINC\",\"version\":\"2.40\",\"title\":\"LOINC\",\"identifier\":[{\"value\":\"urn:oid:2.16.840.1.113883.6.1\"}]}}]}");
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            anyString(), anyInt(), anyInt(), anyString()))
        .thenReturn(
            "{\"resourceType\":\"Bundle\",\"entry\":[{\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"cs1\",\"url\":\"http://lonic.org\",\"name\":\"LOINC\",\"version\":\"2.40\",\"title\":\"LOINC\",\"identifier\":[{\"value\":\"urn:oid:2.16.840.1.113883.6.1\"}]}}]}");
    // Call the method under test
    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);
    // Assert that the result is not empty and the error branch was triggered
    assertFalse(result.isEmpty());
    assertEquals("LOINC", result.get(0).getName());
  }

  @Test
  void retrieveAllCodeSystemsOffsetNullFromNextLinkThrows() {
    when(mockContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    // Build a bundle with a next link containing only _count
    org.hl7.fhir.r4.model.CodeSystem cs1 = new org.hl7.fhir.r4.model.CodeSystem();
    cs1.setTitle("title1");
    cs1.setName("name1");
    cs1.setVersion("v1");
    cs1.setId("title1v1");
    cs1.setUrl("http://example.com/cs1");
    var id1 = new ArrayList<Identifier>();
    id1.add(new Identifier().setValue("codeUrl1"));
    Meta m1 = new Meta();
    m1.setVersionId("vid1");
    m1.setLastUpdated(new Date());
    cs1.setMeta(m1);
    cs1.setIdentifier(id1);

    Bundle bundle1 = new Bundle();
    bundle1.addEntry(new Bundle.BundleEntryComponent().setResource(cs1));
    bundle1.addLink(
        new Bundle.BundleLinkComponent()
            .setRelation("next")
            .setUrl("http://example.com/res/CodeSystem?_count=50"));

    // Second bundle (recursive result) with another CodeSystem
    org.hl7.fhir.r4.model.CodeSystem cs2 = new org.hl7.fhir.r4.model.CodeSystem();
    cs2.setTitle("title2");
    cs2.setName("name2");
    cs2.setVersion("v2");
    cs2.setId("title2v2");
    cs2.setUrl("http://example.com/cs2");
    var id2 = new ArrayList<Identifier>();
    id2.add(new Identifier().setValue("codeUrl2"));
    Meta m2 = new Meta();
    m2.setVersionId("vid2");
    m2.setLastUpdated(new Date());
    cs2.setMeta(m2);
    cs2.setIdentifier(id2);

    Bundle bundle2 = new Bundle();
    bundle2.addEntry(new Bundle.BundleEntryComponent().setResource(cs2));

    IParser parser = FhirContext.forR4().newJsonParser();
    String json1 = parser.encodeResourceToString(bundle1);

    // initial page invoked by retrieveAllCodeSystems -> return page for offset=0,count=50
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(eq(0), eq(50), anyString()))
        .thenReturn(json1);

    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();

    assertThrows(
        IllegalStateException.class,
        () -> codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser));
  }

  @Test
  void testRemoteRetrieveAllCodeSystemsCountNullFromNextLinkThrows() {
    when(mockContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());

    // Build a bundle with a next link containing only _offset
    org.hl7.fhir.r4.model.CodeSystem cs1 = new org.hl7.fhir.r4.model.CodeSystem();
    cs1.setTitle("title1");
    cs1.setName("name1");
    cs1.setVersion("v1");
    cs1.setId("title1v1");
    cs1.setUrl("http://example.com/cs1");
    var id1 = new ArrayList<Identifier>();
    id1.add(new Identifier().setValue("codeUrl1"));
    Meta m1 = new Meta();
    m1.setVersionId("vid1");
    m1.setLastUpdated(new Date());
    cs1.setMeta(m1);
    cs1.setIdentifier(id1);

    Bundle bundle1 = new Bundle();
    bundle1.addEntry(new Bundle.BundleEntryComponent().setResource(cs1));
    bundle1.addLink(
        new Bundle.BundleLinkComponent()
            .setRelation("next")
            .setUrl("http://example.com/res/CodeSystem?_offset=50"));

    // Second bundle (recursive result) with another CodeSystem
    org.hl7.fhir.r4.model.CodeSystem cs2 = new org.hl7.fhir.r4.model.CodeSystem();
    cs2.setTitle("title2");
    cs2.setName("name2");
    cs2.setVersion("v2");
    cs2.setId("title2v2");
    cs2.setUrl("http://example.com/cs2");
    var id2 = new ArrayList<Identifier>();
    id2.add(new Identifier().setValue("codeUrl2"));
    Meta m2 = new Meta();
    m2.setVersionId("vid2");
    m2.setLastUpdated(new Date());
    cs2.setMeta(m2);
    cs2.setIdentifier(id2);

    Bundle bundle2 = new Bundle();
    bundle2.addEntry(new Bundle.BundleEntryComponent().setResource(cs2));

    IParser parser = FhirContext.forR4().newJsonParser();
    String json1 = parser.encodeResourceToString(bundle1);

    // initial page invoked by retrieveAllCodeSystems -> return page for offset=0,count=50
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(eq(0), eq(50), anyString()))
        .thenReturn(json1);

    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();

    assertThrows(
        IllegalStateException.class,
        () -> codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser));
  }

  private Bundle createMockCodeSystemsResource() {
    Bundle bundle = new Bundle();
    bundle.setId("codesystem-search");
    bundle.setMeta(new Meta().setLastUpdated(Date.from(Instant.parse("2024-03-28T19:04:59.375Z"))));
    bundle.setType(Bundle.BundleType.SEARCHSET);
    bundle.setTotal(831);
    bundle
        .addLink()
        .setRelation("self")
        .setUrl("http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=500&_count=2");
    bundle
        .addLink()
        .setRelation("first")
        .setUrl("http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=0&_count=2");
    bundle
        .addLink()
        .setRelation("previous")
        .setUrl("http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=498&_count=2");
    bundle
        .addLink()
        .setRelation("last")
        .setUrl("http://uat-cts.nlm.nih.gov/fhir/res/CodeSystem?_offset=829&_count=2");
    bundle
        .addEntry()
        .setFullUrl("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation")
        .setResource(
            mockFhirCodeSystem(
                "ObservationInterpretation",
                "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                "urn:oid:2.16.840.1.113883.5.83",
                "1710382394",
                "2019-04-25T04:00:00Z",
                "2019-03-01"));
    bundle
        .addEntry()
        .setFullUrl("http://terminology.hl7.org/CodeSystem/v3-ActCode")
        .setResource(
            mockFhirCodeSystem(
                "v3-ActCode",
                "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                "urn:oid:2.16.840.1.113883.5.4",
                "11",
                "2024-01-15T00:00:00-05:00",
                "9.0.0"));
    return bundle;
  }

  private org.hl7.fhir.r4.model.CodeSystem mockFhirCodeSystem(
      String id, String url, String oid, String versionId, String lastUpdated, String version) {
    org.hl7.fhir.r4.model.CodeSystem codeSystem = new org.hl7.fhir.r4.model.CodeSystem();
    codeSystem.setId(id);
    codeSystem.setUrl(url);
    codeSystem.setVersion(version);
    codeSystem.setName(id);
    codeSystem.setTitle(id);
    codeSystem.setStatus(org.hl7.fhir.r4.model.Enumerations.PublicationStatus.ACTIVE);
    codeSystem.setContent(org.hl7.fhir.r4.model.CodeSystem.CodeSystemContentMode.COMPLETE);
    codeSystem.setCount(57);
    codeSystem.setMeta(
        new Meta().setVersionId(versionId).setLastUpdated(Date.from(Instant.parse(lastUpdated))));
    codeSystem.addIdentifier(new Identifier().setSystem("urn:ietf:rfc:3986").setValue(oid));
    return codeSystem;
  }

  @Test
  void retrieveAllCodeSystemsUsesLogicalIdAndSkipsDeletedHistoryEntries() {
    when(mockContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    String currentBundle =
        """
        {
          "resourceType": "Bundle",
          "type": "searchset",
          "entry": [{
            "resource": {
              "resourceType": "CodeSystem",
              "id": "logical-id",
              "url": "http://example.com/system",
              "identifier": [{"value": "urn:oid:1.2.3"}],
              "version": "2",
              "name": "DifferentFriendlyName",
              "title": "Example"
            }
          }]
        }
        """;
    String historyBundle =
        """
        {
          "resourceType": "Bundle",
          "type": "history",
          "entry": [
            {
              "resource": {
                "resourceType": "CodeSystem",
                "id": "logical-id",
                "url": "http://example.com/system",
                "identifier": [{"value": "urn:oid:1.2.3"}],
                "version": "2",
                "name": "DifferentFriendlyName",
                "title": "Example"
              },
              "request": {"method": "PUT", "url": "CodeSystem/logical-id"}
            },
            {
              "resource": {
                "resourceType": "CodeSystem",
                "id": "logical-id",
                "url": "http://example.com/system",
                "identifier": [{"value": "urn:oid:1.2.3"}],
                "version": "1",
                "name": "DifferentFriendlyName",
                "title": "Example"
              },
              "request": {"method": "PUT", "url": "CodeSystem/logical-id"}
            },
            {
              "resource": {
                "resourceType": "CodeSystem",
                "id": "logical-id",
                "url": "http://example.com/system",
                "identifier": [{"value": "urn:oid:1.2.3"}],
                "version": "deleted",
                "name": "DifferentFriendlyName",
                "title": "Example"
              },
              "request": {"method": "DELETE", "url": "CodeSystem/logical-id"}
            },
            {
              "request": {"method": "DELETE", "url": "CodeSystem/logical-id"}
            }
          ]
        }
        """;
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(0, 50, TEST_API_KEY))
        .thenReturn(currentBundle);
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            "logical-id", 0, 50, TEST_API_KEY))
        .thenReturn(historyBundle);
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());

    List<gov.cms.madie.terminology.models.CodeSystem> result =
        codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    assertEquals(
        List.of("2", "1"), result.stream().map(cs -> cs.getVersion().getFhirVersion()).toList());
    verify(fhirTerminologyServiceWebClient)
        .getCodeSystemHistoryPage("logical-id", 0, 50, TEST_API_KEY);
    verify(fhirTerminologyServiceWebClient, never())
        .getCodeSystemHistoryPage(eq("DifferentFriendlyName"), anyInt(), anyInt(), anyString());
    verify(codeSystemService, times(2))
        .createCodeSystem(any(gov.cms.madie.terminology.models.CodeSystem.class));
  }

  @Test
  void retrieveAllCodeSystemsPagesThroughCodeSystemHistory() {
    when(mockContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    IParser parser = FhirContext.forR4().newJsonParser();
    org.hl7.fhir.r4.model.CodeSystem current = buildFhirCodeSystem("logical-id", "3");
    org.hl7.fhir.r4.model.CodeSystem historicalVersion2 = buildFhirCodeSystem("logical-id", "2");
    org.hl7.fhir.r4.model.CodeSystem historicalVersion1 = buildFhirCodeSystem("logical-id", "1");

    Bundle currentBundle = new Bundle().setType(Bundle.BundleType.SEARCHSET);
    currentBundle.addEntry().setResource(current);
    Bundle firstHistoryPage = new Bundle().setType(Bundle.BundleType.HISTORY);
    firstHistoryPage.addEntry().setResource(current);
    firstHistoryPage.addEntry().setResource(historicalVersion2);
    firstHistoryPage.addLink(
        new Bundle.BundleLinkComponent()
            .setRelation("next")
            .setUrl(
                "https://cts.nlm.nih.gov/fhir/CodeSystem/logical-id/_history?_offset=50&_count=50"));
    Bundle secondHistoryPage = new Bundle().setType(Bundle.BundleType.HISTORY);
    secondHistoryPage.addEntry().setResource(historicalVersion1);

    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(0, 50, TEST_API_KEY))
        .thenReturn(parser.encodeResourceToString(currentBundle));
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            "logical-id", 0, 50, TEST_API_KEY))
        .thenReturn(parser.encodeResourceToString(firstHistoryPage));
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            "logical-id", 50, 50, TEST_API_KEY))
        .thenReturn(parser.encodeResourceToString(secondHistoryPage));
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());

    List<gov.cms.madie.terminology.models.CodeSystem> result =
        codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    assertEquals(
        List.of("3", "2", "1"),
        result.stream().map(cs -> cs.getVersion().getFhirVersion()).toList());
    verify(fhirTerminologyServiceWebClient)
        .getCodeSystemHistoryPage("logical-id", 50, 50, TEST_API_KEY);
  }

  @Test
  void retrieveAllCodeSystemsDeduplicatesIdsAndSkipsInvalidSearchEntries() {
    IParser parser = FhirContext.forR4().newJsonParser();
    when(mockContext.newJsonParser()).thenReturn(parser);
    Bundle search = new Bundle().setType(Bundle.BundleType.SEARCHSET);
    search.addEntry().setResource(buildFhirCodeSystem("logical-id", "2"));
    search.addEntry().setResource(buildFhirCodeSystem("logical-id", "2"));
    search.addEntry().setResource(buildFhirCodeSystem(null, "1"));
    search.addEntry();
    search.addEntry().setResource(new org.hl7.fhir.r4.model.OperationOutcome());
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(0, 50, TEST_API_KEY))
        .thenReturn(parser.encodeResourceToString(search));
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            "logical-id", 0, 50, TEST_API_KEY))
        .thenReturn("{\"resourceType\":\"Bundle\",\"type\":\"history\"}");

    assertTrue(codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser).isEmpty());

    verify(fhirTerminologyServiceWebClient)
        .getCodeSystemHistoryPage("logical-id", 0, 50, TEST_API_KEY);
    verifyNoInteractions(codeSystemRepository, codeSystemService);
  }

  @Test
  void retrieveAllCodeSystemsKeepsNewestRevisionOfEachCanonicalVersion() {
    var newestRevision = buildFhirCodeSystem("logical-id", "2");
    newestRevision.setMeta(
        new Meta()
            .setVersionId("new")
            .setLastUpdated(Date.from(Instant.parse("2024-01-01T00:00:00Z"))));
    var olderRevision = buildFhirCodeSystem("logical-id", "2");
    olderRevision.setMeta(
        new Meta()
            .setVersionId("old")
            .setLastUpdated(Date.from(Instant.parse("2020-01-01T00:00:00Z"))));
    var undatedRevision = buildFhirCodeSystem("logical-id", "2");
    var version1 = buildFhirCodeSystem("logical-id", "1");
    version1.setMeta(new Meta().setLastUpdated(Date.from(Instant.parse("2019-01-01T00:00:00Z"))));
    stubSearchAndHistory(undatedRevision, newestRevision, version1, olderRevision);

    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    assertEquals(
        List.of("2", "1"), result.stream().map(cs -> cs.getVersion().getFhirVersion()).toList());
    assertEquals("new", result.get(0).getVersionId());
    assertTrue(result.get(0).isLatestVersion());
    assertFalse(result.get(1).isLatestVersion());
    assertTrue(result.stream().allMatch(CodeSystem::isInVsac));
    verify(codeSystemService, times(2)).createCodeSystem(any(CodeSystem.class));
    verify(codeSystemRepository)
        .findByFullUrlAndVersionFhirVersion("http://example.com/system", "2");
    verify(codeSystemRepository)
        .findByFullUrlAndVersionFhirVersion("http://example.com/system", "1");
    verifyNoMoreInteractions(codeSystemRepository);
  }

  @Test
  void retrieveAllCodeSystemsDoesNotLetInvalidVersionSuppressLatest() {
    var valid = buildFhirCodeSystem("logical-id", "2");
    valid.setMeta(new Meta().setLastUpdated(Date.from(Instant.parse("2020-01-01T00:00:00Z"))));
    var invalid = buildFhirCodeSystem("logical-id", "null");
    invalid.setMeta(new Meta().setLastUpdated(Date.from(Instant.parse("2024-01-01T00:00:00Z"))));
    stubSearchAndHistory(valid, invalid);

    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    assertTrue(result.get(0).isLatestVersion());
    assertFalse(result.get(1).isLatestVersion());
    verify(codeSystemService).createCodeSystem(result.get(0));
    verifyNoMoreInteractions(codeSystemService);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void refreshPersistsHistoryPreservesMappingsAndDemotesPreviousLatest(boolean latestExists) {
    String url = "http://example.com/system";
    Map<String, CodeSystem> stored = new LinkedHashMap<>();
    CodeSystem previousLatest =
        CodeSystem.builder()
            .id("previous-latest")
            .fullUrl(url)
            .name("Example")
            .oid("urn:oid:1.2.3")
            .version(CodeSystem.Version.builder().fhirVersion("0").build())
            .isLatestVersion(true)
            .build();
    CodeSystem existingVersion =
        previousLatest.toBuilder()
            .id("version-1")
            .isLatestVersion(false)
            .version(CodeSystem.Version.builder().fhirVersion("1").vsacVersion("mapped-1").build())
            .build();
    stored.put("0", previousLatest);
    stored.put("1", existingVersion);
    if (latestExists) {
      stored.put(
          "2",
          existingVersion.toBuilder()
              .id("version-2")
              .version(
                  CodeSystem.Version.builder().fhirVersion("2").vsacVersion("mapped-2").build())
              .build());
    }
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(eq(url), anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(1))));
    when(codeSystemRepository.findAllByFullUrl(url))
        .thenAnswer(invocation -> new ArrayList<>(stored.values()));
    when(codeSystemRepository.findById(anyString()))
        .thenAnswer(
            invocation ->
                stored.values().stream()
                    .filter(cs -> cs.getId().equals(invocation.getArgument(0)))
                    .findFirst());
    when(codeSystemRepository.save(any(CodeSystem.class)))
        .thenAnswer(
            invocation -> {
              CodeSystem saved = invocation.getArgument(0);
              if (saved.getId() == null) {
                saved.setId("version-" + saved.getVersion().getFhirVersion());
              }
              stored.put(saved.getVersion().getFhirVersion(), saved);
              return saved;
            });
    var version2 = buildFhirCodeSystem("logical-id", "2");
    version2.setMeta(
        new Meta()
            .setVersionId("revision-2")
            .setLastUpdated(Date.from(Instant.parse("2024-01-01T00:00:00Z"))));
    var version1 = buildFhirCodeSystem("logical-id", "1");
    version1.setMeta(
        new Meta()
            .setVersionId("revision-1")
            .setLastUpdated(Date.from(Instant.parse("2020-01-01T00:00:00Z"))));
    stubSearchAndHistory(version2, version1);
    CodeSystemSyncService syncWithPersistence =
        new CodeSystemSyncService(
            mockContext,
            fhirTerminologyServiceWebClient,
            new CodeSystemService(codeSystemRepository),
            codeSystemRepository);

    List<CodeSystem> result = syncWithPersistence.remoteRetrieveAllCodeSystems(umlsUser);

    assertEquals(2, result.size());
    assertEquals(3, stored.size());
    assertFalse(previousLatest.isLatestVersion());
    assertEquals("mapped-1", stored.get("1").getVersion().getVsacVersion());
    assertEquals("revision-1", stored.get("1").getVersionId());
    assertFalse(stored.get("1").isLatestVersion());
    assertTrue(stored.get("1").isInVsac());
    assertTrue(stored.get("2").isInVsac());
    assertTrue(stored.get("2").isLatestVersion());
    assertEquals("revision-2", stored.get("2").getVersionId());
    assertEquals(1, stored.values().stream().filter(CodeSystem::isLatestVersion).count());
    if (latestExists) {
      assertEquals("mapped-2", stored.get("2").getVersion().getVsacVersion());
    }
    verify(codeSystemRepository, never()).findAllByOid(anyString());
  }

  @Test
  void retrieveAllCodeSystemsIteratesThroughThousandsOfSearchAndHistoryPages() {
    int pages = 2000;
    IParser parser = mock(IParser.class);
    when(mockContext.newJsonParser()).thenReturn(parser);
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), eq(TEST_API_KEY)))
        .thenAnswer(invocation -> "search:" + invocation.getArgument(0));
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            eq("logical-id"), anyInt(), anyInt(), eq(TEST_API_KEY)))
        .thenAnswer(invocation -> "history:" + invocation.getArgument(1));
    when(parser.parseResource(eq(Bundle.class), anyString()))
        .thenAnswer(
            invocation -> {
              String response = invocation.getArgument(1);
              int offset = Integer.parseInt(response.substring(response.indexOf(':') + 1));
              Bundle page = new Bundle();
              page.addEntry().setResource(buildFhirCodeSystem("logical-id", "1"));
              if (offset + 1 < pages) {
                page.addLink()
                    .setRelation("next")
                    .setUrl("http://example.com/CodeSystem?_offset=" + (offset + 1) + "&_count=1");
              }
              return page;
            });

    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    assertEquals(1, result.size());
    verify(fhirTerminologyServiceWebClient, times(pages))
        .getCodeSystemsPage(anyInt(), anyInt(), eq(TEST_API_KEY));
    verify(fhirTerminologyServiceWebClient, times(pages))
        .getCodeSystemHistoryPage(eq("logical-id"), anyInt(), anyInt(), eq(TEST_API_KEY));
    verify(fhirTerminologyServiceWebClient).getCodeSystemsPage(pages - 1, 1, TEST_API_KEY);
    verify(fhirTerminologyServiceWebClient)
        .getCodeSystemHistoryPage("logical-id", pages - 1, 1, TEST_API_KEY);
    verify(codeSystemService).createCodeSystem(any(CodeSystem.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"_count=50", "_offset=50"})
  void retrieveAllCodeSystemsRejectsIncompleteHistoryNextLink(String query) {
    IParser parser = FhirContext.forR4().newJsonParser();
    when(mockContext.newJsonParser()).thenReturn(parser);
    Bundle search = new Bundle();
    search.addEntry().setResource(buildFhirCodeSystem("logical-id", "2"));
    Bundle history = new Bundle();
    history.addEntry().setResource(buildFhirCodeSystem("logical-id", "1"));
    history.addLink().setRelation("next").setUrl("http://example.com/_history?" + query);
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(0, 50, TEST_API_KEY))
        .thenReturn(parser.encodeResourceToString(search));
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            "logical-id", 0, 50, TEST_API_KEY))
        .thenReturn(parser.encodeResourceToString(history));

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser));

    assertTrue(error.getMessage().contains("missing _offset or _count"));
    verifyNoInteractions(codeSystemRepository, codeSystemService);
  }

  @Test
  void retrieveAllCodeSystemsDoesNotPersistPartialHistoryWhenRemoteRequestFails() {
    IParser parser = FhirContext.forR4().newJsonParser();
    when(mockContext.newJsonParser()).thenReturn(parser);
    Bundle search = new Bundle();
    search.addEntry().setResource(buildFhirCodeSystem("logical-id", "2"));
    Bundle history = new Bundle();
    history.addEntry().setResource(buildFhirCodeSystem("logical-id", "2"));
    history
        .addLink()
        .setRelation("next")
        .setUrl("http://example.com/_history?_offset=50&_count=50");
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(0, 50, TEST_API_KEY))
        .thenReturn(parser.encodeResourceToString(search));
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            "logical-id", 0, 50, TEST_API_KEY))
        .thenReturn(parser.encodeResourceToString(history));
    IllegalStateException failure = new IllegalStateException("VSAC unavailable");
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            "logical-id", 50, 50, TEST_API_KEY))
        .thenThrow(failure);

    assertSame(
        failure,
        assertThrows(
            IllegalStateException.class,
            () -> codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser)));
    verifyNoInteractions(codeSystemRepository, codeSystemService);
  }

  private void stubSearchAndHistory(org.hl7.fhir.r4.model.CodeSystem... revisions) {
    IParser parser = FhirContext.forR4().newJsonParser();
    when(mockContext.newJsonParser()).thenReturn(parser);
    Bundle search = new Bundle().setType(Bundle.BundleType.SEARCHSET);
    search.addEntry().setResource(revisions[0]);
    Bundle history = new Bundle().setType(Bundle.BundleType.HISTORY);
    for (var revision : revisions) {
      history.addEntry().setResource(revision);
    }
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(0, 50, TEST_API_KEY))
        .thenReturn(parser.encodeResourceToString(search));
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            revisions[0].getIdElement().getIdPart(), 0, 50, TEST_API_KEY))
        .thenReturn(parser.encodeResourceToString(history));
  }

  @Test
  void parseOidFromIdentifierReturnsCustomOidForHCPCS() throws Exception {
    Identifier id = new Identifier();
    id.setValue("urn:oid:2.16.840.1.113883.6.14,2.16.840.1.113883.6.285");
    Method method =
        CodeSystemSyncService.class.getDeclaredMethod("parseOidFromIdentifier", List.class);
    method.setAccessible(true);
    String result = (String) method.invoke(codeSystemSyncService, List.of(id));
    assertEquals("urn:oid:2.16.840.1.113883.6.285", result);
  }

  @Test
  void parseOidFromIdentifierReturnsEmptyStringWhenValueEmpty() throws Exception {
    Identifier id = new Identifier();
    id.setValue("");
    Method method =
        CodeSystemSyncService.class.getDeclaredMethod("parseOidFromIdentifier", List.class);
    method.setAccessible(true);
    String result = (String) method.invoke(codeSystemSyncService, List.of(id));
    assertEquals("", result);
  }

  private org.hl7.fhir.r4.model.CodeSystem buildFhirCodeSystem(String id, String version) {
    org.hl7.fhir.r4.model.CodeSystem codeSystem = new org.hl7.fhir.r4.model.CodeSystem();
    codeSystem.setId(id);
    codeSystem.setUrl("http://example.com/system");
    codeSystem.setIdentifier(List.of(new Identifier().setValue("urn:oid:1.2.3")));
    codeSystem.setVersion(version);
    codeSystem.setName("DifferentFriendlyName");
    codeSystem.setTitle("Example");
    return codeSystem;
  }
}
