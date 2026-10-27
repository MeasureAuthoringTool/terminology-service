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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(codeSystemRepository.save(any(CodeSystem.class)))
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

    verify(codeSystemRepository, times(2)).save(any(CodeSystem.class));
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
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(codeSystemRepository.save(any(CodeSystem.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    List<CodeSystem> codeSystems = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    verify(codeSystemRepository, times(2)).save(any(CodeSystem.class));
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
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.of(existingCodeSystem));
    when(codeSystemRepository.save(any(CodeSystem.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

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
    when(codeSystemRepository.findByFullUrlAndVersionFhirVersion(anyString(), anyString()))
        .thenReturn(Optional.of(existingCodeSystem));
    when(codeSystemRepository.save(any(CodeSystem.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

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

    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    assertEquals(1, result.size());
    assertEquals(
        "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
        result.get(0).getFullUrl());
    assertEquals("null", result.get(0).getVersion().getFhirVersion());
    verify(codeSystemRepository, never()).save(any(CodeSystem.class));
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

    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    assertEquals(1, result.size());
    assertEquals(
        "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
        result.get(0).getFullUrl());
    assertNull(result.get(0).getVersion().getFhirVersion());
    verify(codeSystemRepository, never()).save(any(CodeSystem.class));
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

    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    assertEquals(1, result.size());
    assertEquals("null", result.get(0).getFullUrl());
    verify(codeSystemRepository, never()).save(any(CodeSystem.class));
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

    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    assertEquals(1, result.size());
    assertNull(result.get(0).getFullUrl());
    verify(codeSystemRepository, never()).save(any(CodeSystem.class));
    verify(codeSystemRepository, never())
        .findByFullUrlAndVersionFhirVersion(anyString(), anyString());
  }

  @Test
  void retrieveAllCodeSystemsParsesOffsetAndCountFromNextLinkAndRecurses() {
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

    // second bundle (recursive result) with another CodeSystem
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

    // initial page invoked by retrieveAllCodeSystems -> return page for offset=0,count=50
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(eq(0), eq(50), anyString()))
        .thenReturn(json1);
    // return page for the recursive offset=50,count=50
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(eq(50), eq(50), anyString()))
        .thenReturn(json2);
    when(codeSystemRepository.save(any(CodeSystem.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    var result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);

    // should collect both code systems from initial + recursive
    assertEquals(2, result.size());
    // calls getCodeSystemsPage(offset,count,apiKey) when retrieving pages
    verify(fhirTerminologyServiceWebClient, atLeast(1))
        .getCodeSystemsPage(anyInt(), anyInt(), eq(TEST_API_KEY));
  }

  @Test
  void retrieveAllCodeSystemsHandlesDataAccessException() {
    UmlsUser umlsUser = UmlsUser.builder().apiKey(TEST_API_KEY).harpId(TEST_HARP_ID).build();
    // Mock repository to throw DataAccessException when save is called
    doThrow(new DataAccessException("Simulated DB error") {})
        .when(codeSystemRepository)
        .save(any());
    when(mockContext.newJsonParser()).thenReturn(FhirContext.forR4().newJsonParser());
    when(fhirTerminologyServiceWebClient.getCodeSystemsPage(anyInt(), anyInt(), anyString()))
        .thenReturn(
            "{\"resourceType\":\"Bundle\",\"entry\":[{\"resource\":{\"resourceType\":\"CodeSystem\",\"id\":\"cs1\",\"url\":\"http://lonic.org\",\"name\":\"LOINC\",\"version\":\"2.40\",\"title\":\"LOINC\",\"identifier\":[{\"value\":\"urn:oid:2.16.840.1.113883.6.1\"}]}}]}");
    // Call the method under test
    List<CodeSystem> result = codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser);
    // Assert that the result is not empty and the error branch was triggered
    assertFalse(result.isEmpty());
    assertEquals("LOINC", result.get(0).getName());
  }

  @Test
  void retrieveAllCodeSystemsOffsetNullFromNextLinkAssertsNotNull() {
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
        AssertionError.class, () -> codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser));
  }

  @Test
  void testRemoteRetrieveAllCodeSystemsCountNullFromNextLinkAssertsNotNull() {
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
        AssertionError.class, () -> codeSystemSyncService.remoteRetrieveAllCodeSystems(umlsUser));
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
}
