package gov.cms.madie.terminology.config.migrations;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.models.mapping.CodeSystemEntry;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.File;
import java.io.IOException;
import java.util.*;

@TargetSystem(id = "terminology")
@Change(id = "load-code-system-mapping-from-doc", author = "madie-dev", transactional = false)
@Slf4j
public class _0001__LoadCodeSystemMappingData {

  private final String CODE_SYSTEM_ENTRY_FILE_PATH = "src/main/resources/code-system-entry.json";
  private final List<CodeSystem> originalCodeSystems = new ArrayList<>();

  @Apply
  public void apply(
      MongoTemplate mongoTemplate,
      CodeSystemRepository codeSystemRepository,
      ObjectMapper objectMapper) {
    List<CodeSystem> codeSystems = codeSystemRepository.findAll();

    // Store a copy of the original code system for potential rollback
    codeSystems.forEach(
        codeSystem -> {
          // Builder is sufficient since all fields in CodeSystem are either primitives
          // or immutable objects (String, boolean, etc.)
          originalCodeSystems.add(codeSystem.toBuilder().build());
        });

    // All existing documents are FHIR Value Sets, set fhir to true.
    codeSystems.forEach(codeSystem -> codeSystem.setFhir(true));
    codeSystemRepository.saveAll(codeSystems);

    List<CodeSystemEntry> codeSystemEntries = deserializeFromFile(objectMapper);

    for (CodeSystemEntry entry : codeSystemEntries) {
      boolean isLastestVersion = true;
      for (CodeSystemEntry.Version version : entry.getVersions()) {
        Optional<CodeSystem> newFhirCodeSystem = Optional.empty();
        Optional<CodeSystem> newQdmCodeSystem = Optional.empty();
        Optional<CodeSystem> existingCodeSystem = Optional.empty();

        // FHIR Version
        if (StringUtils.isNotBlank(version.getFhir())) {
          // Update Existing Code System if FHIR version matches any existing.
          // Assumption: Code Systems already in the DB are searchable in VSAC.
          existingCodeSystem =
              codeSystems.stream()
                  .filter(
                      cs ->
                          Objects.equals(cs.getFullUrl(), entry.getUrl())
                              && Objects.equals(cs.getVersion(), version.getFhir()))
                  .findFirst();
          // Add new Code System if FHIR version does not match any existing.
          if (existingCodeSystem.isEmpty()) {
            newFhirCodeSystem = Optional.of(new CodeSystem());
            // ID is a composite of Title and Version, but the mapping document
            // does not store the title, so using name instead.
            newFhirCodeSystem.get().setId(entry.getName() + version.getFhir());
            newFhirCodeSystem.get().setOid(entry.getOid());
            newFhirCodeSystem.get().setName(entry.getName());
            newFhirCodeSystem.get().setFullUrl(entry.getUrl());
            newFhirCodeSystem.get().setFhir(true);
            newFhirCodeSystem.get().setLatestVersion(isLastestVersion);
            newFhirCodeSystem.get().setVersion(version.getFhir());
          } else {
            existingCodeSystem.get().setLatestVersion(isLastestVersion);
            existingCodeSystem.get().setVsacSearchable(true);
          }
        }

        // QDM Version
        if (StringUtils.isNotBlank(version.getVsac())) {
          // If FHIR and QDM versions match, update the existing CS or the new FHIR CS.
          if (version.getVsac().equals(version.getFhir())) {
            existingCodeSystem.ifPresent(
                existing -> {
                  existing.setQdm(true);
                  existing.setVsacSearchable(true);
                  existing.setQdmDisplayVersion(version.getVsac());
                });
            newFhirCodeSystem.ifPresent(
                newFhirCs -> {
                  newFhirCs.setQdm(true);
                  newFhirCs.setVsacSearchable(true);
                  newFhirCs.setQdmDisplayVersion(version.getVsac());
                });
          } else { // unique qdm version
            newQdmCodeSystem = Optional.of(new CodeSystem());
            newQdmCodeSystem.get().setId(entry.getName() + version.getVsac());
            newQdmCodeSystem.get().setOid(entry.getOid());
            newQdmCodeSystem.get().setName(entry.getName());
            newQdmCodeSystem.get().setVersion(version.getVsac());
            newQdmCodeSystem.get().setQdmDisplayVersion(version.getVsac());
            newQdmCodeSystem.get().setQdm(true);
            newQdmCodeSystem.get().setVsacSearchable(true);
            newQdmCodeSystem.get().setLatestVersion(isLastestVersion);
          }
        }

        existingCodeSystem.ifPresent(codeSystemRepository::save);
        newFhirCodeSystem.ifPresent(codeSystemRepository::save);
        newQdmCodeSystem.ifPresent(codeSystemRepository::save);
        isLastestVersion = false; // First version in the list is the latest.
      }
    }
  }

  @Rollback
  public void rollback(MongoTemplate mongoTemplate, CodeSystemRepository codeSystemRepository) {
    if (CollectionUtils.isEmpty(originalCodeSystems)) {
      log.warn("No original code systems found for rollback. Skipping rollback.");
      return;
    }
    codeSystemRepository.saveAll(originalCodeSystems);
  }

  /**
   * Deserialize code-system-entry.json from a file path into a List of CodeSystemEntry objects.
   *
   * @param objectMapper the ObjectMapper to use for deserialization. Exposed as a parameter for
   *     easier testing.
   * @return a List of CodeSystemEntry objects, or an empty list if deserialization fails
   */
  private List<CodeSystemEntry> deserializeFromFile(ObjectMapper objectMapper) {
    try {
      CodeSystemEntry[] entries =
          objectMapper.readValue(new File(CODE_SYSTEM_ENTRY_FILE_PATH), CodeSystemEntry[].class);
      if (entries != null) {
        log.info(
            "Successfully deserialized {} CodeSystemEntry objects from file: {}",
            entries.length,
            CODE_SYSTEM_ENTRY_FILE_PATH);
        return Arrays.asList(entries);
      }
    } catch (IOException e) {
      log.error(
          "Error deserializing CodeSystemEntry from file: {}", CODE_SYSTEM_ENTRY_FILE_PATH, e);
    }
    return Collections.emptyList();
  }
}
