package cms.gov.madie.terminology.service;

import java.util.stream.Collectors;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.List;

import org.springframework.util.CollectionUtils;
import org.springframework.stereotype.Service;

import cms.gov.madie.terminology.dto.ValueSetsSearchCriteria;
import cms.gov.madie.terminology.exceptions.VsacUnauthorizedException;
import cms.gov.madie.terminology.mapper.VsacToFhirValueSetMapper;
import cms.gov.madie.terminology.models.UmlsUser;
import cms.gov.madie.terminology.repositories.UmlsUserRepository;
import cms.gov.madie.terminology.util.TerminologyServiceUtil;
import cms.gov.madie.terminology.webclient.TerminologyServiceWebClient;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import gov.cms.madie.models.cql.terminology.CqlCode;
import gov.cms.madie.models.cql.terminology.VsacCode;
import gov.cms.madie.models.mapping.CodeSystemEntry;
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

  public String getServiceTicket(String tgt) {
    return terminologyWebClient.getServiceTicket(tgt);
  }

  public RetrieveMultipleValueSetsResponse getValueSet(
      String oid, String tgt, String profile, String includeDraft, String release, String version) {
    log.debug("Fetching SVS ValueSet: " + oid);
    String serviceTicket;
    try {
      serviceTicket = getServiceTicket(tgt);
    } catch (Exception e) {
      log.error("Error while getting service ticket", e);
      throw new VsacUnauthorizedException(
          "Error occurred while fetching service ticket. "
              + "Please make sure you are logged in to UMLS.");
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
      ValueSetsSearchCriteria searchCriteria, String tgt) {
    List<ValueSetsSearchCriteria.ValueSetParams> valueSetParams =
        searchCriteria.getValueSetParams();
    return valueSetParams.stream()
        .map(
            vsParam ->
                getValueSet(
                    vsParam.getOid(),
                    tgt,
                    searchCriteria.getProfile(),
                    searchCriteria.getIncludeDraft(),
                    vsParam.getRelease(),
                    vsParam.getVersion()))
        .collect(Collectors.toList());
  }

  public List<CqlCode> validateCodes(List<CqlCode> cqlCodes, String tgt) {
    List<CodeSystemEntry> codeSystemEntries = mappingService.getCodeSystemEntries();
    for (CqlCode cqlCode : cqlCodes) {
      cqlCode.getCodeSystem().setValid(true);
      cqlCode.setValid(true);
      String cqlCodeSystemOid = cqlCode.getCodeSystem().getOid();
      if (!StringUtils.isBlank(cqlCodeSystemOid)) {
        Optional<CodeSystemEntry> codeSystemEntry =
            codeSystemEntries.stream()
                .filter(
                    cse ->
                        cse.getUrl()
                            .equalsIgnoreCase(
                                TerminologyServiceUtil.sanitizeInput(cqlCodeSystemOid)))
                .findFirst();
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
              VsacCode vsacCode = terminologyWebClient.getCode(codePath, getServiceTicket(tgt));
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
    return cqlCodes;
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
    String cqlCodeSystemVersion = cqlCode.getCodeSystem().getVersion();
    List<CodeSystemEntry.Version> codeSystemEntryVersion = codeSystemEntry.getVersions();
    if (!StringUtils.isBlank(cqlCodeSystemVersion)) { // sanitize before checking for null value
      if (CollectionUtils.isEmpty(codeSystemEntryVersion)) {
        log.debug(
            "CodeSystem {} does not have any known versions", cqlCode.getCodeSystem().getOid());
        return TerminologyServiceUtil.sanitizeInput(cqlCodeSystemVersion);
      } else {
        Optional<CodeSystemEntry.Version> optionalCodeSystemVersion =
            codeSystemEntry.getVersions().stream()
                .filter(
                    v ->
                        v.getFhir()
                            .equalsIgnoreCase(
                                TerminologyServiceUtil.sanitizeInput(cqlCodeSystemVersion)))
                .findFirst();
        if (optionalCodeSystemVersion.isPresent()) {
          return optionalCodeSystemVersion.get().getVsac();
        } else {
          log.debug(
              "None of the known FHIR code system versions {} matches with version {}",
              cqlCode.getCodeSystem().getOid(),
              cqlCodeSystemVersion);
          return TerminologyServiceUtil.sanitizeInput(cqlCodeSystemVersion);
        }
      }
    } else {
      if (CollectionUtils.isEmpty(codeSystemEntryVersion)) {
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

  public Optional<UmlsUser> findByHarpId(String harpId) {
    return umlsUserRepository.findByHarpId(harpId);
  }

  public String getTgt(String apiKey) throws InterruptedException, ExecutionException {
    return terminologyWebClient.getTgt(apiKey);
  }
}
