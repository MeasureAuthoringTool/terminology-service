package gov.cms.madie.terminology.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.repositories.CodeSystemRepository;
import gov.cms.madie.terminology.util.PageCursor;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

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
    List<CodeSystem> historyCodeSystems = new ArrayList<>();
    List<String> failedCodeSystemIds = new ArrayList<>();
    Set<String> invalidCurrentIds = new LinkedHashSet<>();
    // URLs from families whose current entry is invalid keep their existing latest flags.
    Set<String> preservedLatestUrls = new LinkedHashSet<>();
    for (var currentCodeSystem :
        retrieveCurrentCodeSystems(umlsUser, invalidCurrentIds).entrySet()) {
      String id = currentCodeSystem.getKey();
      List<CodeSystem> history;
      try {
        history = retrieveCodeSystemHistory(umlsUser, id);
      } catch (RuntimeException e) {
        // A family without complete history is left unchanged in the database.
        failedCodeSystemIds.add(id);
        log.error("History retrieval failed for CodeSystem {}; skipping this CodeSystem", id, e);
        continue;
      }
      allCodeSystems.add(currentCodeSystem.getValue());
      if (invalidCurrentIds.contains(id)) {
        history.forEach(codeSystem -> preservedLatestUrls.add(codeSystem.getFullUrl()));
      }
      historyCodeSystems.addAll(history);
    }
    // Search defines current business versions and takes precedence over history duplicates.
    allCodeSystems.addAll(historyCodeSystems);
    List<CodeSystem> codeSystems = distinctBusinessVersions(allCodeSystems);
    codeSystems.stream()
        .filter(CodeSystem::isLatestVersion)
        .map(CodeSystem::getFullUrl)
        .forEach(preservedLatestUrls::remove);
    updateOrInsertRetrievedCodeSystems(codeSystems, preservedLatestUrls);
    preservedLatestUrls.forEach(url -> keepSingleLatestVersion(url, codeSystems));
    if (!failedCodeSystemIds.isEmpty()) {
      log.warn(
          "Code system refresh skipped CodeSystems with failed history retrieval; "
              + "existing records left unchanged: {}",
          failedCodeSystemIds);
    }
    return codeSystems;
  }

  private List<CodeSystem> distinctBusinessVersions(List<CodeSystem> allCodeSystems) {
    Map<CodeSystemVersionKey, CodeSystem> versions = new LinkedHashMap<>();
    allCodeSystems.forEach(
        codeSystem ->
            versions.putIfAbsent(
                new CodeSystemVersionKey(
                    codeSystem.getFullUrl(), codeSystem.getVersion().getFhirVersion()),
                codeSystem));
    return new ArrayList<>(versions.values());
  }

  private record CodeSystemVersionKey(String fullUrl, String version) {}

  /**
   * Resolves multiple latest records for a URL whose current VSAC entry was invalid. Keeps the
   * latest record listed first in VSAC history, or the most recently updated when none are listed.
   */
  private void keepSingleLatestVersion(String url, List<CodeSystem> retrievedCodeSystems) {
    try {
      List<CodeSystem> latestVersions =
          codeSystemRepository.findAllByFullUrl(url).stream()
              .filter(CodeSystem::isLatestVersion)
              .toList();
      if (latestVersions.size() < 2) {
        return;
      }
      List<String> historyVersions =
          retrievedCodeSystems.stream()
              .filter(codeSystem -> url.equals(codeSystem.getFullUrl()))
              .map(CodeSystemSyncService::fhirVersion)
              .toList();
      CodeSystem keep =
          latestVersions.stream()
              .filter(codeSystem -> historyVersions.contains(fhirVersion(codeSystem)))
              .min(comparingInt(codeSystem -> historyVersions.indexOf(fhirVersion(codeSystem))))
              .orElseGet(
                  () ->
                      latestVersions.stream()
                          .max(comparing(CodeSystem::getLastUpdated, nullsFirst(naturalOrder())))
                          .orElseThrow());
      List<CodeSystem> demoted =
          latestVersions.stream()
              .filter(codeSystem -> !Objects.equals(codeSystem.getId(), keep.getId()))
              .toList();
      demoted.forEach(codeSystem -> codeSystem.setLatestVersion(false));
      codeSystemRepository.saveAll(demoted);
      log.warn(
          "Multiple latest CodeSystem versions found for url: {}; kept version: {} and demoted: {}",
          url,
          fhirVersion(keep),
          demoted.stream().map(CodeSystemSyncService::fhirVersion).toList());
    } catch (DataAccessException e) {
      log.error(
          "Database error while resolving latest CodeSystem version for url {}: {}",
          url,
          e.getMessage());
    }
  }

  private static String fhirVersion(CodeSystem codeSystem) {
    return codeSystem.getVersion() == null ? null : codeSystem.getVersion().getFhirVersion();
  }

  private Map<String, CodeSystem> retrieveCurrentCodeSystems(
      UmlsUser umlsUser, Set<String> invalidCurrentIds) {
    Map<String, CodeSystem> currentCodeSystems = new LinkedHashMap<>();
    PageCursor page = new PageCursor(0, 50);
    do {
      log.info("requesting page offset: {} count: {}", page.offset(), page.count());
      Bundle codeSystemBundle = retrieveCodeSystemsPage(umlsUser, page.offset(), page.count());
      for (Bundle.BundleEntryComponent entry : codeSystemBundle.getEntry()) {
        if (entry.getResource() instanceof org.hl7.fhir.r4.model.CodeSystem codeSystem) {
          String codeSystemId = codeSystem.getIdElement().getIdPart();
          if (StringUtils.isNotBlank(codeSystemId)) {
            CodeSystem currentCodeSystem = toCodeSystem(codeSystem);
            currentCodeSystem.setLatestVersion(hasValidIdentity(currentCodeSystem));
            if (!currentCodeSystem.isLatestVersion()) {
              invalidCurrentIds.add(codeSystemId);
              log.warn(
                  "Current VSAC CodeSystem {} has an invalid URL or business version; "
                      + "skipping this entry and preserving existing latest flags: {}",
                  codeSystemId,
                  currentCodeSystem);
            }
            currentCodeSystems.putIfAbsent(codeSystemId, currentCodeSystem);
          } else {
            log.warn("Skipping history retrieval for CodeSystem with no logical id");
          }
        } else {
          log.warn("Skipping search entry without a CodeSystem resource");
        }
      }
      page = nextPageCursor(codeSystemBundle);
    } while (page != null);
    return currentCodeSystems;
  }

  private List<CodeSystem> retrieveCodeSystemHistory(UmlsUser umlsUser, String codeSystemId) {
    List<CodeSystem> history = new ArrayList<>();
    PageCursor page = new PageCursor(0, 50);
    do {
      log.info(
          "requesting CodeSystem {} history page offset: {} count: {}",
          codeSystemId,
          page.offset(),
          page.count());
      Bundle historyBundle =
          retrieveCodeSystemHistoryPage(umlsUser, codeSystemId, page.offset(), page.count());
      historyBundle.getEntry().stream()
          .filter(this::isActiveCodeSystemHistoryEntry)
          .map(entry -> (org.hl7.fhir.r4.model.CodeSystem) entry.getResource())
          .map(this::toCodeSystem)
          .forEach(history::add);
      page = nextPageCursor(historyBundle);
    } while (page != null);
    return history;
  }

  private PageCursor nextPageCursor(Bundle bundle) {
    Optional<String> nextUrl =
        bundle.getLink().stream()
            .filter(link -> "next".equals(link.getRelation()))
            .map(Bundle.BundleLinkComponent::getUrl)
            .findFirst();
    if (nextUrl.isEmpty()) {
      return null;
    }
    var queryParams = UriComponentsBuilder.fromUriString(nextUrl.get()).build().getQueryParams();
    String newOffset = queryParams.getFirst("_offset");
    String newCount = queryParams.getFirst("_count");
    if (newOffset == null || newCount == null) {
      throw new IllegalStateException(
          "Bundle next link is missing _offset or _count: " + nextUrl.get());
    }
    return new PageCursor(Integer.parseInt(newOffset), Integer.parseInt(newCount));
  }

  private boolean isActiveCodeSystemHistoryEntry(Bundle.BundleEntryComponent entry) {
    return entry.getResource() instanceof org.hl7.fhir.r4.model.CodeSystem
        && (!entry.hasRequest() || entry.getRequest().getMethod() != Bundle.HTTPVerb.DELETE);
  }

  private CodeSystem toCodeSystem(org.hl7.fhir.r4.model.CodeSystem codeSystem) {
    return CodeSystem.builder()
        .fullUrl(codeSystem.getUrl())
        .title(codeSystem.getTitle())
        .name(codeSystem.getName())
        .version(CodeSystem.Version.builder().fhirVersion(codeSystem.getVersion()).build())
        .versionId(codeSystem.getMeta().getVersionId())
        .oid(parseOidFromIdentifier(codeSystem.getIdentifier()))
        .isInVsac(true)
        .lastUpdated(Instant.now())
        .lastUpdatedUpstream(codeSystem.getMeta().getLastUpdated())
        .build();
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

  private Bundle retrieveCodeSystemHistoryPage(
      UmlsUser umlsUser, String codeSystemId, Integer offset, Integer count) {
    IParser parser = fhirContext.newJsonParser();
    String responseString =
        fhirTerminologyServiceWebClient.getCodeSystemHistoryPage(
            codeSystemId, offset, count, umlsUser.getApiKey());
    return parser.parseResource(Bundle.class, responseString);
  }

  private boolean hasValidIdentity(CodeSystem codeSystem) {
    return codeSystem.getVersion() != null
        && StringUtils.isNotBlank(codeSystem.getVersion().getFhirVersion())
        && !Strings.CI.equals(codeSystem.getVersion().getFhirVersion(), "null")
        && StringUtils.isNotBlank(codeSystem.getFullUrl())
        && !Strings.CI.equals(codeSystem.getFullUrl(), "null");
  }

  private void updateOrInsertRetrievedCodeSystems(
      List<CodeSystem> codeSystemList, Set<String> preservedLatestUrls) {
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
        if (existingCodeSystemOptional.isEmpty()) {
          codeSystemService.createCodeSystem(codeSystem);
          log.info("New CodeSystem inserted: {}", codeSystem);
        } else {
          if (preservedLatestUrls.contains(codeSystem.getFullUrl())) {
            codeSystem.setLatestVersion(existingCodeSystemOptional.get().isLatestVersion());
          }
          CodeSystem updated =
              codeSystemService.updateCodeSystem(
                  existingCodeSystemOptional.get().getId(), codeSystem);
          log.info("CodeSystem updated: {}", updated);
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
