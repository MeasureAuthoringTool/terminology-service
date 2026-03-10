package gov.cms.madie.terminology.config.migrations;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.models.mapping.CodeSystemEntry;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.task.UpdateCodeSystemTask;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.File;
import java.io.IOException;
import java.util.*;

@ChangeUnit(id = "load-code-system-mapping-from-doc", order = "1", author = "madie-dev")
@Slf4j
public class LoadCodeSystemMappingData {

  private final String CODE_SYSTEM_ENTRY_FILE_PATH = "src/main/resources/code-system-entry.json";
  private final List<CodeSystem> originalCodeSystems = new ArrayList<>();

  @Execution
  public void apply(
      MongoTemplate mongoTemplate,
      CodeSystemRepository codeSystemRepository,
      ObjectMapper objectMapper,
      UpdateCodeSystemTask updateCodeSystemTask) {

    // Drop the existing codeSystem collection to allow for model changes.
    mongoTemplate.dropCollection("codeSystem");

    // Run the Code System refresh task to update the existing CS entries with the updated model.
    updateCodeSystemTask.updateCodeSystems();

    List<CodeSystem> codeSystems = codeSystemRepository.findAll();

    // Store a copy of the original code system for potential rollback
    codeSystems.forEach(
        codeSystem -> {
          // Duplication with Builder is sufficient since all fields in CodeSystem
          // are either primitives or immutable (String, boolean, etc.)
          originalCodeSystems.add(codeSystem.toBuilder().build());
        });

    List<CodeSystemEntry> codeSystemEntries = deserializeFromFile(objectMapper);

    for (CodeSystemEntry entry : codeSystemEntries) {
      boolean isLastestVersion = true; // First version in the list is the latest.
      for (CodeSystemEntry.Version version : entry.getVersions()) {
        Optional<CodeSystem> newCsVersion = Optional.empty();
        Optional<CodeSystem> existingCsVersion = Optional.empty();

        // If FHIR, check for existing Code System.
        if (StringUtils.isNotBlank(version.getFhir())) {
          // Update Existing Code System if FHIR version matches any existing CS.
          existingCsVersion =
              codeSystems.stream()
                  .filter(
                      cs ->
                          Objects.equals(cs.getFullUrl(), entry.getUrl())
                              && Objects.equals(
                                  cs.getVersion().getFhirVersion(), version.getFhir()))
                  .findFirst();
          if (existingCsVersion.isPresent()) {
            existingCsVersion.get().setLatestVersion(isLastestVersion);
            existingCsVersion.get().getVersion().setVsacVersion(version.getVsac());
          }
        }

        // New Code System
        if (existingCsVersion.isEmpty()) {
          newCsVersion = Optional.of(new CodeSystem());
          newCsVersion.get().setOid(entry.getOid());
          newCsVersion.get().setName(entry.getName());
          newCsVersion.get().setFullUrl(entry.getUrl());
          newCsVersion.get().setLatestVersion(isLastestVersion);
          newCsVersion
              .get()
              .setVersion(
                  CodeSystem.Version.builder()
                      .fhirVersion(version.getFhir())
                      .vsacVersion(version.getVsac())
                      .build());
        }

        existingCsVersion.ifPresent(codeSystemRepository::save);
        newCsVersion.ifPresent(codeSystemRepository::save);
        isLastestVersion = false; // First version in the list is the latest.
      }
    }
  }

  @RollbackExecution
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
