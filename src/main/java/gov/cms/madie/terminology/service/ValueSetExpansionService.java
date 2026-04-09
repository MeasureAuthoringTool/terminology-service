package gov.cms.madie.terminology.service;

import gov.cms.madie.cql_elm_translator.utils.ImplementationGuideLoader;
import gov.cms.madie.terminology.models.MadieValueSet;
import gov.cms.madie.terminology.repositories.ValueSetExpansionRepository;
import gov.cms.madie.terminology.util.ImplementationGuideProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ValueSetExpansionService {

  private final List<ImplementationGuide> parentIgs = ImplementationGuideLoader.load();
  private final ImplementationGuideProcessor implementationGuideProcessor;
  private final ValueSetExpansionRepository vseRepo;

  public Set<MadieValueSet> getValueSetDependencies(String igName, String version) {
    if (CollectionUtils.isEmpty(parentIgs)) {
      log.info("No implementation guides found.");
      return Collections.emptySet();
    }

    ImplementationGuide parentIg =
        parentIgs.stream()
            .filter(
                guide ->
                    guide.getName().equalsIgnoreCase(igName)
                        && guide.getVersion().equalsIgnoreCase(version))
            .findFirst()
            .orElse(null);

    return implementationGuideProcessor.collectValueSetDependencies(parentIg).entrySet().stream()
        .flatMap(pkgEntry -> pkgEntry.getValue().entrySet().stream())
        .flatMap(sdEntry -> sdEntry.getValue().stream())
        .collect(Collectors.toSet());
  }

  public Map<String, Map<String, Set<MadieValueSet>>> getValueSetDependencies() {
    if (CollectionUtils.isEmpty(parentIgs)) {
      return new HashMap<>();
    }
    Map<String, Map<String, Set<MadieValueSet>>> valueSetDependencies = new HashMap<>();
    for (ImplementationGuide ig : parentIgs) {
      valueSetDependencies.putAll(implementationGuideProcessor.collectValueSetDependencies(ig));
    }
    return valueSetDependencies;
  }

  public void updateValueSetDependencies() {
    Set<MadieValueSet> madieValueSets =
        getValueSetDependencies().entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() != null)
            .flatMap(igEntry -> igEntry.getValue().entrySet().stream())
            .flatMap(sdEntry -> sdEntry.getValue().stream())
            .collect(Collectors.toSet());

    List<MadieValueSet> existingValueSets = vseRepo.findAll();

    log.info("Found {} value set dependencies.", madieValueSets.size());

    madieValueSets.removeIf(
        vs ->
            existingValueSets.stream()
                .anyMatch(
                    existingVs ->
                        existingVs.getUrl().equals(vs.getUrl())
                            && Objects.equals(existingVs.getVersion(), vs.getVersion())));

    log.info("Saving {} new value set dependencies.", madieValueSets.size());

    vseRepo.saveAll(madieValueSets);
  }
}
