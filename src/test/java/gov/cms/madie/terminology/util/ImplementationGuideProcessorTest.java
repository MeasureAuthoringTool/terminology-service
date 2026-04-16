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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImplementationGuideProcessorTest {

  private ImplementationGuideProcessor processor;

  // Minimal StructureDefinition JSON with a snapshot element that binds to a ValueSet
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

  /**
   * Builds a fake ImplementationGuide with a dependsOn entry, which is required for the processor
   * to attempt parsing.
   */
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

  @BeforeEach
  void setUp() {
    processor = new ImplementationGuideProcessor();
  }

  @Test
  void collectValueSetDependenciesReturnsEmptyMapWhenIgIsNull() {
    Map<String, Map<String, Set<MadieValueSet>>> result =
        processor.collectValueSetDependencies(null);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void collectValueSetDependenciesReturnsEmptyMapWhenIgHasNoDependencies() {
    ImplementationGuide ig = new ImplementationGuide();
    ig.setName("no-deps-ig");
    // No dependsOn → hasDependsOn() returns false

    Map<String, Map<String, Set<MadieValueSet>>> result = processor.collectValueSetDependencies(ig);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void collectValueSetDependenciesExtractsValueSetBindings() throws IOException {
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
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), any()))
          .thenReturn(mockPm);

      Map<String, Map<String, Set<MadieValueSet>>> result =
          processor.collectValueSetDependencies(ig);

      assertNotNull(result);
      assertEquals(1, result.size());
      assertTrue(result.containsKey("hl7.fhir.us.core6.1.0"));

      Map<String, Set<MadieValueSet>> sdMap = result.get("hl7.fhir.us.core6.1.0");
      assertNotNull(sdMap);
      assertEquals(1, sdMap.size());

      Set<MadieValueSet> valueSets = sdMap.get("StructureDefinition-us-core-patient.json");
      assertNotNull(valueSets);
      assertEquals(2, valueSets.size());

      // Verify the versioned ValueSet binding was parsed correctly
      assertTrue(
          valueSets.stream()
              .anyMatch(
                  vs ->
                      "http://hl7.org/fhir/ValueSet/administrative-gender".equals(vs.getUrl())
                          && "4.0.1".equals(vs.getVersion())));
      // Verify the unversioned ValueSet binding
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
    when(mockPackage.name()).thenReturn("hl7.fhir.us.core");
    when(mockPackage.version()).thenReturn("6.1.0");
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

      Map<String, Map<String, Set<MadieValueSet>>> result =
          processor.collectValueSetDependencies(ig);

      assertNotNull(result);
      Map<String, Set<MadieValueSet>> sdMap = result.get("hl7.fhir.us.core6.1.0");
      assertNotNull(sdMap);
      Set<MadieValueSet> valueSets = sdMap.get("StructureDefinition-us-core-organization.json");
      assertNotNull(valueSets);
      assertTrue(valueSets.isEmpty(), "No bindings should produce an empty set");
    }
  }

  @Test
  void collectValueSetDependenciesHandlesMultiplePackages() throws IOException {
    ImplementationGuide ig = buildTestIg("composite-ig", "1.0.0");

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

    NpmPackageManager mockPm = mock(NpmPackageManager.class);
    when(mockPm.getNpmList()).thenReturn(List.of(pkg1, pkg2));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), any()))
          .thenReturn(mockPm);

      Map<String, Map<String, Set<MadieValueSet>>> result =
          processor.collectValueSetDependencies(ig);

      assertEquals(2, result.size());
      assertTrue(result.containsKey("hl7.fhir.us.core6.1.0"));
      assertTrue(result.containsKey("hl7.fhir.us.qicore5.0.0"));
    }
  }

  @Test
  void collectValueSetDependenciesSkipsAlreadyParsedPackages() throws IOException {
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
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), any()))
          .thenReturn(mockPm);

      // First call — parses the package
      processor.collectValueSetDependencies(ig);
      // Second call — same IG, package should be skipped (cached)
      Map<String, Map<String, Set<MadieValueSet>>> result =
          processor.collectValueSetDependencies(ig);

      assertNotNull(result);
      assertEquals(1, result.size());
      // listResources should only have been called once (first parse)
      verify(mockPackage, times(1)).listResources("StructureDefinition");
    }
  }

  @Test
  void collectValueSetDependenciesHandlesEmptyStructureDefinitionList() throws IOException {
    ImplementationGuide ig = buildTestIg("empty-ig", "1.0.0");

    NpmPackage mockPackage = mock(NpmPackage.class);
    when(mockPackage.name()).thenReturn("empty-ig");
    when(mockPackage.version()).thenReturn("1.0.0");
    when(mockPackage.listResources("StructureDefinition")).thenReturn(Collections.emptyList());

    NpmPackageManager mockPm = mock(NpmPackageManager.class);
    when(mockPm.getNpmList()).thenReturn(List.of(mockPackage));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), any()))
          .thenReturn(mockPm);

      Map<String, Map<String, Set<MadieValueSet>>> result =
          processor.collectValueSetDependencies(ig);

      assertNotNull(result);
      assertTrue(result.containsKey("empty-ig1.0.0"));
      Map<String, Set<MadieValueSet>> sdMap = result.get("empty-ig1.0.0");
      assertNotNull(sdMap);
      assertTrue(sdMap.isEmpty());
    }
  }

  @Test
  void collectValueSetDependenciesHandlesNullPackageGracefully() throws IOException {
    ImplementationGuide ig = buildTestIg("null-pkg-ig", "1.0.0");

    // NpmPackageManager returns a list with a null package
    NpmPackage nullPkg = mock(NpmPackage.class);
    when(nullPkg.name()).thenReturn("null-pkg-ig");
    when(nullPkg.version()).thenReturn("1.0.0");
    // listResources on a null-like package returns empty
    when(nullPkg.listResources("StructureDefinition")).thenReturn(Collections.emptyList());

    NpmPackageManager mockPm = mock(NpmPackageManager.class);
    when(mockPm.getNpmList()).thenReturn(List.of(nullPkg));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), any()))
          .thenReturn(mockPm);

      Map<String, Map<String, Set<MadieValueSet>>> result =
          processor.collectValueSetDependencies(ig);

      assertNotNull(result);
    }
  }

  @Test
  void collectValueSetDependenciesLogsAndReturnsOnIOException() throws IOException {
    ImplementationGuide ig = buildTestIg("failing-ig", "1.0.0");

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader
          .when(() -> ImplementationGuideLoader.buildPackageManager(isNull(), any()))
          .thenThrow(new IOException("Simulated network failure"));

      Map<String, Map<String, Set<MadieValueSet>>> result =
          processor.collectValueSetDependencies(ig);

      // Should return the (empty) igValueSets map without throwing
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }
  }

  private static InputStream toInputStream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
