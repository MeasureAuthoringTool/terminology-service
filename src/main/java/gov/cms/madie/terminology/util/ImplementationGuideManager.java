package gov.cms.madie.terminology.util;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.cql_elm_translator.utils.ImplementationGuideLoader;
import gov.cms.madie.terminology.models.MadieValueSet;
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

/**
 * Provides access to the MADiE implementation guide catalog and resolves ValueSet bindings for
 * given IG.
 *
 * <p>NPM packages are loaded on demand for one IG and are never retained, so package content (which
 * can be very large) can be garbage collected as soon as the IG has been processed.
 */
@Slf4j
@Component
public class ImplementationGuideManager {

  /** Lightweight catalog of IGs, keyed by "name|version". Holds no package content. */
  private final ConcurrentHashMap<String, ImplementationGuide> availableImplementationGuides =
      new ConcurrentHashMap<>();

  private final FhirContext ctx = FhirContext.forR4Cached();

  @Value("${madie.fhir-cache}")
  private String fhirCachePath;

  /** Returns a snapshot of all IGs in the catalog. */
  public List<ImplementationGuide> getImplementationGuides() {
    ensureIGCatalogLoaded();
    return new ArrayList<>(availableImplementationGuides.values());
  }

  /**
   * Returns the names of the IGs and their direct dependencies, derived from the catalog without
   * loading any packages.
   */
  public List<String> getImplementationGuideNames() {
    Set<String> igNames = new LinkedHashSet<>();
    for (ImplementationGuide ig : getImplementationGuides()) {
      igNames.add(ig.getName() + " v" + ig.getVersion());
      ig.getDependsOn().stream()
          .filter(ImplementationGuide.ImplementationGuideDependsOnComponent::hasPackageId)
          .forEach(dep -> igNames.add(dep.getPackageId() + " v" + dep.getVersion()));
    }
    return new ArrayList<>(igNames);
  }

  /**
   * Returns the ValueSet dependencies for the given MADiE IG, or for the given package when it is a
   * direct dependency of one of the MADiE IGs. Only one IG's packages are loaded at a time.
   */
  public List<MadieValueSet> getValueSetDependencies(String igName, String version) {
    Optional<ImplementationGuide> madieIg = findImplementationGuide(igName, version);
    if (madieIg.isPresent()) {
      return new ArrayList<>(collectValueSetDependencies(madieIg.get()));
    }

    List<ImplementationGuide> implementationGuides =
        getImplementationGuides().stream()
            .filter(
                ig -> ig.getDependsOn().stream().anyMatch(dep -> igName.equals(dep.getPackageId())))
            .toList();

    for (ImplementationGuide implementationGuide : implementationGuides) {
      Optional<Set<MadieValueSet>> packageValueSets =
          collectPackageValueSets(implementationGuide, igName, version);
      if (packageValueSets.isPresent()) {
        return new ArrayList<>(packageValueSets.get());
      }
    }

    log.info(
        "No implementation guide or package found matching name: {} and version: {}",
        igName,
        version);
    return Collections.emptyList();
  }

  /**
   * Returns the distinct ValueSet dependencies across all MADiE IGs. IGs are processed one at a
   * time; only the lightweight {@link MadieValueSet} references are accumulated.
   */
  public List<MadieValueSet> getValueSetDependencies() {
    Set<MadieValueSet> valueSets = new LinkedHashSet<>();
    for (ImplementationGuide ig : getImplementationGuides()) {
      valueSets.addAll(collectValueSetDependencies(ig));
    }
    return new ArrayList<>(valueSets);
  }

  /**
   * Loads the packages for a single IG, extracts the ValueSet bindings from every package and
   * returns them. No package content is retained after this method returns.
   */
  public Set<MadieValueSet> collectValueSetDependencies(ImplementationGuide ig) {
    if (ig == null || !ig.hasDependsOn()) {
      return new LinkedHashSet<>();
    }
    log.info("Collecting ValueSet dependencies for IG {} v{}", ig.getName(), ig.getVersion());
    Instant start = Instant.now();
    Set<MadieValueSet> valueSets = new LinkedHashSet<>();
    try {
      for (NpmPackage igPkg : getIgNpmPackages(ig)) {
        valueSets.addAll(getBindingValueSets(igPkg));
      }
    } catch (IOException e) {
      log.error("Unable to load packages for IG: {}", ig.getName(), e);
      return new LinkedHashSet<>();
    }
    log.info(
        "Collected {} ValueSet dependencies for IG {} v{} in {} milliseconds.",
        valueSets.size(),
        ig.getName(),
        ig.getVersion(),
        Duration.between(start, Instant.now()).toMillis());
    return valueSets;
  }

  public Optional<ImplementationGuide> findImplementationGuide(String igName, String version) {
    ensureIGCatalogLoaded();
    return Optional.ofNullable(availableImplementationGuides.get(buildIgKey(igName, version)));
  }

  private Optional<Set<MadieValueSet>> collectPackageValueSets(
      ImplementationGuide ig, String pkgName, String pkgVersion) {
    try {
      for (NpmPackage igPkg : getIgNpmPackages(ig)) {
        if (pkgName.equals(igPkg.name()) && pkgVersion.equals(igPkg.version())) {
          return Optional.of(getBindingValueSets(igPkg));
        }
      }
    } catch (IOException e) {
      log.error("Unable to load packages for IG: {}", ig.getName(), e);
    }
    return Optional.empty();
  }

  private Set<MadieValueSet> getBindingValueSets(NpmPackage igPkg) throws IOException {
    List<String> structureDefFiles = getStructureDefinitionFilesForIg(igPkg);
    if (structureDefFiles.isEmpty()) {
      return new LinkedHashSet<>();
    }
    Set<MadieValueSet> packageValueSets = new LinkedHashSet<>();
    for (String sdFile : structureDefFiles) {
      try (InputStream sdStream = igPkg.load("package", sdFile)) {
        // Parse the StructureDefinition from JSON
        StructureDefinition structureDefinition =
            ctx.newJsonParser().parseResource(StructureDefinition.class, sdStream);
        log.debug("    - {} ({})", structureDefinition.getName(), structureDefinition.getUrl());

        // Check each Element for ValueSet bindings
        for (ElementDefinition element : structureDefinition.getSnapshot().getElement()) {
          if (element.hasBinding() && element.getBinding().hasValueSet()) {
            packageValueSets.add(buildMadieValueSet(element));
          }
        }
      }
    }
    return packageValueSets;
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
    log.info("Loaded {} packages for IG {} v{}", igPackages.size(), ig.getName(), ig.getVersion());
    return igPackages;
  }

  private synchronized void ensureIGCatalogLoaded() {
    if (!availableImplementationGuides.isEmpty()) {
      return;
    }

    List<ImplementationGuide> madieIgs = ImplementationGuideLoader.load("classpath*:igs/*.json");
    madieIgs.forEach(
        ig -> availableImplementationGuides.put(buildIgKey(ig.getName(), ig.getVersion()), ig));
  }

  private String buildIgKey(String igName, String version) {
    return igName + "|" + version;
  }
}
