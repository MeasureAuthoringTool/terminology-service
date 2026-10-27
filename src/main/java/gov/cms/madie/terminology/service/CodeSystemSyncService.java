package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.webclient.FhirTerminologyServiceWebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.Comparator.*;
import static java.util.stream.Collectors.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class CodeSystemSyncService {
  private final FhirContext fhirContext;
  private final FhirTerminologyServiceWebClient fhirTerminologyServiceWebClient;
  private final CodeSystemService codeSystemService;
  private final CodeSystemRepository codeSystemRepository;

  public List<CodeSystem> remoteRetrieveAllCodeSystems(UmlsUser umlsUser) {
    List<CodeSystem> allCodeSystems = new ArrayList<>();
    recursiveRemoteRetrieveCodeSystems(umlsUser, 0, 50, allCodeSystems);
    markLatestVersion(allCodeSystems);
    updateOrInsertRetrievedCodeSystems(allCodeSystems);
    return allCodeSystems;
  }

  private void recursiveRemoteRetrieveCodeSystems(
      UmlsUser umlsUser, Integer offset, Integer count, List<CodeSystem> allCodeSystems) {
    log.info("requesting page offset: {} count: {}", offset, count);
    Bundle codeSystemBundle = retrieveCodeSystemsPage(umlsUser, offset, count);
    List<CodeSystem> codeSystemsPage = new ArrayList<>();
    codeSystemBundle
        .getEntry()
        .forEach(
            entry -> {
              var codeSystem = (org.hl7.fhir.r4.model.CodeSystem) entry.getResource();
              codeSystemsPage.add(
                  CodeSystem.builder()
                      .fullUrl(codeSystem.getUrl())
                      .title(codeSystem.getTitle())
                      .name(codeSystem.getName())
                      .version(
                          CodeSystem.Version.builder().fhirVersion(codeSystem.getVersion()).build())
                      .versionId(codeSystem.getMeta().getVersionId())
                      .oid(parseOidFromIdentifier(codeSystem.getIdentifier()))
                      .isInVsac(true)
                      .lastUpdated(Instant.now())
                      .lastUpdatedUpstream(codeSystem.getMeta().getLastUpdated())
                      .build());
            });
    allCodeSystems.addAll(codeSystemsPage);
    var links = codeSystemBundle.getLink();
    links.forEach(
        link -> {
          if (link.getRelation().equals("next")) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(link.getUrl());
            String newOffset = builder.build().getQueryParams().getFirst("_offset");
            String newCount = builder.build().getQueryParams().getFirst("_count");
            assert newOffset != null;
            assert newCount != null;
            recursiveRemoteRetrieveCodeSystems(
                umlsUser, Integer.parseInt(newOffset), Integer.parseInt(newCount), allCodeSystems);
          }
        });
  }

  private String parseOidFromIdentifier(List<Identifier> identifiers) {
    for (Identifier identifier : identifiers) {
      if (identifier.getValue() != null && !identifier.getValue().isEmpty()) {
        // HCPCS has two OIDs; MADiE uses Level 2, which contains CMS custom codes.
        if (Strings.CI.equals(
            StringUtils.deleteWhitespace(identifier.getValue()),
            "urn:oid:2.16.840.1.113883.6.14,2.16.840.1.113883.6.285")) {
          return "urn:oid:2.16.840.1.113883.6.285";
        }
        return identifier.getValue();
      }
    }
    return "";
  }

  private Bundle retrieveCodeSystemsPage(UmlsUser umlsUser, Integer offset, Integer count) {
    IParser parser = fhirContext.newJsonParser();
    String responseString =
        fhirTerminologyServiceWebClient.getCodeSystemsPage(offset, count, umlsUser.getApiKey());
    return parser.parseResource(Bundle.class, responseString);
  }

  private void markLatestVersion(List<CodeSystem> allCodeSystems) {
    // Group by fullUrl and find the latest version for each group
    allCodeSystems.stream()
        .filter(
            cs -> Objects.nonNull(cs.getFullUrl()) && Objects.nonNull(cs.getLastUpdatedUpstream()))
        .collect(
            groupingBy(
                CodeSystem::getFullUrl, maxBy(comparing(CodeSystem::getLastUpdatedUpstream))))
        .values()
        .forEach(
            optionalCodeSystem -> {
              if (optionalCodeSystem.isPresent() && !optionalCodeSystem.get().isLatestVersion()) {
                CodeSystem latestCodeSystem = optionalCodeSystem.get();
                latestCodeSystem.setLatestVersion(true);
                log.info(
                    "Setting new latest version for CodeSystem with fullUrl: {} and version: {}",
                    latestCodeSystem.getFullUrl(),
                    latestCodeSystem.getVersion().getFhirVersion());
              }
            });
  }

  private void updateOrInsertRetrievedCodeSystems(List<CodeSystem> codeSystemList) {
    for (CodeSystem codeSystem : codeSystemList) {
      // Skip any CodeSystem that is missing a FHIR version or fullUrl.
      if (codeSystem.getVersion() == null
          || StringUtils.isBlank(codeSystem.getVersion().getFhirVersion())
          || Strings.CI.equals(codeSystem.getVersion().getFhirVersion(), "null")) {
        log.warn("Skipping CodeSystem with missing FHIR version: {}", codeSystem);
        continue;
      }
      if (StringUtils.isBlank(codeSystem.getFullUrl())
          || Strings.CI.equals(codeSystem.getFullUrl(), "null")) {
        log.warn("Skipping CodeSystem with missing fullUrl: {}", codeSystem);
        continue;
      }
      try {
        Optional<CodeSystem> existingCodeSystemOptional =
            codeSystemRepository.findByFullUrlAndVersionFhirVersion(
                codeSystem.getFullUrl(), codeSystem.getVersion().getFhirVersion());
        CodeSystem saved;
        if (existingCodeSystemOptional.isEmpty()) {
          saved = codeSystemRepository.save(codeSystem);
          log.info("New CodeSystem inserted: {}", codeSystem);
        } else {
          CodeSystem existingCodeSystem = existingCodeSystemOptional.get();
          existingCodeSystem.setTitle(codeSystem.getTitle());
          existingCodeSystem.setFullUrl(codeSystem.getFullUrl());
          existingCodeSystem.setName(codeSystem.getName());
          existingCodeSystem.getVersion().setFhirVersion(codeSystem.getVersion().getFhirVersion());
          existingCodeSystem.setVersionId(codeSystem.getVersionId());
          existingCodeSystem.setOid(codeSystem.getOid());
          existingCodeSystem.setLastUpdated(codeSystem.getLastUpdated());
          existingCodeSystem.setLastUpdatedUpstream(codeSystem.getLastUpdatedUpstream());
          existingCodeSystem.setLatestVersion(codeSystem.isLatestVersion());
          existingCodeSystem.setInVsac(codeSystem.isInVsac());
          saved = codeSystemRepository.save(existingCodeSystem);
          log.info("CodeSystem updated: {}", existingCodeSystem);
        }
        if (saved.isLatestVersion()) {
          codeSystemService.promoteToLatestVersion(saved.getId());
        }
      } catch (DataAccessException e) {
        log.error(
            "Database error while updating/inserting CodeSystem {}: {}",
            codeSystem.getName(),
            e.getMessage());
      }
    }
  }
}
