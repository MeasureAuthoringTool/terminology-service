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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ImplementationGuideProcessor {

  private final ConcurrentHashMap<String, Map<String, Set<MadieValueSet>>> igValueSets =
      new ConcurrentHashMap<>();
  private final FhirContext ctx = FhirContext.forR4Cached();

  @Value("${madie.fhir-cache}")
  private String fhirCachePath;

  public Map<String, Map<String, Set<MadieValueSet>>> collectValueSetDependencies(
      ImplementationGuide ig) {
    if (ig == null || !ig.hasDependsOn()) {
      return new HashMap<>();
    }

    try {
      List<NpmPackage> igPackages = getIgNpmPackages(ig);
      for (NpmPackage igPkg : igPackages) {
        String igPkgKey = igPkg.name() + igPkg.version();
        if (igValueSets.containsKey(igPkgKey)) {
          log.info("IG already parsed: {} v{}", igPkg.name(), igPkg.version());
          continue;
        }
        igValueSets.put(igPkgKey, getBindingValueSets(igPkg));
      }
    } catch (IOException e) {
      log.error("Unable to load ig package: {}", ig.getName(), e);
    }
    return igValueSets;
  }

  private Map<String, Set<MadieValueSet>> getBindingValueSets(NpmPackage igPkg) throws IOException {
    List<String> structureDefFiles = getStructureDefinitionFilesForIg(igPkg);
    if (structureDefFiles.isEmpty()) {
      return new HashMap<>();
    }
    Map<String, Set<MadieValueSet>> sdValueSets = new HashMap<>();
    for (String structureDefinition : structureDefFiles) {
      try (InputStream sdStream = igPkg.load("package", structureDefinition)) {
        Set<MadieValueSet> valueSetDependencies = new HashSet<>();

        // Parse the StructureDefinition from JSON
        StructureDefinition sd =
            ctx.newJsonParser().parseResource(StructureDefinition.class, sdStream);
        log.debug("    - {} ({})", sd.getName(), sd.getUrl());

        // Check each Element for ValueSet bindings
        for (ElementDefinition element : sd.getSnapshot().getElement()) {
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
    log.info(
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
