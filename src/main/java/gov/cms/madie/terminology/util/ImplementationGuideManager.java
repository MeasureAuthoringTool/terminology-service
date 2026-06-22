package gov.cms.madie.terminology.util;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.cql_elm_translator.utils.ImplementationGuideLoader;
import gov.cms.madie.terminology.models.MadieValueSet;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cqframework.fhir.npm.NpmPackageManager;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ImplementationGuideManager {

  @Getter
  private final ConcurrentHashMap<
          ImplementationGuide, Map<NpmPackage, Map<StructureDefinition, Set<MadieValueSet>>>>
      igHierarchicalValueSets = new ConcurrentHashMap<>();

  private final FhirContext ctx = FhirContext.forR4Cached();

  @Value("${madie.fhir-cache}")
  private String fhirCachePath;

  private IgLoadingState igLoadingState = IgLoadingState.NOT_LOADED;

  public enum IgLoadingState {
    NOT_LOADED,
    LOADING,
    LOADED,
    ERROR_FAILED
  }

  private void loadImplementationGuides() {
    log.info("Implementation guide manager::loading implementation guide packages.");
    Instant now = Instant.now();
    igLoadingState = IgLoadingState.LOADING;
    List<ImplementationGuide> madieIgs = ImplementationGuideLoader.load("classpath*:igs/*.json");
    for (ImplementationGuide madieIg : madieIgs) {
      collectValueSetDependencies(madieIg);
    }
    igLoadingState = IgLoadingState.LOADED;
    log.info(
        "Implementation guide manager::IG loading complete in {} milliseconds.",
        Duration.between(now, Instant.now()).toMillis());
  }

  private IgLoadingState igsLoading() {
    if (igLoadingState == IgLoadingState.NOT_LOADED) {
      loadImplementationGuides();
    }
    return igLoadingState;
  }

  public List<String> getImplementationGuides() {
    List<String> igNames =
        new ArrayList<>(
            igHierarchicalValueSets.keySet().stream()
                .map(ig -> ig.getName() + " v" + ig.getVersion())
                .toList());
    igNames.addAll(
        igHierarchicalValueSets.values().stream()
            .flatMap(pkgMap -> pkgMap.keySet().stream())
            .map(pkg -> pkg.name() + " v" + pkg.version())
            .toList());
    return igNames;
  }

  public List<MadieValueSet> getValueSetDependencies(String igName, String version) {
    if (igsLoading() != IgLoadingState.LOADED) {
      return Collections.emptyList();
    }

    // First, check if igName/version are madie Igs
    Optional<ImplementationGuide> implementationGuide =
        igHierarchicalValueSets.keySet().stream()
            .filter(ig -> igName.equals(ig.getName()) && version.equals(ig.getVersion()))
            .findFirst();
    if (implementationGuide.isPresent()) {
      return igHierarchicalValueSets.get(implementationGuide.get()).values().stream()
          .flatMap(structureDefMap -> structureDefMap.values().stream())
          .flatMap(Set::stream)
          .distinct()
          .toList();
    }

    // Next, check if igName/version are in any of the packages of the madie Igs
    Optional<Map<NpmPackage, Map<StructureDefinition, Set<MadieValueSet>>>> packageMapOpt =
        igHierarchicalValueSets.values().stream()
            .filter(
                pkgMap ->
                    pkgMap.keySet().stream()
                        .anyMatch(
                            pkg -> igName.equals(pkg.name()) && version.equals(pkg.version())))
            .findFirst();

    if (packageMapOpt.isPresent()) {
      Map<NpmPackage, Map<StructureDefinition, Set<MadieValueSet>>> packageMap =
          packageMapOpt.get();
      return packageMap.entrySet().stream()
          .filter(
              entry ->
                  igName.equals(entry.getKey().name()) && version.equals(entry.getKey().version()))
          .flatMap(entry -> entry.getValue().values().stream())
          .flatMap(Set::stream)
          .distinct()
          .toList();
    }

    log.info(
        "No implementation guide or package found matching name: {} and version: {}",
        igName,
        version);
    return Collections.emptyList();
  }

  public List<MadieValueSet> getValueSetDependencies() {
    if (igsLoading() != IgLoadingState.LOADED) {
      return Collections.emptyList();
    }
    return igHierarchicalValueSets.values().stream()
        .flatMap(pkgMap -> pkgMap.values().stream())
        .flatMap(structureDefMap -> structureDefMap.values().stream())
        .flatMap(Set::stream)
        .distinct()
        .toList();
  }

  public Map<ImplementationGuide, Map<NpmPackage, Map<StructureDefinition, Set<MadieValueSet>>>>
      collectValueSetDependencies(ImplementationGuide ig) {
    if (ig == null || !ig.hasDependsOn()) {
      return new HashMap<>();
    }
    if (igHierarchicalValueSets.containsKey(ig)) {
      log.info("MADiE IG already parsed: {} v{}", ig.getName(), ig.getVersion());
      return igHierarchicalValueSets;
    } else {
      igHierarchicalValueSets.put(ig, new HashMap<>());
    }

    Map<NpmPackage, Map<StructureDefinition, Set<MadieValueSet>>> packageMap =
        igHierarchicalValueSets.get(ig);

    try {
      List<NpmPackage> igPackages = getIgNpmPackages(ig);
      for (NpmPackage igPkg : igPackages) {
        if (packageMap.containsKey(igPkg)) {
          log.info("IG already parsed: {} v{}", igPkg.name(), igPkg.version());
          continue;
        }
        packageMap.put(igPkg, getBindingValueSets(igPkg));
      }
    } catch (IOException e) {
      log.error("Unable to load packages for IG: {}", ig.getName(), e);
      return new HashMap<>();
    }
    igHierarchicalValueSets.put(ig, packageMap);
    return igHierarchicalValueSets;
  }

  private Map<StructureDefinition, Set<MadieValueSet>> getBindingValueSets(NpmPackage igPkg)
      throws IOException {
    List<String> structureDefFiles = getStructureDefinitionFilesForIg(igPkg);
    if (structureDefFiles.isEmpty()) {
      return new HashMap<>();
    }
    Map<StructureDefinition, Set<MadieValueSet>> sdValueSets = new HashMap<>();
    for (String sdFile : structureDefFiles) {
      try (InputStream sdStream = igPkg.load("package", sdFile)) {
        Set<MadieValueSet> valueSetDependencies = new HashSet<>();

        // Parse the StructureDefinition from JSON
        StructureDefinition structureDefinition =
            ctx.newJsonParser().parseResource(StructureDefinition.class, sdStream);
        log.debug("    - {} ({})", structureDefinition.getName(), structureDefinition.getUrl());

        // Check each Element for ValueSet bindings
        for (ElementDefinition element : structureDefinition.getSnapshot().getElement()) {
          if (element.hasBinding() && element.getBinding().hasValueSet()) {
            MadieValueSet madieValueSet = buildMadieValueSet(element);
            valueSetDependencies.add(madieValueSet);
          }
        }
        sdValueSets.put(structureDefinition, valueSetDependencies);
      }
    }
    return sdValueSets;
  }

  private @NonNull MadieValueSet buildMadieValueSet(@NonNull ElementDefinition element) {
    String valueSetRef = element.getBinding().getValueSet();
    log.debug("      * Element: {} binds to ValueSet: {}", element.getPath(), valueSetRef);
    MadieValueSet madieValueSet = new MadieValueSet();
    if (valueSetRef.contains("|")) {
      String[] parts = valueSetRef.split("\\|");
      madieValueSet.setUrl(parts[0]);
      madieValueSet.setVersion(parts[1]);
    } else {
      madieValueSet.setUrl(valueSetRef);
    }
    return madieValueSet;
  }

  private List<String> getStructureDefinitionFilesForIg(NpmPackage igPkg) throws IOException {
    if (igPkg == null) {
      return Collections.emptyList();
    }
    log.debug(
        "Parsing Structure Definitions from IG Package: {} v{}", igPkg.name(), igPkg.version());

    // List all StructureDefinition resources in the package
    List<String> structureDefFiles = igPkg.listResources("StructureDefinition");
    log.debug("  Found {} StructureDefinitions", structureDefFiles.size());
    return structureDefFiles;
  }

  private @NonNull List<NpmPackage> getIgNpmPackages(ImplementationGuide ig) throws IOException {
    NpmPackageManager packageManager =
        ImplementationGuideLoader.buildPackageManager(fhirCachePath, ig);
    // Access the downloaded NpmPackages
    List<NpmPackage> igPackages = packageManager.getNpmList().stream().distinct().toList();
    log.info("Loaded {} packages", igPackages.size());
    return igPackages;
  }
}
