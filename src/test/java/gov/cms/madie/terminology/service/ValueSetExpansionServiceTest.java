package gov.cms.madie.terminology.service;

import gov.cms.madie.cql_elm_translator.utils.ImplementationGuideLoader;
import gov.cms.madie.terminology.models.MadieValueSet;
import gov.cms.madie.terminology.repositories.ValueSetExpansionRepository;
import gov.cms.madie.terminology.util.ImplementationGuideProcessor;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValueSetExpansionServiceTest {

  @Mock private ImplementationGuideProcessor implementationGuideProcessor;
  @Mock private ValueSetExpansionRepository vseRepo;

  /** Builds a minimal IG with a name and version for filtering tests. */
  private static ImplementationGuide buildIg(String name, String version) {
    ImplementationGuide ig = new ImplementationGuide();
    ig.setName(name);
    ig.setVersion(version);
    return ig;
  }

  /** Builds a MadieValueSet with a url and optional version. */
  private static MadieValueSet buildValueSet(String url, String version) {
    return MadieValueSet.builder().url(url).version(version).build();
  }

  // ---------------------------------------------------------------------------
  // getValueSetDependencies(String igName, String version)
  // ---------------------------------------------------------------------------

  @Test
  void getValueSetDependenciesByNameAndVersionReturnsEmptySetWhenNoIgsLoaded() {
    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(ImplementationGuideLoader::load).thenReturn(Collections.emptyList());

      ValueSetExpansionService service =
          new ValueSetExpansionService(implementationGuideProcessor, vseRepo);

      Set<MadieValueSet> result = service.getValueSetDependencies("hl7.fhir.us.core", "6.1.0");

      assertNotNull(result);
      assertTrue(result.isEmpty());
      verify(implementationGuideProcessor, never()).collectValueSetDependencies(any());
    }
  }

  @Test
  void getValueSetDependenciesByNameAndVersionReturnsEmptySetWhenIgNotFound() {
    ImplementationGuide ig = buildIg("hl7.fhir.us.core", "6.1.0");

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(ImplementationGuideLoader::load).thenReturn(List.of(ig));

      when(implementationGuideProcessor.collectValueSetDependencies(isNull()))
          .thenReturn(new HashMap<>());

      ValueSetExpansionService service =
          new ValueSetExpansionService(implementationGuideProcessor, vseRepo);

      // Request a name/version that doesn't match the loaded IG
      Set<MadieValueSet> result = service.getValueSetDependencies("hl7.fhir.us.qicore", "5.0.0");

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }
  }

  @Test
  void getValueSetDependenciesByNameAndVersionReturnsValueSetsForMatchingIg() {
    ImplementationGuide ig = buildIg("hl7.fhir.us.core", "6.1.0");
    MadieValueSet vs1 = buildValueSet("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3", "20230401");
    MadieValueSet vs2 = buildValueSet("http://cts.nlm.nih.gov/fhir/ValueSet/4.5.6", null);

    Map<String, Map<String, Set<MadieValueSet>>> igMap = new HashMap<>();
    Map<String, Set<MadieValueSet>> sdMap = new HashMap<>();
    sdMap.put("StructureDefinition-us-core-patient.json", Set.of(vs1, vs2));
    igMap.put("hl7.fhir.us.core6.1.0", sdMap);

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(ImplementationGuideLoader::load).thenReturn(List.of(ig));

      when(implementationGuideProcessor.collectValueSetDependencies(ig)).thenReturn(igMap);

      ValueSetExpansionService service =
          new ValueSetExpansionService(implementationGuideProcessor, vseRepo);

      Set<MadieValueSet> result = service.getValueSetDependencies("hl7.fhir.us.core", "6.1.0");

      assertEquals(2, result.size());
      assertTrue(result.contains(vs1));
      assertTrue(result.contains(vs2));
      verify(implementationGuideProcessor, times(1)).collectValueSetDependencies(ig);
    }
  }

  @Test
  void getValueSetDependenciesByNameAndVersionIsCaseInsensitive() {
    ImplementationGuide ig = buildIg("hl7.fhir.us.core", "6.1.0");
    MadieValueSet vs = buildValueSet("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3", "20230401");

    Map<String, Map<String, Set<MadieValueSet>>> igMap = new HashMap<>();
    Map<String, Set<MadieValueSet>> sdMap = new HashMap<>();
    sdMap.put("StructureDefinition-us-core-patient.json", Set.of(vs));
    igMap.put("hl7.fhir.us.core6.1.0", sdMap);

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(ImplementationGuideLoader::load).thenReturn(List.of(ig));

      when(implementationGuideProcessor.collectValueSetDependencies(ig)).thenReturn(igMap);

      ValueSetExpansionService service =
          new ValueSetExpansionService(implementationGuideProcessor, vseRepo);

      // Pass name/version in different case
      Set<MadieValueSet> result = service.getValueSetDependencies("HL7.FHIR.US.CORE", "6.1.0");

      assertEquals(1, result.size());
    }
  }

  // ---------------------------------------------------------------------------
  // getValueSetDependencies() — no-arg overload
  // ---------------------------------------------------------------------------

  @Test
  void getValueSetDependenciesReturnsEmptyMapWhenNoIgsLoaded() {
    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(ImplementationGuideLoader::load).thenReturn(Collections.emptyList());

      ValueSetExpansionService service =
          new ValueSetExpansionService(implementationGuideProcessor, vseRepo);

      Map<String, Map<String, Set<MadieValueSet>>> result = service.getValueSetDependencies();

      assertNotNull(result);
      assertTrue(result.isEmpty());
      verify(implementationGuideProcessor, never()).collectValueSetDependencies(any());
    }
  }

  @Test
  void getValueSetDependenciesAggregatesAcrossMultipleIgs() {
    ImplementationGuide ig1 = buildIg("hl7.fhir.us.core", "6.1.0");
    ImplementationGuide ig2 = buildIg("hl7.fhir.us.qicore", "5.0.0");

    MadieValueSet vs1 = buildValueSet("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3", "20230401");
    MadieValueSet vs2 = buildValueSet("http://cts.nlm.nih.gov/fhir/ValueSet/4.5.6", null);

    Map<String, Map<String, Set<MadieValueSet>>> igMap1 = new HashMap<>();
    igMap1.put("hl7.fhir.us.core6.1.0", Map.of("sd1.json", Set.of(vs1)));

    Map<String, Map<String, Set<MadieValueSet>>> igMap2 = new HashMap<>();
    igMap2.put("hl7.fhir.us.qicore5.0.0", Map.of("sd2.json", Set.of(vs2)));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(ImplementationGuideLoader::load).thenReturn(List.of(ig1, ig2));

      when(implementationGuideProcessor.collectValueSetDependencies(ig1)).thenReturn(igMap1);
      when(implementationGuideProcessor.collectValueSetDependencies(ig2)).thenReturn(igMap2);

      ValueSetExpansionService service =
          new ValueSetExpansionService(implementationGuideProcessor, vseRepo);

      Map<String, Map<String, Set<MadieValueSet>>> result = service.getValueSetDependencies();

      assertEquals(2, result.size());
      assertTrue(result.containsKey("hl7.fhir.us.core6.1.0"));
      assertTrue(result.containsKey("hl7.fhir.us.qicore5.0.0"));
      verify(implementationGuideProcessor, times(1)).collectValueSetDependencies(ig1);
      verify(implementationGuideProcessor, times(1)).collectValueSetDependencies(ig2);
    }
  }

  // ---------------------------------------------------------------------------
  // updateValueSetDependencies()
  // ---------------------------------------------------------------------------

  @Test
  void updateValueSetDependenciesSavesNewValueSetsNotAlreadyInRepository() {
    ImplementationGuide ig = buildIg("hl7.fhir.us.core", "6.1.0");
    MadieValueSet newVs = buildValueSet("http://cts.nlm.nih.gov/fhir/ValueSet/new-vs", "20230401");
    MadieValueSet existingVs =
        buildValueSet("http://cts.nlm.nih.gov/fhir/ValueSet/existing-vs", "20220401");

    Map<String, Map<String, Set<MadieValueSet>>> igMap = new HashMap<>();
    igMap.put("hl7.fhir.us.core6.1.0", Map.of("sd.json", new HashSet<>(Set.of(newVs, existingVs))));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(ImplementationGuideLoader::load).thenReturn(List.of(ig));

      when(implementationGuideProcessor.collectValueSetDependencies(ig)).thenReturn(igMap);
      // Repository already contains existingVs but not newVs
      when(vseRepo.findAll()).thenReturn(List.of(existingVs));

      ValueSetExpansionService service =
          new ValueSetExpansionService(implementationGuideProcessor, vseRepo);
      service.updateValueSetDependencies();

      verify(vseRepo, times(1))
          .saveAll(
              argThat(
                  saved -> {
                    List<MadieValueSet> savedList =
                        new ArrayList<>((Collection<MadieValueSet>) saved);
                    return savedList.size() == 1
                        && savedList
                            .get(0)
                            .getUrl()
                            .equals("http://cts.nlm.nih.gov/fhir/ValueSet/new-vs");
                  }));
    }
  }

  @Test
  void updateValueSetDependenciesSavesNothingWhenAllValueSetsAlreadyExist() {
    ImplementationGuide ig = buildIg("hl7.fhir.us.core", "6.1.0");
    MadieValueSet vs = buildValueSet("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3", "20230401");

    Map<String, Map<String, Set<MadieValueSet>>> igMap = new HashMap<>();
    igMap.put("hl7.fhir.us.core6.1.0", Map.of("sd.json", new HashSet<>(Set.of(vs))));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(ImplementationGuideLoader::load).thenReturn(List.of(ig));

      when(implementationGuideProcessor.collectValueSetDependencies(ig)).thenReturn(igMap);
      when(vseRepo.findAll()).thenReturn(List.of(vs));

      ValueSetExpansionService service =
          new ValueSetExpansionService(implementationGuideProcessor, vseRepo);
      service.updateValueSetDependencies();

      verify(vseRepo, times(1))
          .saveAll(
              argThat(
                  saved -> {
                    List<MadieValueSet> savedList =
                        new ArrayList<>((Collection<MadieValueSet>) saved);
                    return savedList.isEmpty();
                  }));
    }
  }

  @Test
  void updateValueSetDependenciesSavesAllValueSetsWhenRepositoryIsEmpty() {
    ImplementationGuide ig = buildIg("hl7.fhir.us.core", "6.1.0");
    MadieValueSet vs1 = buildValueSet("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3", "20230401");
    MadieValueSet vs2 = buildValueSet("http://cts.nlm.nih.gov/fhir/ValueSet/4.5.6", null);

    Map<String, Map<String, Set<MadieValueSet>>> igMap = new HashMap<>();
    igMap.put("hl7.fhir.us.core6.1.0", Map.of("sd.json", new HashSet<>(Set.of(vs1, vs2))));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(ImplementationGuideLoader::load).thenReturn(List.of(ig));

      when(implementationGuideProcessor.collectValueSetDependencies(ig)).thenReturn(igMap);
      when(vseRepo.findAll()).thenReturn(Collections.emptyList());

      ValueSetExpansionService service =
          new ValueSetExpansionService(implementationGuideProcessor, vseRepo);
      service.updateValueSetDependencies();

      verify(vseRepo, times(1))
          .saveAll(
              argThat(
                  saved -> {
                    List<MadieValueSet> savedList =
                        new ArrayList<>((Collection<MadieValueSet>) saved);
                    return savedList.size() == 2;
                  }));
    }
  }

  @Test
  void updateValueSetDependenciesSavesNothingWhenNoIgsLoaded() {
    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(ImplementationGuideLoader::load).thenReturn(Collections.emptyList());
      when(vseRepo.findAll()).thenReturn(Collections.emptyList());

      ValueSetExpansionService service =
          new ValueSetExpansionService(implementationGuideProcessor, vseRepo);
      service.updateValueSetDependencies();

      // findAll is still called but saveAll receives an empty collection
      verify(vseRepo, times(1)).findAll();
      verify(vseRepo, times(1))
          .saveAll(
              argThat(
                  saved -> {
                    List<MadieValueSet> savedList =
                        new ArrayList<>((Collection<MadieValueSet>) saved);
                    return savedList.isEmpty();
                  }));
    }
  }

  @Test
  void updateValueSetDependenciesMatchesExistingByUrlAndVersionNullVersion() {
    ImplementationGuide ig = buildIg("hl7.fhir.us.core", "6.1.0");

    // Both incoming and existing have null version — should be treated as a match
    MadieValueSet incoming = buildValueSet("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3", null);
    MadieValueSet existing = buildValueSet("http://cts.nlm.nih.gov/fhir/ValueSet/1.2.3", null);

    Map<String, Map<String, Set<MadieValueSet>>> igMap = new HashMap<>();
    igMap.put("hl7.fhir.us.core6.1.0", Map.of("sd.json", new HashSet<>(Set.of(incoming))));

    try (MockedStatic<ImplementationGuideLoader> loader =
        mockStatic(ImplementationGuideLoader.class)) {
      loader.when(ImplementationGuideLoader::load).thenReturn(List.of(ig));

      when(implementationGuideProcessor.collectValueSetDependencies(ig)).thenReturn(igMap);
      when(vseRepo.findAll()).thenReturn(List.of(existing));

      ValueSetExpansionService service =
          new ValueSetExpansionService(implementationGuideProcessor, vseRepo);
      service.updateValueSetDependencies();

      // The incoming VS matches the existing one — nothing should be saved
      verify(vseRepo, times(1))
          .saveAll(
              argThat(
                  saved -> {
                    List<MadieValueSet> savedList =
                        new ArrayList<>((Collection<MadieValueSet>) saved);
                    return savedList.isEmpty();
                  }));
    }
  }
}
