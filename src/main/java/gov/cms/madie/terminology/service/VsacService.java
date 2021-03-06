package gov.cms.madie.terminology.service;

import java.util.stream.Collectors;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.List;

import org.springframework.util.CollectionUtils;
import org.springframework.stereotype.Service;

import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import gov.cms.madie.models.cql.terminology.CqlCode;
import gov.cms.madie.models.cql.terminology.VsacCode;
import gov.cms.madie.models.mapping.CodeSystemEntry;

import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.exceptions.VsacUnauthorizedException;
import gov.cms.madie.terminology.mapper.VsacToFhirValueSetMapper;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.repositories.UmlsUserRepository;
import gov.cms.madie.terminology.util.TerminologyServiceUtil;
import gov.cms.madie.terminology.webclient.TerminologyServiceWebClient;

import org.hl7.fhir.r4.model.ValueSet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class VsacService {

  private final TerminologyServiceWebClient terminologyWebClient;
  private final VsacToFhirValueSetMapper vsacToFhirValueSetMapper;
  private final MappingService mappingService;
  private final UmlsUserRepository umlsUserRepository;

  /**
   * If serviceTicket is null, then the TGT is expired. so it fetches a new TGT and tries to
   * generate a service ticket. max retires is 3.
   *
   * @param umlsUser data from db
   * @return service ticket valid for 5 minutes or for a single VSAC call or null / empty string if
   *     unable to generate service ticket.
   */
  private String getServiceTicket(UmlsUser umlsUser, int maxRetries) {
    String serviceTicket = terminologyWebClient.getServiceTicket(umlsUser.getTgt());
    if (StringUtils.isEmpty(serviceTicket) && maxRetries < 3) {
      log.debug("TGT for User {} is expired or invalid, fetching a new TGT", umlsUser.getHarpId());
      UmlsUser umlsUserWithNewTgt = generateNewTgtForUmlsUserAndUpdateDb(umlsUser);
      maxRetries++;
      serviceTicket = getServiceTicket(umlsUserWithNewTgt, maxRetries);
    }
    return serviceTicket;
  }

  /**
   * If umlsUser is not available or if API-KEY is unavailable then return false If API-KEY is
   * available, and TGT is null, then fetch a new TGT and stores in DB If TGT is available in DB,
   * then verify if TGT is still valid If serviceTicket is null, i.e TGT is invalid. Fetch the
   * latest TGT and store it in DB.
   *
   * @param userName harpId
   * @return true/false based on if user is authenticated with UMLS
   */
  public boolean validateUmlsInformation(String userName) {
    Optional<UmlsUser> umlsUser = findByHarpId(userName);
    if (umlsUser.isPresent() && umlsUser.get().getApiKey() != null) {
      String existingTgt = umlsUser.get().getTgt();
      if (!StringUtils.isEmpty(existingTgt)) {
        return !StringUtils.isEmpty(getServiceTicket(umlsUser.get(), 0));
      } else {
        // API-KEY is available, so get a new TGT and store it in DB.
        generateNewTgtForUmlsUserAndUpdateDb(umlsUser.get());
        return true;
      }
    }
    log.debug("User {} is not authenticated with UMLS", userName);
    return false;
  }

  public RetrieveMultipleValueSetsResponse getValueSet(
      String oid,
      UmlsUser umlsUser,
      String profile,
      String includeDraft,
      String release,
      String version) {
    log.debug("Fetching SVS ValueSet: " + oid);
    String serviceTicket = getServiceTicket(umlsUser, 0);
    if (StringUtils.isEmpty(serviceTicket)) {
      log.error("Error while getting service ticket for user {}", umlsUser.getHarpId());
      throw new VsacUnauthorizedException(
          "Error occurred while fetching service ticket. Please contact helpdesk.");
    }
    return terminologyWebClient.getValueSet(
        oid, serviceTicket, profile, includeDraft, release, version);
  }

  public ValueSet convertToFHIRValueSet(RetrieveMultipleValueSetsResponse vsacValueSetResponse) {
    return vsacToFhirValueSetMapper.convertToFHIRValueSet(vsacValueSetResponse);
  }

  public List<ValueSet> convertToFHIRValueSets(
      List<RetrieveMultipleValueSetsResponse> vsacValueSets) {
    return vsacValueSets.stream()
        .map(vsacToFhirValueSetMapper::convertToFHIRValueSet)
        .collect(Collectors.toList());
  }

  public List<RetrieveMultipleValueSetsResponse> getValueSets(
      ValueSetsSearchCriteria searchCriteria, UmlsUser umlsUser) {
    List<ValueSetsSearchCriteria.ValueSetParams> valueSetParams =
        searchCriteria.getValueSetParams();
    return valueSetParams.stream()
        .map(
            vsParam ->
                getValueSet(
                    vsParam.getOid(),
                    umlsUser,
                    searchCriteria.getProfile(),
                    searchCriteria.getIncludeDraft(),
                    vsParam.getRelease(),
                    vsParam.getVersion()))
        .collect(Collectors.toList());
  }

  /**
   * @param cqlCodes List of objects generated by Antlr from user entered CQL string.
   * @param umlsUser umls information from db
   * @return List of CqlCodes with their validation results. If an associated CodeSystem is not
   *     available for a code, then this method will skip validating that code, an error will be
   *     displayed to user by cql-elm-translator
   */
  public List<CqlCode> validateCodes(List<CqlCode> cqlCodes, UmlsUser umlsUser) {
    List<CodeSystemEntry> codeSystemEntries = mappingService.getCodeSystemEntries();
    for (CqlCode cqlCode : cqlCodes) {
      cqlCode.setValid(true);
      if (cqlCode.getCodeSystem() != null) {
        cqlCode.getCodeSystem().setValid(true);
        String cqlCodeSystemOid = cqlCode.getCodeSystem().getOid();
        if (!StringUtils.isBlank(cqlCodeSystemOid)) {
          Optional<CodeSystemEntry> codeSystemEntry =
              getCodeSystemEntry(codeSystemEntries, cqlCodeSystemOid);
          if (codeSystemEntry.isPresent()) {
            // if codeSystemEntry is available in mapping json, but listed as NOT IN VSAC, then it
            // is a valid FHIR code system.
            if (!codeSystemEntry.get().getOid().contains("NOT.IN.VSAC")) {
              String codeSystemVersion = buildCodeSystemVersion(cqlCode, codeSystemEntry.get());
              String codeId = cqlCode.getCodeId();
              if (codeId == null || TerminologyServiceUtil.sanitizeInput(codeId).isBlank()) {
                log.debug("Code id is not available for code {}", cqlCode.getName());
                cqlCode.setValid(false);
                cqlCode.setErrorMessage("Code Id is required");
              } else if (!StringUtils.isBlank(codeSystemVersion)) {
                String codePath =
                    TerminologyServiceUtil.buildCodePath(
                        codeSystemEntry.get().getName(),
                        codeSystemVersion,
                        TerminologyServiceUtil.sanitizeInput(cqlCode.getCodeId()));
                VsacCode vsacCode = validateCodeAgainstVsac(codePath, umlsUser);
                /* if the statusCode is "error" and either CodeSystem or CodeSystem version
                 or Code is not found
                if the statusCode is "ok" then it is a valid code */
                if (vsacCode.getStatus().equalsIgnoreCase("error")) {
                  buildVsacErrorMessage(cqlCode, vsacCode);
                } else {
                  cqlCode.setValid(true);
                }
              }
            }
          } else {
            // unidentified code system.
            log.debug(
                "No associated Code system found in code system entry json for {}",
                cqlCode.getCodeSystem().getOid());
            cqlCode.getCodeSystem().setValid(false);
            cqlCode.getCodeSystem().setErrorMessage("Invalid Code system");
          }
        } else {
          // if oid/url is not provided in cql, then the code system is considered invalid.
          log.debug("CodeSystem {} does not contain any URL", cqlCode.getCodeSystem().getName());
          cqlCode.getCodeSystem().setValid(false);
          cqlCode.getCodeSystem().setErrorMessage("Code system URL is required");
        }
      }
    }
    return cqlCodes;
  }

  private VsacCode validateCodeAgainstVsac(String codePath, UmlsUser umlsUser) {
    String serviceTicket = getServiceTicket(umlsUser, 0);
    if (StringUtils.isEmpty(serviceTicket)) {
      log.error("Error while getting service ticket for user {}", umlsUser.getHarpId());
      throw new VsacUnauthorizedException(
          "Error occurred while fetching service ticket. " + "Please contact helpdesk.");
    }
    return terminologyWebClient.getCode(codePath, serviceTicket);
  }

  /**
   * @param cqlCode object generated by Antlr from user entered CQL string.
   * @param codeSystemEntry object retrieved from the mapping document based on code system oid.
   * @return if user provides a FHIR Code system version, find the equivalent VSAC version in
   *     codeSystemEntry and returns it. if there is no equivalent version in codeSystemEntry, or
   *     there are no versions at all in codeSystemEntry, then this method returns user provided
   *     FHIR version. Finally, if user doesn't provide a version in CQL, get the latest vsac
   *     version from codeSystemEntry (first element in the version List).
   */
  private String buildCodeSystemVersion(CqlCode cqlCode, CodeSystemEntry codeSystemEntry) {
    List<CodeSystemEntry.Version> codeSystemEntryVersion = codeSystemEntry.getVersions();
    if (!StringUtils.isBlank(cqlCode.getCodeSystem().getVersion())) {
      String cqlCodeSystemVersion =
          TerminologyServiceUtil.sanitizeInput(cqlCode.getCodeSystem().getVersion());
      if (CollectionUtils.isEmpty(codeSystemEntryVersion)) {
        log.debug(
            "CodeSystem {} does not have any known versions", cqlCode.getCodeSystem().getOid());
        return cqlCodeSystemVersion;
      } else {
        Optional<CodeSystemEntry.Version> optionalCodeSystemVersion =
            codeSystemEntry.getVersions().stream()
                .filter(v -> v.getFhir().equalsIgnoreCase(cqlCodeSystemVersion))
                .findFirst();
        if (optionalCodeSystemVersion.isPresent()) {
          return optionalCodeSystemVersion.get().getVsac() == null
              ? cqlCodeSystemVersion
              : optionalCodeSystemVersion.get().getVsac();
        } else {
          log.debug(
              "None of the known FHIR code system versions {} matches with version {}",
              cqlCode.getCodeSystem().getOid(),
              cqlCodeSystemVersion);
          return cqlCodeSystemVersion;
        }
      }
    } else {
      if (CollectionUtils.isEmpty(codeSystemEntryVersion)
          || codeSystemEntryVersion.get(0).getVsac() == null) {
        cqlCode.getCodeSystem().setValid(false);
        cqlCode.getCodeSystem().setErrorMessage("Unable to find a code system version");
        return "";
      }
      return codeSystemEntryVersion.get(0).getVsac();
    }
  }

  /**
   * @param cqlCode input cqlCode
   * @param vsacCode response from vsac builds error message based on errorCode from VSAC. errorCode
   *     800 indicates CodeSystem not found. errorCode 801 indicates CodeSystem Version not found.
   *     errorCode 802 indicates Code not found.
   */
  private void buildVsacErrorMessage(CqlCode cqlCode, VsacCode vsacCode) {
    int errorCode = Integer.parseInt(vsacCode.getErrors().getResultSet().get(0).getErrCode());
    if (errorCode == 800 || errorCode == 801) {
      cqlCode.getCodeSystem().setValid(false);
      cqlCode
          .getCodeSystem()
          .setErrorMessage(vsacCode.getErrors().getResultSet().get(0).getErrDesc());
    } else if (errorCode == 802) {
      cqlCode.setValid(false);
      cqlCode.setErrorMessage(vsacCode.getErrors().getResultSet().get(0).getErrDesc());
    }
  }

  private Optional<CodeSystemEntry> getCodeSystemEntry(
      List<CodeSystemEntry> codeSystemEntries, String cqlCodeSystemOid) {
    return codeSystemEntries.stream()
        .filter(
            cse ->
                cse.getUrl()
                    .equalsIgnoreCase(TerminologyServiceUtil.sanitizeInput(cqlCodeSystemOid)))
        .findFirst();
  }

  public UmlsUser saveUmlsUser(String harpId, String apiKey, String tgt) {
    Instant now = Instant.now();
    Instant nowPlus8Hours = now.plus(8, ChronoUnit.HOURS);
    UmlsUser umlsUser =
        UmlsUser.builder()
            .apiKey(apiKey)
            .harpId(harpId)
            .tgt(tgt)
            .tgtExpiryTime(nowPlus8Hours)
            .createdAt(now)
            .modifiedAt(now)
            .build();
    return umlsUserRepository.save(umlsUser);
  }

  private UmlsUser generateNewTgtForUmlsUserAndUpdateDb(UmlsUser umlsUser) {
    String newTgt = getTgt(umlsUser.getApiKey());
    Instant now = Instant.now();
    Instant nowPlus8Hours = now.plus(8, ChronoUnit.HOURS);
    umlsUser.setTgt(newTgt);
    umlsUser.setTgtExpiryTime(nowPlus8Hours);
    umlsUser.setModifiedAt(now);
    return umlsUserRepository.save(umlsUser);
  }

  public Optional<UmlsUser> findByHarpId(String harpId) {
    return umlsUserRepository.findByHarpId(harpId);
  }

  public String getTgt(String apiKey) {
    try {
      return terminologyWebClient.getTgt(apiKey);
    } catch (Exception e) {
      log.error("Error while fetching tgt from UMLS", e);
      throw new VsacUnauthorizedException("Error occurred while fetching tgt from UMLS.");
    }
  }
}
