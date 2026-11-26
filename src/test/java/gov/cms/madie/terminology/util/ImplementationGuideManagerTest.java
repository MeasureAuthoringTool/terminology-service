package gov.cms.madie.terminology.util;

import gov.cms.madie.cql_elm_translator.utils.ImplementationGuideLoader;
import gov.cms.madie.terminology.models.MadieValueSet;
import org.cqframework.fhir.npm.NpmPackageManager;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImplementationGuideManagerTest {

  private ImplementationGuideManager manager;

  // Minimal StructureDefinition JSON — one versioned binding, one unversioned binding
  private static final String STRUCTURE_DEF_WITH_BINDING =
      """
      {
        "resourceType": "StructureDefinition",
        "id": "us-core-patient",
        "url": "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient",
        "name": "USCorePatientProfile",
        "status": "active",
        "kind": "resource",
        "abstract": false,
        "type": "Patient",
        "baseDefinition": "http://hl7.org/fhir/StructureDefinition/Patient",
        "snapshot": {
          "element": [
            {
              "id": "Patient.gender",
              "path": "Patient.gender",
              "binding": {
                "strength": "required",
                "valueSet": "http://hl7.org/fhir/ValueSet/administrative-gender|4.0.1"
              }
            },
            {
              "id": "Patient.communication.language",
              "path": "Patient.communication.language",
              "binding": {
                "strength": "extensible",
                "valueSet": "http://hl7.org/fhir/us/core/ValueSet/simple-language"
              }
            },
            {
              "id": "Patient.name",
              "path": "Patient.name",
              "comment": "No binding on this element"
            }
          ]
        }
      }
      """;

  // StructureDefinition with no ValueSet bindings
  private static final String STRUCTURE_DEF_WITHOUT_BINDING =
      """
      {
        "resourceType": "StructureDefinition",
        "id": "us-core-organization",
        "url": "http://hl7.org/fhir/us/core/StructureDefinition/us-core-organization",
        "name": "USCoreOrganizationProfile",
        "status": "active",
        "kind": "resource",
        "abstract": false,
        "type": "Organization",
        "baseDefinition": "http://hl7.org/fhir/StructureDefinition/Organization",
        "snapshot": {
          "element": [
            {
              "id": "Organization.name",
              "path": "Organization.name"
            }
          ]
        }
      }
      """;

  /** Builds a test IG with a dependsOn entry — required for the manager to attempt parsing. */
  private static ImplementationGuide buildTestIg(String name, String version) {
    ImplementationGuide ig = new ImplementationGuide();
    ig.setName(name);
    ig.setVersion(version);
    ig.setUrl("http://hl7.org/fhir/us/core/ImplementationGuide/" + name);
    ImplementationGuideDependsOnComponent dep = new ImplementationGuideDependsOnComponent();
    dep.setUri("http://hl7.org/fhir/us/core");
    dep.setPackageId("hl7.fhir.us.core");
    dep.setVersion(version);
    ig.addDependsOn(dep);
    return ig;
  }

  private static InputStream toInputStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }

  @BeforeEach
  void setUp() {
    manager = new ImplementationGuideManager();
  }

  // ---------------------------------------------------------------------------
  // collectValueSetDependencies()
  // ---------------------------------------------------------------------------

  @Test
  void collectValueSetDependenciesReturnsEmptySetWhenIgIsNull() {
    Set<MadieValueSet> result = manager.collectValueSetDependencies(null);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void collectValueSetDependenciesReturnsEmptySetWhenIgHasNoDependencies() {
    ImplementationGuide ig = new ImplementationGuide();
    ig.setName("no-deps-ig");

    Set<MadieValueSet> result = manager.collectValueSetDependencies(ig);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void collectValueSetDependenciesExtractsVersionedAndUnversionedValueSetBindings()
      throws IOException {
    ImplementationGuide ig = buildTestIg("hl7.fhir.us.core", "6.1.0");

    NpmPackage mockPackage = mock(NpmPackage.class);
    when(mockPackage.listResources("StructureDefinition"))
        .thenReturn(List.of("StructureDefinition-us-core-patient.json"));
    when(mockPackage.load("package", "StructureDefinition-us-core-patient.json"))
        .thenReturn(toInputStream(STRUCTURE_DEF_WITH_BINDING));

    NpmPackageManager mockPm = mock(NpmPackageManager.class);
    when(mockPm.getNpmList()).thenReturn(List.of(mockPackage));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), any()))
          .thenReturn(mockPm);

      Set<MadieValueSet> valueSets = manager.collectValueSetDependencies(ig);

      assertNotNull(valueSets);
      assertThat(valueSets.size(), is(equalTo(2)));

      // Versioned binding: url + version split on '|'
      assertTrue(
          valueSets.stream()
              .anyMatch(
                  vs ->
                      "http://hl7.org/fhir/ValueSet/administrative-gender".equals(vs.getUrl())
                          && "4.0.1".equals(vs.getVersion())));

      // Unversioned binding: url only, version null
      assertTrue(
          valueSets.stream()
              .anyMatch(
                  vs ->
                      "http://hl7.org/fhir/us/core/ValueSet/simple-language".equals(vs.getUrl())
                          && vs.getVersion() == null));
    }
  }

  @Test
  void collectValueSetDependenciesHandlesStructureDefWithNoBindings() throws IOException {
    ImplementationGuide ig = buildTestIg("hl7.fhir.us.core", "6.1.0");

    NpmPackage mockPackage = mock(NpmPackage.class);
    when(mockPackage.listResources("StructureDefinition"))
        .thenReturn(List.of("StructureDefinition-us-core-organization.json"));
    when(mockPackage.load("package", "StructureDefinition-us-core-organization.json"))
        .thenReturn(toInputStream(STRUCTURE_DEF_WITHOUT_BINDING));

    NpmPackageManager mockPm = mock(NpmPackageManager.class);
    when(mockPm.getNpmList()).thenReturn(List.of(mockPackage));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), any()))
          .thenReturn(mockPm);

      Set<MadieValueSet> valueSets = manager.collectValueSetDependencies(ig);

      assertNotNull(valueSets);
      assertTrue(valueSets.isEmpty(), "No bindings should produce an empty set");
    }
  }

  @Test
  void collectValueSetDependenciesHandlesEmptyStructureDefinitionList() throws IOException {
    ImplementationGuide ig = buildTestIg("empty-ig", "1.0.0");

    NpmPackage mockPackage = mock(NpmPackage.class);
    when(mockPackage.listResources("StructureDefinition")).thenReturn(Collections.emptyList());

    NpmPackageManager mockPm = mock(NpmPackageManager.class);
    when(mockPm.getNpmList()).thenReturn(List.of(mockPackage));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), any()))
          .thenReturn(mockPm);

      Set<MadieValueSet> result = manager.collectValueSetDependencies(ig);

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }
  }

  @Test
  void collectValueSetDependenciesMergesBindingsFromMultiplePackages() throws IOException {
    ImplementationGuide ig = buildTestIg("composite-ig", "1.0.0");

    NpmPackage pkg1 = mock(NpmPackage.class);
    when(pkg1.listResources("StructureDefinition"))
        .thenReturn(List.of("StructureDefinition-us-core-patient.json"));
    when(pkg1.load("package", "StructureDefinition-us-core-patient.json"))
        .thenReturn(toInputStream(STRUCTURE_DEF_WITH_BINDING));

    NpmPackage pkg2 = mock(NpmPackage.class);
    when(pkg2.listResources("StructureDefinition"))
        .thenReturn(List.of("StructureDefinition-us-core-organization.json"));
    when(pkg2.load("package", "StructureDefinition-us-core-organization.json"))
        .thenReturn(toInputStream(STRUCTURE_DEF_WITHOUT_BINDING));

    NpmPackageManager mockPm = mock(NpmPackageManager.class);
    when(mockPm.getNpmList()).thenReturn(List.of(pkg1, pkg2));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), any()))
          .thenReturn(mockPm);

      Set<MadieValueSet> result = manager.collectValueSetDependencies(ig);

      assertThat(result.size(), is(equalTo(2)));
      verify(pkg1, times(1)).listResources("StructureDefinition");
      verify(pkg2, times(1)).listResources("StructureDefinition");
    }
  }

  @Test
  void collectValueSetDependenciesDoesNotRetainPackagesBetweenCalls() throws IOException {
    ImplementationGuide ig = buildTestIg("hl7.fhir.us.core", "6.1.0");

    NpmPackage mockPackage = mock(NpmPackage.class);
    when(mockPackage.listResources("StructureDefinition"))
        .thenReturn(List.of("StructureDefinition-us-core-patient.json"));
    when(mockPackage.load("package", "StructureDefinition-us-core-patient.json"))
        .thenAnswer(invocation -> toInputStream(STRUCTURE_DEF_WITH_BINDING));

    NpmPackageManager mockPm = mock(NpmPackageManager.class);
    when(mockPm.getNpmList()).thenReturn(List.of(mockPackage));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), any()))
          .thenReturn(mockPm);

      Set<MadieValueSet> first = manager.collectValueSetDependencies(ig);
      Set<MadieValueSet> second = manager.collectValueSetDependencies(ig);

      // Nothing is cached: each call reloads the IG packages from the package cache
      assertEquals(first, second);
      verify(mockPackage, times(2)).listResources("StructureDefinition");
      loader.verify(
          () -> ImplementationGuideLoader.buildPackageManager(isNull(), eq(ig)), times(2));
    }
  }

  @Test
  void collectValueSetDependenciesLogsAndReturnsEmptySetOnIOException() {
    ImplementationGuide ig = buildTestIg("failing-ig", "1.0.0");

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), any()))
          .thenThrow(new IOException("Simulated network failure"));

      Set<MadieValueSet> result = manager.collectValueSetDependencies(ig);

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }
  }

  // ---------------------------------------------------------------------------
  // getImplementationGuides() / getImplementationGuideNames()
  // ---------------------------------------------------------------------------

  @Test
  void getImplementationGuidesReturnsCatalogFromClasspath() {
    ImplementationGuide ig1 = buildTestIg("madie.ig.one", "1.0.0");
    ImplementationGuide ig2 = buildTestIg("madie.ig.two", "2.0.0");

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(() -> ImplementationGuideLoader.load(anyString())).thenReturn(List.of(ig1, ig2));

      List<ImplementationGuide> result = manager.getImplementationGuides();
      manager.getImplementationGuides(); // catalog is loaded only once

      assertThat(result.size(), is(equalTo(2)));
      assertTrue(result.containsAll(List.of(ig1, ig2)));
      loader.verify(() -> ImplementationGuideLoader.load(anyString()), times(1));
      loader.verify(() -> ImplementationGuideLoader.buildPackageManager(any(), any()), never());
    }
  }

  @Test
  void getImplementationGuideNamesReturnsEmptyListWhenCatalogEmpty() {
    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader
          .when(() -> ImplementationGuideLoader.load(anyString()))
          .thenReturn(Collections.emptyList());

      List<String> result = manager.getImplementationGuideNames();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }
  }

  @Test
  void getImplementationGuideNamesIncludesIgsAndDirectDependenciesWithoutLoadingPackages() {
    ImplementationGuide ig = buildTestIg("madie.ig.one", "1.0.0");
    ig.getDependsOnFirstRep().setVersion("6.1.0");

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(() -> ImplementationGuideLoader.load(anyString())).thenReturn(List.of(ig));

      List<String> result = manager.getImplementationGuideNames();

      assertTrue(result.contains("madie.ig.one v1.0.0"));
      assertTrue(result.contains("hl7.fhir.us.core v6.1.0"));
      loader.verify(() -> ImplementationGuideLoader.buildPackageManager(any(), any()), never());
    }
  }

  // ---------------------------------------------------------------------------
  // getValueSetDependencies(String igName, String version)
  // ---------------------------------------------------------------------------

  @Test
  void getValueSetDependenciesByNameAndVersionTriggersLoadWhenNotLoaded() throws IOException {
    ImplementationGuide ig = buildTestIg("hl7.fhir.us.core", "6.1.0");

    NpmPackage mockPackage = mock(NpmPackage.class);
    when(mockPackage.name()).thenReturn("hl7.fhir.us.core");
    when(mockPackage.version()).thenReturn("6.1.0");
    when(mockPackage.listResources("StructureDefinition"))
        .thenReturn(List.of("StructureDefinition-us-core-patient.json"));
    when(mockPackage.load("package", "StructureDefinition-us-core-patient.json"))
        .thenReturn(toInputStream(STRUCTURE_DEF_WITH_BINDING));

    NpmPackageManager mockPm = mock(NpmPackageManager.class);
    when(mockPm.getNpmList()).thenReturn(List.of(mockPackage));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(() -> ImplementationGuideLoader.load(anyString())).thenReturn(List.of(ig));
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), any()))
          .thenReturn(mockPm);

      // Catalog not loaded yet — calling getValueSetDependencies should trigger it
      List<MadieValueSet> result = manager.getValueSetDependencies("hl7.fhir.us.core", "6.1.0");

      assertFalse(result.isEmpty());
      loader.verify(() -> ImplementationGuideLoader.load(anyString()), times(1));
    }
  }

  @Test
  void getValueSetDependenciesByNameAndVersionReturnsValueSetsForMatchingTopLevelIg()
      throws IOException {
    ImplementationGuide ig = buildTestIg("hl7.fhir.us.core", "6.1.0");

    NpmPackage mockPackage = mock(NpmPackage.class);
    when(mockPackage.name()).thenReturn("hl7.fhir.us.core");
    when(mockPackage.version()).thenReturn("6.1.0");
    when(mockPackage.listResources("StructureDefinition"))
        .thenReturn(List.of("StructureDefinition-us-core-patient.json"));
    when(mockPackage.load("package", "StructureDefinition-us-core-patient.json"))
        .thenReturn(toInputStream(STRUCTURE_DEF_WITH_BINDING));

    NpmPackageManager mockPm = mock(NpmPackageManager.class);
    when(mockPm.getNpmList()).thenReturn(List.of(mockPackage));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(() -> ImplementationGuideLoader.load(anyString())).thenReturn(List.of(ig));
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), any()))
          .thenReturn(mockPm);

      List<MadieValueSet> result = manager.getValueSetDependencies("hl7.fhir.us.core", "6.1.0");

      assertThat(result.size(), is(equalTo(2)));
      assertTrue(
          result.stream()
              .anyMatch(
                  vs -> "http://hl7.org/fhir/ValueSet/administrative-gender".equals(vs.getUrl())));
      assertTrue(
          result.stream()
              .anyMatch(
                  vs ->
                      "http://hl7.org/fhir/us/core/ValueSet/simple-language".equals(vs.getUrl())));
    }
  }

  @Test
  void getValueSetDependenciesByNameAndVersionReturnsValueSetsForMatchingPackage()
      throws IOException {
    ImplementationGuide ig = buildTestIg("composite-ig", "1.0.0");

    NpmPackage pkg = mock(NpmPackage.class);
    when(pkg.name()).thenReturn("hl7.fhir.us.core");
    when(pkg.version()).thenReturn("6.1.0");
    when(pkg.listResources("StructureDefinition"))
        .thenReturn(List.of("StructureDefinition-us-core-patient.json"));
    when(pkg.load("package", "StructureDefinition-us-core-patient.json"))
        .thenReturn(toInputStream(STRUCTURE_DEF_WITH_BINDING));

    NpmPackageManager mockPm = mock(NpmPackageManager.class);
    when(mockPm.getNpmList()).thenReturn(List.of(pkg));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(() -> ImplementationGuideLoader.load(anyString())).thenReturn(List.of(ig));
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), any()))
          .thenReturn(mockPm);

      // Request by package name/version, not the top-level IG name
      List<MadieValueSet> result = manager.getValueSetDependencies("hl7.fhir.us.core", "6.1.0");

      assertFalse(result.isEmpty());
    }
  }

  @Test
  void getValueSetDependenciesByNameAndVersionReturnsEmptyListWhenNotFound() throws IOException {
    ImplementationGuide ig = buildTestIg("hl7.fhir.us.core", "6.1.0");

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(() -> ImplementationGuideLoader.load(anyString())).thenReturn(List.of(ig));

      List<MadieValueSet> result = manager.getValueSetDependencies("unknown.ig", "9.9.9");

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }
  }

  @Test
  void getValueSetDependenciesByNameAndVersionLoadsOnlyRequestedIg() throws IOException {
    ImplementationGuide ig1 = buildTestIg("hl7.fhir.us.core", "6.1.0");
    ImplementationGuide ig2 = buildTestIg("hl7.fhir.us.qicore", "5.0.0");

    NpmPackage pkg1 = mock(NpmPackage.class);
    when(pkg1.name()).thenReturn("hl7.fhir.us.core");
    when(pkg1.version()).thenReturn("6.1.0");
    when(pkg1.listResources("StructureDefinition"))
        .thenReturn(List.of("StructureDefinition-us-core-patient.json"));
    when(pkg1.load("package", "StructureDefinition-us-core-patient.json"))
        .thenReturn(toInputStream(STRUCTURE_DEF_WITH_BINDING));

    NpmPackageManager pm1 = mock(NpmPackageManager.class);
    when(pm1.getNpmList()).thenReturn(List.of(pkg1));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(() -> ImplementationGuideLoader.load(anyString())).thenReturn(List.of(ig1, ig2));
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), eq(ig1)))
          .thenReturn(pm1);

      List<MadieValueSet> result = manager.getValueSetDependencies("hl7.fhir.us.core", "6.1.0");

      assertFalse(result.isEmpty());
      loader.verify(
          () -> ImplementationGuideLoader.buildPackageManager(isNull(), eq(ig1)), times(1));
      loader.verify(
          () -> ImplementationGuideLoader.buildPackageManager(isNull(), eq(ig2)), never());
    }
  }

  @Test
  void getValueSetDependenciesByPackageNameLoadsOnlyIgThatDeclaresDependency() throws IOException {
    ImplementationGuide ig1 = buildTestIg("madie.ig.one", "1.0.0");
    ImplementationGuide ig2 = buildTestIg("madie.ig.two", "1.0.0");

    // Keep ig1 unrelated so only ig2 is a package candidate.
    ig1.getDependsOnFirstRep().setPackageId("hl7.fhir.us.carin-bb");
    ig1.getDependsOnFirstRep().setVersion("2.1.0");

    // Make ig2 depend on the requested package so the lookup can resolve lazily.
    ig2.getDependsOnFirstRep().setPackageId("hl7.fhir.us.core");
    ig2.getDependsOnFirstRep().setVersion("6.1.0");

    NpmPackage pkg2 = mock(NpmPackage.class);
    when(pkg2.name()).thenReturn("hl7.fhir.us.core");
    when(pkg2.version()).thenReturn("6.1.0");
    when(pkg2.listResources("StructureDefinition"))
        .thenReturn(List.of("StructureDefinition-us-core-patient.json"));
    when(pkg2.load("package", "StructureDefinition-us-core-patient.json"))
        .thenReturn(toInputStream(STRUCTURE_DEF_WITH_BINDING));

    NpmPackageManager pm1 = mock(NpmPackageManager.class);
    NpmPackageManager pm2 = mock(NpmPackageManager.class);
    when(pm2.getNpmList()).thenReturn(List.of(pkg2));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(() -> ImplementationGuideLoader.load(anyString())).thenReturn(List.of(ig1, ig2));
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), eq(ig1)))
          .thenReturn(pm1);
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), eq(ig2)))
          .thenReturn(pm2);

      List<MadieValueSet> result = manager.getValueSetDependencies("hl7.fhir.us.core", "6.1.0");

      assertFalse(result.isEmpty());
      loader.verify(
          () -> ImplementationGuideLoader.buildPackageManager(isNull(), eq(ig2)), times(1));
      loader.verify(
          () -> ImplementationGuideLoader.buildPackageManager(isNull(), eq(ig1)), never());
    }
  }

  // ---------------------------------------------------------------------------
  // getValueSetDependencies() — no-arg overload
  // ---------------------------------------------------------------------------

  @Test
  void getValueSetDependenciesAggregatesValueSetsAcrossAllIgs() throws IOException {
    ImplementationGuide ig1 = buildTestIg("hl7.fhir.us.core", "6.1.0");
    ImplementationGuide ig2 = buildTestIg("hl7.fhir.us.qicore", "5.0.0");

    NpmPackage pkg1 = mock(NpmPackage.class);
    when(pkg1.name()).thenReturn("hl7.fhir.us.core");
    when(pkg1.version()).thenReturn("6.1.0");
    when(pkg1.listResources("StructureDefinition"))
        .thenReturn(List.of("StructureDefinition-us-core-patient.json"));
    when(pkg1.load("package", "StructureDefinition-us-core-patient.json"))
        .thenReturn(toInputStream(STRUCTURE_DEF_WITH_BINDING));

    NpmPackage pkg2 = mock(NpmPackage.class);
    when(pkg2.name()).thenReturn("hl7.fhir.us.qicore");
    when(pkg2.version()).thenReturn("5.0.0");
    when(pkg2.listResources("StructureDefinition"))
        .thenReturn(List.of("StructureDefinition-us-core-organization.json"));
    when(pkg2.load("package", "StructureDefinition-us-core-organization.json"))
        .thenReturn(toInputStream(STRUCTURE_DEF_WITHOUT_BINDING));

    NpmPackageManager pm1 = mock(NpmPackageManager.class);
    when(pm1.getNpmList()).thenReturn(List.of(pkg1));
    NpmPackageManager pm2 = mock(NpmPackageManager.class);
    when(pm2.getNpmList()).thenReturn(List.of(pkg2));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(() -> ImplementationGuideLoader.load(anyString())).thenReturn(List.of(ig1, ig2));
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), eq(ig1)))
          .thenReturn(pm1);
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), eq(ig2)))
          .thenReturn(pm2);

      List<MadieValueSet> result = manager.getValueSetDependencies();

      // ig1 contributes 2 bindings, ig2 contributes 0
      assertThat(result.size(), is(equalTo(2)));
    }
  }

  @Test
  void getValueSetDependenciesReturnsEmptyListWhenNoIgsLoaded() {
    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader
          .when(() -> ImplementationGuideLoader.load(anyString()))
          .thenReturn(Collections.emptyList());

      List<MadieValueSet> result = manager.getValueSetDependencies();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }
  }

  @Test
  void getValueSetDependenciesDeduplicatesValueSetsAcrossStructureDefinitions() throws IOException {
    ImplementationGuide ig = buildTestIg("hl7.fhir.us.core", "6.1.0");

    // Two SD files that both bind to the same ValueSet URL
    String sdWithDuplicateBinding =
        """
        {
          "resourceType": "StructureDefinition",
          "id": "us-core-encounter",
          "url": "http://hl7.org/fhir/us/core/StructureDefinition/us-core-encounter",
          "name": "USCoreEncounterProfile",
          "status": "active",
          "kind": "resource",
          "abstract": false,
          "type": "Encounter",
          "baseDefinition": "http://hl7.org/fhir/StructureDefinition/Encounter",
          "snapshot": {
            "element": [
              {
                "id": "Encounter.status",
                "path": "Encounter.status",
                "binding": {
                  "strength": "required",
                  "valueSet": "http://hl7.org/fhir/ValueSet/administrative-gender|4.0.1"
                }
              }
            ]
          }
        }
        """;

    NpmPackage mockPackage = mock(NpmPackage.class);
    when(mockPackage.name()).thenReturn("hl7.fhir.us.core");
    when(mockPackage.version()).thenReturn("6.1.0");
    when(mockPackage.listResources("StructureDefinition"))
        .thenReturn(
            List.of(
                "StructureDefinition-us-core-patient.json",
                "StructureDefinition-us-core-encounter.json"));
    when(mockPackage.load("package", "StructureDefinition-us-core-patient.json"))
        .thenReturn(toInputStream(STRUCTURE_DEF_WITH_BINDING));
    when(mockPackage.load("package", "StructureDefinition-us-core-encounter.json"))
        .thenReturn(toInputStream(sdWithDuplicateBinding));

    NpmPackageManager mockPm = mock(NpmPackageManager.class);
    when(mockPm.getNpmList()).thenReturn(List.of(mockPackage));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(() -> ImplementationGuideLoader.load(anyString())).thenReturn(List.of(ig));
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), any()))
          .thenReturn(mockPm);

      List<MadieValueSet> result = manager.getValueSetDependencies();

      // administrative-gender|4.0.1 appears in both SDs — distinct() should deduplicate
      long count =
          result.stream()
              .filter(
                  vs -> "http://hl7.org/fhir/ValueSet/administrative-gender".equals(vs.getUrl()))
              .count();
      assertThat(count, is(equalTo(1L)));
    }
  }

  // ---------------------------------------------------------------------------
  // Package load failure
  // ---------------------------------------------------------------------------

  @Test
  void getValueSetDependenciesReturnsEmptyListWhenIgLoadFails() {
    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      // load() succeeds but buildPackageManager throws
      ImplementationGuide ig = buildTestIg("failing-ig", "1.0.0");
      loader.when(() -> ImplementationGuideLoader.load(anyString())).thenReturn(List.of(ig));
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), any()))
          .thenThrow(new IOException("network failure"));

      // Should not throw; returns empty list because no IGs were successfully loaded
      List<MadieValueSet> result = manager.getValueSetDependencies();
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }
  }
}
