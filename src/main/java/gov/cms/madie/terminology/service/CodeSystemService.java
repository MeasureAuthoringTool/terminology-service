package gov.cms.madie.terminology.service;

import gov.cms.madie.terminology.exceptions.CodeSystemNotFoundException;
import gov.cms.madie.terminology.exceptions.DuplicateCodeSystemException;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CodeSystemService {
  private final CodeSystemRepository codeSystemRepository;

  public List<CodeSystem> getAllCodeSystems() {
    return codeSystemRepository.findAll().stream().filter(CodeSystem::isInVsac).distinct().toList();
  }

  public Page<CodeSystem> getCodeSystems(Pageable pageable, String filterField, String searchText) {
    if (StringUtils.isBlank(searchText)) {
      return codeSystemRepository.findAll(pageable);
    }

    if (filterField == null) {
      return codeSystemRepository.findAllByAnyFieldContainingIgnoreCase(searchText, pageable);
    }

    return switch (StringUtils.deleteWhitespace(StringUtils.lowerCase(filterField))) {
      case "title" -> codeSystemRepository.findAllByTitleContainingIgnoreCase(searchText, pageable);
      case "name" -> codeSystemRepository.findAllByNameContainingIgnoreCase(searchText, pageable);
      case "version" ->
          codeSystemRepository.findAllByVersionFhirVersionContainingIgnoreCase(
              searchText, pageable);
      case "fullurl" ->
          codeSystemRepository.findAllByFullUrlContainingIgnoreCase(searchText, pageable);
      default -> codeSystemRepository.findAllByAnyFieldContainingIgnoreCase(searchText, pageable);
    };
  }

  public CodeSystem createCodeSystem(CodeSystem codeSystem) {
    var existingCodeSystem =
        codeSystemRepository.findByFullUrlAndVersionFhirVersion(
            codeSystem.getFullUrl(), codeSystem.getVersion().getFhirVersion());
    if (existingCodeSystem.isPresent()) {
      log.warn(
          "Duplicate CodeSystem — url: [{}] fhir version: [{}] already exists",
          codeSystem.getFullUrl(),
          codeSystem.getVersion().getFhirVersion());
      throw new DuplicateCodeSystemException(
          "CodeSystem with url ["
              + codeSystem.getFullUrl()
              + "] and fhir version ["
              + codeSystem.getVersion().getFhirVersion()
              + "] already exists");
    }

    codeSystem.setLastUpdated(Instant.now());
    if (codeSystem.isLatestVersion()) {
      demoteAllLatestVersionsByUrl(codeSystem.getFullUrl());
    }

    CodeSystem saved = codeSystemRepository.save(codeSystem);
    log.info("New CodeSystem created by admin: {}", saved);
    return saved;
  }

  public CodeSystem updateCodeSystem(String id, CodeSystem codeSystem) {
    CodeSystem existing =
        codeSystemRepository
            .findById(id)
            .orElseThrow(
                () -> {
                  log.warn("CodeSystem not found for update — id: [{}]", id);
                  return new CodeSystemNotFoundException("CodeSystem not found for id: " + id);
                });
    existing.setFullUrl(codeSystem.getFullUrl());
    existing.setInVsac(codeSystem.isInVsac());

    if (codeSystem.getTitle() != null) {
      existing.setTitle(codeSystem.getTitle());
    }

    existing.setName(codeSystem.getName());
    existing.getVersion().setFhirVersion(codeSystem.getVersion().getFhirVersion());
    if (codeSystem.getVersion().getVsacVersion() != null) {
      existing.getVersion().setVsacVersion(codeSystem.getVersion().getVsacVersion());
    }
    if (codeSystem.getVersionId() != null) {
      existing.setVersionId(codeSystem.getVersionId());
    }

    existing.setOid(codeSystem.getOid());
    existing.setLastUpdated(Instant.now());
    if (codeSystem.getLastUpdatedUpstream() != null) {
      existing.setLastUpdatedUpstream(codeSystem.getLastUpdatedUpstream());
    }

    if (codeSystem.isLatestVersion()) {
      promoteToLatestVersion(existing);
    } else {
      existing.setLatestVersion(false);
    }

    CodeSystem updated = codeSystemRepository.save(existing);
    log.info("CodeSystem updated by admin: {}", updated);
    return updated;
  }

  private void promoteToLatestVersion(CodeSystem codeSystem) {
    demoteAllLatestVersionsByUrl(codeSystem.getFullUrl());
    codeSystem.setLatestVersion(true);
    log.info("CodeSystem promoted to latest version: {}", codeSystem);
  }

  private void demoteAllLatestVersionsByUrl(String url) {
    List<CodeSystem> existingCodeSystems = codeSystemRepository.findAllByFullUrl(url);
    if (!CollectionUtils.isEmpty(existingCodeSystems)) {
      existingCodeSystems.forEach(
          existingCodeSystem -> {
            if (existingCodeSystem.isLatestVersion()) {
              log.info(
                  "Demoting existing CodeSystem id: [{}] url: [{}] fhirVersion: [{}]"
                      + " - setting its isLatestVersion from true to false",
                  existingCodeSystem.getId(),
                  existingCodeSystem.getFullUrl(),
                  existingCodeSystem.getVersion().getFhirVersion());
              existingCodeSystem.setLatestVersion(false);
            }
          });
      codeSystemRepository.saveAll(existingCodeSystems);
    }
  }

  public CodeSystem deleteCodeSystem(String id) {
    CodeSystem existing =
        codeSystemRepository
            .findById(id)
            .orElseThrow(
                () -> {
                  log.warn("CodeSystem not found for delete — id: [{}]", id);
                  return new CodeSystemNotFoundException("CodeSystem not found for id: " + id);
                });
    codeSystemRepository.deleteById(id);
    log.info("CodeSystem deleted by admin, id: {}", id);
    return existing;
  }
}
