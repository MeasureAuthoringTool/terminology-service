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
    // Search discovers logical ids; history supplies all versions, including the latest.
    retrieveCodeSystemIds(umlsUser)
        .forEach(id -> retrieveCodeSystemHistory(umlsUser, id, allCodeSystems));
    List<CodeSystem> codeSystems = latestRevisions(allCodeSystems);
    markLatestVersion(codeSystems);
    updateOrInsertRetrievedCodeSystems(codeSystems);
    return codeSystems;
  }

  private List<CodeSystem> latestRevisions(List<CodeSystem> allCodeSystems) {
    Map<CodeSystemVersionKey, CodeSystem> revisions = new LinkedHashMap<>();
    allCodeSystems.forEach(
        codeSystem ->
            revisions.merge(
                new CodeSystemVersionKey(
                    codeSystem.getFullUrl(), codeSystem.getVersion().getFhirVersion()),
                codeSystem,
                (existing, incoming) ->
                    comparing(CodeSystem::getLastUpdatedUpstream, nullsFirst(naturalOrder()))
                                .compare(existing, incoming)
                            >= 0
                        ? existing
                        : incoming));
    return new ArrayList<>(revisions.values());
  }

  private record CodeSystemVersionKey(String fullUrl, String version) {}

  private Set<String> retrieveCodeSystemIds(UmlsUser umlsUser) {
    Set<String> codeSystemIds = new LinkedHashSet<>();
    PageCursor page = new PageCursor(0, 50);
    do {
      log.info("requesting page offset: {} count: {}", page.offset(), page.count());
      Bundle codeSystemBundle = retrieveCodeSystemsPage(umlsUser, page.offset(), page.count());
      for (Bundle.BundleEntryComponent entry : codeSystemBundle.getEntry()) {
        if (entry.getResource() instanceof org.hl7.fhir.r4.model.CodeSystem codeSystem) {
          String codeSystemId = codeSystem.getIdElement().getIdPart();
          if (StringUtils.isNotBlank(codeSystemId)) {
            codeSystemIds.add(codeSystemId);
          } else {
            log.warn("Skipping history retrieval for CodeSystem with no logical id");
          }
        } else {
          log.warn("Skipping search entry without a CodeSystem resource");
        }
      }
      page = nextPageCursor(codeSystemBundle);
    } while (page != null);
    return codeSystemIds;
  }

  private void retrieveCodeSystemHistory(
      UmlsUser umlsUser, String codeSystemId, List<CodeSystem> allCodeSystems) {
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
          .forEach(allCodeSystems::add);
      page = nextPageCursor(historyBundle);
    } while (page != null);
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

  private void markLatestVersion(List<CodeSystem> allCodeSystems) {
    // Group by fullUrl and find the latest version for each group
    allCodeSystems.stream()
        .filter(cs -> hasValidIdentity(cs) && Objects.nonNull(cs.getLastUpdatedUpstream()))
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
                    "New latest version for CodeSystem with fullUrl: {} and version: {}",
                    latestCodeSystem.getFullUrl(),
                    latestCodeSystem.getVersion().getFhirVersion());
              }
            });
  }

  private boolean hasValidIdentity(CodeSystem codeSystem) {
    return codeSystem.getVersion() != null
        && StringUtils.isNotBlank(codeSystem.getVersion().getFhirVersion())
        && !Strings.CI.equals(codeSystem.getVersion().getFhirVersion(), "null")
        && StringUtils.isNotBlank(codeSystem.getFullUrl())
        && !Strings.CI.equals(codeSystem.getFullUrl(), "null");
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
        if (existingCodeSystemOptional.isEmpty()) {
          codeSystemService.createCodeSystem(codeSystem);
          log.info("New CodeSystem inserted: {}", codeSystem);
        } else {
          codeSystemService.updateCodeSystem(existingCodeSystemOptional.get().getId(), codeSystem);
          log.info("CodeSystem updated: {}", existingCodeSystemOptional.get());
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
