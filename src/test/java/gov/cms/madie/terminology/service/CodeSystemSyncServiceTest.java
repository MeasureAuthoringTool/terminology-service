package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import java.io.IOException;
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
    // given
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

    // when
    var result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    // then
    assertEquals(2, result.size());
    assertTrue(result.stream().allMatch(CodeSystem::isLatestVersion));
    verify(fhirTerminologyServiceWebClient).getCodeSystemsPage(50, 50, TEST_API_KEY);
    verify(fhirTerminologyServiceWebClient)
        .getCodeSystemHistoryPage("title1v1", 0, 50, TEST_API_KEY);
    verify(fhirTerminologyServiceWebClient)
        .getCodeSystemHistoryPage("title2v2", 0, 50, TEST_API_KEY);
  }

  @Test
  void retrieveAllCodeSystemsHandlesDataAccessException() {
    // given
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
    // when
    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    // then
    assertFalse(result.isEmpty());
    assertEquals("LOINC", result.get(0).getName());
  }

  @Test
  void retrieveAllCodeSystemsOffsetNullFromNextLinkThrows() {
    // given
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

    // when
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser));

    // then
    assertTrue(error.getMessage().contains("missing _offset or _count"));
    verifyNoInteractions(codeSystemRepository, codeSystemService);
  }

  @Test
  void testRemoteRetrieveAllCodeSystemsCountNullFromNextLinkThrows() {
    // given
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

    // when
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser));

    // then
    assertTrue(error.getMessage().contains("missing _offset or _count"));
    verifyNoInteractions(codeSystemRepository, codeSystemService);
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
    // given
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

    // when
    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    // then
    assertEquals(
        List.of("2", "1"), result.stream().map(cs -> cs.getVersion().getFhirVersion()).toList());
    verify(fhirTerminologyServiceWebClient)
        .getCodeSystemHistoryPage("logical-id", 0, 50, TEST_API_KEY);
    verify(fhirTerminologyServiceWebClient, never())
        .getCodeSystemHistoryPage(eq("DifferentFriendlyName"), anyInt(), anyInt(), anyString());
    verify(codeSystemService, times(2)).createCodeSystem(any(CodeSystem.class));
  }

  @Test
  void retrieveAllCodeSystemsPagesThroughCodeSystemHistory() {
    // given
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

    // when
    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    // then
    assertEquals(
        List.of("3", "2", "1"),
        result.stream().map(cs -> cs.getVersion().getFhirVersion()).toList());
    assertTrue(result.get(0).isLatestVersion());
    assertFalse(result.get(1).isLatestVersion());
    assertFalse(result.get(2).isLatestVersion());
    verify(fhirTerminologyServiceWebClient)
        .getCodeSystemHistoryPage("logical-id", 50, 50, TEST_API_KEY);
  }

  @Test
  void retrieveAllCodeSystemsDeduplicatesIdsAndSkipsInvalidSearchEntries() {
    // given
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

    // when
    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    // then
    assertEquals(1, result.size());
    assertEquals("2", result.get(0).getVersion().getFhirVersion());
    assertTrue(result.get(0).isLatestVersion());
    verify(fhirTerminologyServiceWebClient)
        .getCodeSystemHistoryPage("logical-id", 0, 50, TEST_API_KEY);
    verify(codeSystemRepository)
        .findByFullUrlAndVersionFhirVersion("http://example.com/system", "2");
    verify(codeSystemService).createCodeSystem(result.get(0));
  }

  @Test
  void retrieveAllCodeSystemsDeduplicatesBusinessVersionsWithoutComparingRevisions() {
    // given
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
    var version1Correction = version1.copy();
    version1.setTitle("First historical entry");
    version1Correction.setTitle("Later historical entry");
    version1Correction.setMeta(
        new Meta().setLastUpdated(Date.from(Instant.parse("2025-01-01T00:00:00Z"))));
    stubSearchAndHistory(
        undatedRevision, newestRevision, version1, olderRevision, version1Correction);

    // when
    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    // then
    assertEquals(
        List.of("2", "1"), result.stream().map(cs -> cs.getVersion().getFhirVersion()).toList());
    assertNull(result.get(0).getVersionId());
    assertNull(result.get(0).getLastUpdatedUpstream());
    assertEquals("First historical entry", result.get(1).getTitle());
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
  void retrieveAllCodeSystemsMarksMedrtCurrentBusinessVersionLatestWithoutRevisionMetadata() {
    // given
    var current = buildFhirCodeSystem("MEDRT", "2026-09");
    current.setUrl("http://va.gov/terminology/medrt");
    current.setName("MEDRT");
    current.setTitle("Medication Reference Terminology");
    current.setIdentifier(List.of(new Identifier().setValue("urn:oid:2.16.840.1.113883.6.345")));
    current.setDateElement(new org.hl7.fhir.r4.model.DateTimeType("2026-10-01T00:00:00-04:00"));
    var historical = current.copy();
    historical.setVersion("2026-02");
    historical.setMeta(
        new Meta()
            .setVersionId("18")
            .setLastUpdated(Date.from(Instant.parse("2026-05-07T05:00:00Z"))));
    stubSearchAndHistory(current, historical);

    // when
    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    // then
    assertEquals(2, result.size());
    CodeSystem latest = result.get(0);
    assertEquals("2026-09", latest.getVersion().getFhirVersion());
    assertTrue(latest.isLatestVersion());
    assertNull(latest.getVersionId());
    assertNull(latest.getLastUpdatedUpstream());
    assertEquals("http://va.gov/terminology/medrt", latest.getFullUrl());
    assertEquals("MEDRT", latest.getName());
    assertEquals("Medication Reference Terminology", latest.getTitle());
    assertEquals("urn:oid:2.16.840.1.113883.6.345", latest.getOid());
    assertTrue(latest.isInVsac());
    assertNotNull(latest.getLastUpdated());
    assertEquals("2026-02", result.get(1).getVersion().getFhirVersion());
    assertFalse(result.get(1).isLatestVersion());
    assertEquals("18", result.get(1).getVersionId());
    assertEquals(historical.getMeta().getLastUpdated(), result.get(1).getLastUpdatedUpstream());
    verify(codeSystemService).createCodeSystem(latest);
    verify(codeSystemService).createCodeSystem(result.get(1));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"2.83", "2026-09", "http://snomed.info/sct/731000124108/version/20260901"})
  void retrieveAllCodeSystemsUsesSearchVersionRegardlessOfHistoryOrderOrTimestamps(String version) {
    // given
    var current = buildFhirCodeSystem("logical-id", version);
    current.setMeta(
        new Meta()
            .setVersionId("search-revision")
            .setLastUpdated(Date.from(Instant.parse("2020-01-01T00:00:00Z"))));
    var differentVersion = buildFhirCodeSystem("logical-id", "other-business-version");
    differentVersion.setMeta(
        new Meta()
            .setVersionId("history-revision")
            .setLastUpdated(Date.from(Instant.parse("2026-01-01T00:00:00Z"))));
    IParser parser = FhirContext.forR4().newJsonParser();
    when(mockContext.newJsonParser()).thenReturn(parser);
    Bundle search = new Bundle();
    search.addEntry().setResource(current);
    Bundle history = new Bundle();
    history.addEntry().setResource(differentVersion);
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(0, 50, TEST_API_KEY))
        .thenReturn(parser.encodeResourceToString(search));
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            "logical-id", 0, 50, TEST_API_KEY))
        .thenReturn(parser.encodeResourceToString(history));

    // when
    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    // then
    assertEquals(2, result.size());
    assertEquals(version, result.get(0).getVersion().getFhirVersion());
    assertTrue(result.get(0).isLatestVersion());
    assertEquals("search-revision", result.get(0).getVersionId());
    assertEquals(current.getMeta().getLastUpdated(), result.get(0).getLastUpdatedUpstream());
    assertFalse(result.get(1).isLatestVersion());
    assertEquals("history-revision", result.get(1).getVersionId());
    verify(codeSystemService, times(2)).createCodeSystem(any(CodeSystem.class));
  }

  @Test
  void retrieveAllCodeSystemsDoesNotLetInvalidVersionSuppressLatest() {
    // given
    var valid = buildFhirCodeSystem("logical-id", "2");
    valid.setMeta(new Meta().setLastUpdated(Date.from(Instant.parse("2020-01-01T00:00:00Z"))));
    var invalid = buildFhirCodeSystem("logical-id", "null");
    invalid.setMeta(new Meta().setLastUpdated(Date.from(Instant.parse("2024-01-01T00:00:00Z"))));
    stubSearchAndHistory(valid, invalid);

    // when
    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    // then
    assertTrue(result.get(0).isLatestVersion());
    assertFalse(result.get(1).isLatestVersion());
    verify(codeSystemService).createCodeSystem(result.get(0));
    verifyNoMoreInteractions(codeSystemService);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "missingVersion",
        "blankVersion",
        "nullVersion",
        "missingUrl",
        "blankUrl",
        "nullUrl"
      })
  void refreshPreservesExistingLatestAndPersistsUsableHistoryWhenCurrentIdentityInvalid(
      String invalidField) {
    // given
    var current = buildFhirCodeSystem("logical-id", "3");
    switch (invalidField) {
      case "missingVersion" -> current.setVersion(null);
      case "blankVersion" -> current.setVersion(" ");
      case "nullVersion" -> current.setVersion("null");
      case "missingUrl" -> current.setUrl(null);
      case "blankUrl" -> current.setUrl(" ");
      case "nullUrl" -> current.setUrl("null");
      default -> throw new IllegalArgumentException(invalidField);
    }
    var historyLatest = buildFhirCodeSystem("logical-id", "2");
    historyLatest.setTitle("Updated historical title");
    historyLatest.setMeta(new Meta().setVersionId("revision-2"));
    var historyOther = buildFhirCodeSystem("logical-id", "1");
    var historyNew = buildFhirCodeSystem("logical-id", "new-history-version");
    stubSearchAndHistory(current, historyLatest, historyOther, historyNew);
    CodeSystem existingLatest =
        CodeSystem.builder()
            .id("existing-latest")
            .fullUrl("http://example.com/system")
            .name("Existing")
            .isLatestVersion(true)
            .version(CodeSystem.Version.builder().fhirVersion("2").vsacVersion("mapped-2").build())
            .build();
    CodeSystem existingOther =
        existingLatest.toBuilder()
            .id("existing-other")
            .isLatestVersion(false)
            .version(CodeSystem.Version.builder().fhirVersion("1").build())
            .build();
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion("http://example.com/system", "2"))
        .thenReturn(Optional.of(existingLatest));
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion("http://example.com/system", "1"))
        .thenReturn(Optional.of(existingOther));
    when(codeSystemRepository.findById("existing-latest")).thenReturn(Optional.of(existingLatest));
    when(codeSystemRepository.findById("existing-other")).thenReturn(Optional.of(existingOther));
    when(codeSystemRepository.findAllByFullUrl("http://example.com/system"))
        .thenReturn(List.of(existingLatest, existingOther));
    when(codeSystemRepository.save(any(CodeSystem.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    CodeSystemSyncService syncWithPersistence =
        new CodeSystemSyncService(
            mockContext,
            fhirTerminologyServiceWebClient,
            new CodeSystemService(codeSystemRepository),
            codeSystemRepository);
    Logger logger = (Logger) LoggerFactory.getLogger(CodeSystemSyncService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    List<CodeSystem> result;

    // when
    try {
      result = syncWithPersistence.remoteRetrieveAllCodeSystems(umlsUser);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    // then
    assertTrue(existingLatest.isLatestVersion());
    assertFalse(existingOther.isLatestVersion());
    assertEquals("Updated historical title", existingLatest.getTitle());
    assertEquals("revision-2", existingLatest.getVersionId());
    assertEquals("mapped-2", existingLatest.getVersion().getVsacVersion());
    CodeSystem newVersion =
        result.stream()
            .filter(cs -> "new-history-version".equals(cs.getVersion().getFhirVersion()))
            .findFirst()
            .orElseThrow();
    assertFalse(newVersion.isLatestVersion());
    verify(codeSystemRepository).save(newVersion);
    verify(codeSystemRepository, times(3)).save(any(CodeSystem.class));
    assertTrue(
        appender.list.stream()
            .anyMatch(
                event ->
                    event.getLevel() == Level.WARN
                        && event
                            .getFormattedMessage()
                            .contains("Current VSAC CodeSystem logical-id")
                        && event
                            .getFormattedMessage()
                            .contains("preserving existing latest flags")));
  }

  @Test
  void refreshKeepsFirstHistoryLatestWhenCurrentIdentityInvalidAndMultipleLatestExist() {
    // given
    String url = "http://example.com/system";
    var current = buildFhirCodeSystem("logical-id", null);
    stubSearchAndHistory(
        current, buildFhirCodeSystem("logical-id", "2"), buildFhirCodeSystem("logical-id", "1"));
    Map<String, CodeSystem> stored = new LinkedHashMap<>();
    stored.put("0", storedCodeSystem(url, "0", true, Instant.parse("2026-11-01T00:00:00Z")));
    stored.put("1", storedCodeSystem(url, "1", true, Instant.parse("2026-10-01T00:00:00Z")));
    stored.put("2", storedCodeSystem(url, "2", true, Instant.parse("2026-09-01T00:00:00Z")));
    stored.put("3", storedCodeSystem(url, "3", false, null));
    CodeSystemSyncService syncWithPersistence = syncWithStoredCodeSystems(url, stored);

    // when
    syncWithPersistence.remoteRetrieveAllCodeSystems(umlsUser);

    // then
    assertEquals(
        List.of("2"),
        stored.values().stream()
            .filter(CodeSystem::isLatestVersion)
            .map(cs -> cs.getVersion().getFhirVersion())
            .toList());
  }

  @Test
  void refreshKeepsMostRecentlyUpdatedLatestWhenCurrentIdentityInvalidAndNoLatestInHistory() {
    // given
    String url = "http://example.com/system";
    var current = buildFhirCodeSystem("logical-id", null);
    stubSearchAndHistory(current, buildFhirCodeSystem("logical-id", "3"));
    Map<String, CodeSystem> stored = new LinkedHashMap<>();
    stored.put("0", storedCodeSystem(url, "0", true, null));
    stored.put("1", storedCodeSystem(url, "1", true, Instant.parse("2026-11-01T00:00:00Z")));
    stored.put("2", storedCodeSystem(url, "2", true, Instant.parse("2026-10-01T00:00:00Z")));
    stored.put("3", storedCodeSystem(url, "3", false, null));
    CodeSystemSyncService syncWithPersistence = syncWithStoredCodeSystems(url, stored);
    Logger logger = (Logger) LoggerFactory.getLogger(CodeSystemSyncService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    // when
    try {
      syncWithPersistence.remoteRetrieveAllCodeSystems(umlsUser);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    // then
    assertEquals(
        List.of("1"),
        stored.values().stream()
            .filter(CodeSystem::isLatestVersion)
            .map(cs -> cs.getVersion().getFhirVersion())
            .toList());
    assertTrue(
        appender.list.stream()
            .anyMatch(
                event ->
                    event.getLevel() == Level.WARN
                        && event
                            .getFormattedMessage()
                            .contains("Multiple latest CodeSystem versions found for url")
                        && event.getFormattedMessage().contains("kept version: 1")));
  }

  private CodeSystem storedCodeSystem(
      String url, String version, boolean latest, Instant lastUpdated) {
    return CodeSystem.builder()
        .id("version-" + version)
        .fullUrl(url)
        .name("Existing")
        .isLatestVersion(latest)
        .lastUpdated(lastUpdated)
        .version(CodeSystem.Version.builder().fhirVersion(version).build())
        .build();
  }

  private CodeSystemSyncService syncWithStoredCodeSystems(
      String url, Map<String, CodeSystem> stored) {
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(eq(url), anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(1))));
    when(codeSystemRepository.findById(anyString()))
        .thenAnswer(
            invocation ->
                stored.values().stream()
                    .filter(cs -> cs.getId().equals(invocation.getArgument(0)))
                    .findFirst());
    when(codeSystemRepository.findAllByFullUrl(url))
        .thenAnswer(invocation -> new ArrayList<>(stored.values()));
    when(codeSystemRepository.save(any(CodeSystem.class)))
        .thenAnswer(
            invocation -> {
              CodeSystem saved = invocation.getArgument(0);
              stored.put(saved.getVersion().getFhirVersion(), saved);
              return saved;
            });
    return new CodeSystemSyncService(
        mockContext,
        fhirTerminologyServiceWebClient,
        new CodeSystemService(codeSystemRepository),
        codeSystemRepository);
  }

  @Test
  void refreshDemotesExistingLatestForHistoricalUrlWhenCurrentIdentityValid() {
    // given
    String oldUrl = "http://example.com/old-system";
    var current = buildFhirCodeSystem("logical-id", "2");
    var historical = buildFhirCodeSystem("logical-id", "1");
    historical.setUrl(oldUrl);
    stubSearchAndHistory(current, historical);
    CodeSystem existingOldUrlLatest =
        CodeSystem.builder()
            .id("old-url-latest")
            .fullUrl(oldUrl)
            .name("Existing")
            .isLatestVersion(true)
            .version(CodeSystem.Version.builder().fhirVersion("1").build())
            .build();
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion("http://example.com/system", "2"))
        .thenReturn(Optional.empty());
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(oldUrl, "1"))
        .thenReturn(Optional.of(existingOldUrlLatest));
    when(codeSystemRepository.findById("old-url-latest"))
        .thenReturn(Optional.of(existingOldUrlLatest));
    when(codeSystemRepository.save(any(CodeSystem.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    CodeSystemSyncService syncWithPersistence =
        new CodeSystemSyncService(
            mockContext,
            fhirTerminologyServiceWebClient,
            new CodeSystemService(codeSystemRepository),
            codeSystemRepository);

    // when
    List<CodeSystem> result = syncWithPersistence.remoteRetrieveAllCodeSystems(umlsUser);

    // then
    assertEquals(2, result.size());
    assertTrue(result.get(0).isLatestVersion());
    assertFalse(result.get(1).isLatestVersion());
    assertFalse(existingOldUrlLatest.isLatestVersion());
    verify(codeSystemRepository).save(existingOldUrlLatest);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void refreshPersistsHistoryPreservesMappingsAndDemotesPreviousLatest(boolean latestExists) {
    // given
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
            .isLatestVersion(true)
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
    version2.setMeta(new Meta().setVersionId("revision-2"));
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

    // when
    List<CodeSystem> result = syncWithPersistence.remoteRetrieveAllCodeSystems(umlsUser);

    // then
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
    assertNull(stored.get("2").getLastUpdatedUpstream());
    assertEquals(1, stored.values().stream().filter(CodeSystem::isLatestVersion).count());
    if (latestExists) {
      assertEquals("mapped-2", stored.get("2").getVersion().getVsacVersion());
    }
    verify(codeSystemRepository, never()).findAllByOid(anyString());
  }

  @Test
  void retrieveAllCodeSystemsIteratesThroughThousandsOfSearchAndHistoryPages() {
    // given
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

    // when
    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    // then
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
  void retrieveAllCodeSystemsSkipsCodeSystemWithIncompleteHistoryNextLink(String query) {
    // given
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

    // when
    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    // then
    assertTrue(result.isEmpty());
    verifyNoInteractions(codeSystemRepository, codeSystemService);
  }

  @ParameterizedTest
  @ValueSource(strings = {"firstPage", "laterPage", "malformedResponse"})
  void refreshPersistsOtherCodeSystemsWhenOneHistoryFails(String failureType) {
    // given
    IParser parser = FhirContext.forR4().newJsonParser();
    when(mockContext.newJsonParser()).thenReturn(parser);
    Bundle search = new Bundle();
    for (String id : List.of("before", "failed", "after")) {
      var current = buildFhirCodeSystem(id, "2");
      current.setUrl("http://example.com/" + id);
      search.addEntry().setResource(current);
      if (!"failed".equals(id)) {
        Bundle history = new Bundle();
        history.addEntry().setResource(current);
        when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(id, 0, 50, TEST_API_KEY))
            .thenReturn(parser.encodeResourceToString(history));
      }
    }
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(0, 50, TEST_API_KEY))
        .thenReturn(parser.encodeResourceToString(search));
    if ("firstPage".equals(failureType)) {
      when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage("failed", 0, 50, TEST_API_KEY))
          .thenThrow(new IllegalStateException("VSAC unavailable"));
    } else if ("malformedResponse".equals(failureType)) {
      when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage("failed", 0, 50, TEST_API_KEY))
          .thenReturn("not FHIR JSON");
    } else {
      Bundle partialHistory = new Bundle();
      partialHistory.addEntry().setResource(buildFhirCodeSystem("failed", "1"));
      partialHistory
          .addLink()
          .setRelation("next")
          .setUrl("http://example.com/_history?_offset=50&_count=50");
      when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage("failed", 0, 50, TEST_API_KEY))
          .thenReturn(parser.encodeResourceToString(partialHistory));
      when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage("failed", 50, 50, TEST_API_KEY))
          .thenThrow(new IllegalStateException("VSAC unavailable"));
    }
    Logger logger = (Logger) LoggerFactory.getLogger(CodeSystemSyncService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    List<CodeSystem> result;

    // when
    try {
      result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    // then
    assertEquals(
        List.of("http://example.com/before", "http://example.com/after"),
        result.stream().map(CodeSystem::getFullUrl).toList());
    verify(codeSystemService, times(2)).createCodeSystem(argThat(CodeSystem::isLatestVersion));
    verify(codeSystemRepository, never())
        .findByFullUrlAndVersionFhirVersion(eq("http://example.com/failed"), anyString());
    verifyNoMoreInteractions(codeSystemService);
    assertTrue(
        appender.list.stream()
            .anyMatch(
                event ->
                    event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("left unchanged: [failed]")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void refreshDoesNotUpdateOrCleanUpFailedCodeSystemWithInvalidCurrentIdentity(boolean missingUrl) {
    // given
    var current = buildFhirCodeSystem("failed", missingUrl ? "2" : null);
    if (missingUrl) {
      current.setUrl(null);
    }
    IParser parser = FhirContext.forR4().newJsonParser();
    when(mockContext.newJsonParser()).thenReturn(parser);
    Bundle search = new Bundle();
    search.addEntry().setResource(current);
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(0, 50, TEST_API_KEY))
        .thenReturn(parser.encodeResourceToString(search));
    when(fhirTerminologyServiceWebClient.getCodeSystemHistoryPage("failed", 0, 50, TEST_API_KEY))
        .thenThrow(new IllegalStateException("VSAC unavailable"));

    // when
    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    // then
    assertTrue(result.isEmpty());
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
  void retrieveAllCodeSystemsReturnsCustomOidForHCPCS() {
    // given
    var codeSystem = buildFhirCodeSystem("logical-id", "1");
    codeSystem.setIdentifier(
        List.of(
            new Identifier().setValue("urn:oid:2.16.840.1.113883.6.14,2.16.840.1.113883.6.285")));
    stubSearchAndHistory(codeSystem);

    // when
    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    // then
    assertEquals(1, result.size());
    assertEquals("urn:oid:2.16.840.1.113883.6.285", result.get(0).getOid());
    verify(codeSystemService).createCodeSystem(result.get(0));
  }

  @Test
  void retrieveAllCodeSystemsReturnsEmptyOidWhenIdentifierValueEmpty() {
    // given
    var codeSystem = buildFhirCodeSystem("logical-id", "1");
    codeSystem.setIdentifier(List.of(new Identifier().setValue("")));
    stubSearchAndHistory(codeSystem);

    // when
    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    // then
    assertEquals(1, result.size());
    assertEquals("", result.get(0).getOid());
    verify(codeSystemService).createCodeSystem(result.get(0));
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
