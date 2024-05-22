package gov.cms.madie.terminology.service;

import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import gov.cms.madie.models.cql.terminology.CqlCode;
import gov.cms.madie.models.cql.terminology.VsacCode;
import gov.cms.madie.models.mapping.CodeSystemEntry;
import gov.cms.madie.terminology.dto.Code;
import gov.cms.madie.terminology.dto.CodeStatus;
import gov.cms.madie.terminology.dto.QdmValueSet;
import gov.cms.madie.terminology.dto.ValueSetsSearchCriteria;
import gov.cms.madie.terminology.exceptions.VsacUnauthorizedException;
import gov.cms.madie.terminology.mapper.VsacToFhirValueSetMapper;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.repositories.UmlsUserRepository;
import gov.cms.madie.terminology.util.TerminologyServiceUtil;
import gov.cms.madie.terminology.webclient.TerminologyServiceWebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse.DescribedValueSet;

@Service
@Slf4j
@RequiredArgsConstructor
public class VsacService {

  private final TerminologyServiceWebClient terminologyWebClient;
  private final VsacToFhirValueSetMapper vsacToFhirValueSetMapper;
  private final MappingService mappingService;
  private final UmlsUserRepository umlsUserRepository;

  /**
   * If umlsUser is not available or if API-KEY is unavailable then return false. Otherwise, return
   * true.
   *
   * @param userName harpId
   * @return true/false based on if user's API-KEY is available in the data store.
   */
  public boolean validateUmlsInformation(String userName) {
    Optional<UmlsUser> umlsUser = findByHarpId(userName);
    return umlsUser.isPresent() && !StringUtils.isBlank(umlsUser.get().getApiKey());
  }

  public RetrieveMultipleValueSetsResponse getValueSet(
      String oid,
      UmlsUser umlsUser,
      String profile,
      String includeDraft,
      String release,
      String version) {
    log.debug("Fetching SVS ValueSet: " + oid);
    return terminologyWebClient.getValueSet(
        oid, umlsUser.getApiKey(), profile, includeDraft, release, version);
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

  public List<QdmValueSet> getValueSetsInQdmFormat(
      ValueSetsSearchCriteria searchCriteria, UmlsUser umlsUser) {
    List<RetrieveMultipleValueSetsResponse> vsacValueSets = getValueSets(searchCriteria, umlsUser);
    return convertToQdmValueSets(vsacValueSets);
  }

  /**
   * @param cqlCodes List of objects generated by Antlr from user entered CQL string.
   * @param umlsUser umls information from db
   * @return List of CqlCodes with their validation results. If an associated CodeSystem is not
   *     available for a code, then this method will skip validating that code, an error will be
   *     displayed to user by cql-elm-translator
   */
  public List<CqlCode> validateCodes(List<CqlCode> cqlCodes, UmlsUser umlsUser, String model) {
    List<CodeSystemEntry> codeSystemEntries = mappingService.getCodeSystemEntries();
    for (CqlCode cqlCode : cqlCodes) {
      cqlCode.setValid(true);
      if (cqlCode.getCodeSystem() != null) {
        cqlCode.getCodeSystem().setValid(true);
        String cqlCodeSystemOid = cqlCode.getCodeSystem().getOid();
        if (!StringUtils.isBlank(cqlCodeSystemOid)) {
          Optional<CodeSystemEntry> codeSystemEntry =
              TerminologyServiceUtil.getCodeSystemEntry(codeSystemEntries, cqlCodeSystemOid, model);
          if (codeSystemEntry.isPresent()) {
            // if codeSystemEntry is available in mapping json, but listed as NOT IN VSAC, then it
            // is a valid FHIR code system.
            if (!codeSystemEntry.get().getOid().contains("NOT.IN.VSAC")) {
              String codeSystemVersion = buildCodeSystemVersion(cqlCode, codeSystemEntry.get());
              String codeId = cqlCode.getCodeId();
              if (codeId == null || TerminologyServiceUtil.sanitizeInput(codeId).isBlank()) {
                log.info("Code id is not available for code {}", cqlCode.getName());
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
                if (!vsacCode.getStatus().equalsIgnoreCase("ok")) {
                  buildVsacErrorMessage(cqlCode, vsacCode);
                } else {
                  cqlCode.setValid(true);
                }
              }
            }
          } else {
            // unidentified code system.
            log.info(
                    "No associated Code system found in code system entry json for {}",
                    cqlCode.getCodeSystem().getOid());
            cqlCode.getCodeSystem().setValid(false);
            cqlCode.getCodeSystem().setErrorMessage("Invalid Code system");
          }
        } else {
          // if oid/url is not provided in cql, then the code system is considered invalid.
          log.info("CodeSystem {} does not contain any URL", cqlCode.getCodeSystem().getName());
          cqlCode.getCodeSystem().setValid(false);
          cqlCode.getCodeSystem().setErrorMessage("Code system URL is required");
        }
      }
    }
    return cqlCodes;
  }

  public CodeStatus getCodeStatus(Code code, String apiKey) {
    String svsVersion = getSvsCodeSystemVersion(code);
    if (svsVersion == null) {
      return CodeStatus.NA;
    }
    // prepare code path e.g. CODE:/CodeSystem/ActCode/Version/9.0.0/Code/AMB/Info
    String codePath =
        TerminologyServiceUtil.buildCodePath(code.getCodeSystem(), svsVersion, code.getName());
    VsacCode svsCode = terminologyWebClient.getCode(codePath, apiKey);
    if (svsCode.getStatus().equalsIgnoreCase("ok")) {
      if ("Yes".equals(svsCode.getData().getResultSet().get(0).getActive())) {
        return CodeStatus.ACTIVE;
      } else {
        return CodeStatus.INACTIVE;
      }
    }
    return CodeStatus.NA;
  }

  private String getSvsCodeSystemVersion(Code code) {
    if (StringUtils.isNotBlank(code.getSvsVersion())) {
      return code.getSvsVersion();
    }
    CodeSystemEntry systemEntry = mappingService.getCodeSystemEntryByOid(code.getCodeSystemOid());
    // do not call SVS API to get code status if the system is not in SVS API
    if (systemEntry == null
        || systemEntry.getOid().contains("NOT.IN.VSAC")
        || CollectionUtils.isEmpty(systemEntry.getVersions())) {
      return null;
    }

    // get corresponding SVS version for given FHIR version
    CodeSystemEntry.Version version =
        systemEntry.getVersions().stream()
            .filter(v -> Objects.equals(v.getFhir(), code.getVersion()))
            .findFirst()
            .orElse(null);
    if (version == null || version.getVsac() == null) {
      return null;
    }
    return version.getVsac();
  }

  /**
   * Verify if the user with given harp id has valid UMLS user
   *
   * @param harpId
   * @return Instance of UmlsUser
   */
  public UmlsUser verifyUmlsAccess(String harpId) {
    UmlsUser user = findByHarpId(harpId).orElse(null);
    if (user == null || StringUtils.isBlank(user.getApiKey())) {
      log.error("UMLS API Key Not found for user : [{}}]", harpId);
      throw new VsacUnauthorizedException("Please login to UMLS before proceeding");
    }
    return user;
  }

  private VsacCode validateCodeAgainstVsac(String codePath, UmlsUser umlsUser) {
    return terminologyWebClient.getCode(codePath, umlsUser.getApiKey());
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
    // Validation errors appear in an Errors object
    if (vsacCode.getErrors() != null
        && StringUtils.isNumeric(vsacCode.getErrors().getResultSet().get(0).getErrCode())) {
      int errorCode = Integer.parseInt(vsacCode.getErrors().getResultSet().get(0).getErrCode());
      if (errorCode == 800 || errorCode == 801) {
        log.info("Error code is 800, or 801 from VSAC. Error: {}", vsacCode.getErrors().getResultSet().get(0));
        cqlCode.getCodeSystem().setValid(false);
        cqlCode
            .getCodeSystem()
            .setErrorMessage(vsacCode.getErrors().getResultSet().get(0).getErrDesc());
      } else if (errorCode == 802) {
        log.info("Error code is 802 from VSAC. Error: {}", vsacCode.getErrors().getResultSet().get(0));
        cqlCode.setValid(false);
        cqlCode.setErrorMessage(vsacCode.getErrors().getResultSet().get(0).getErrDesc());
      }
      // API Errors appear in the status field.
    } else if (StringUtils.isNumeric(vsacCode.getStatus())) {
      cqlCode.setValid(false);
      cqlCode.setErrorMessage(
          "Communication Error with VSAC. Please retry your request. "
              + "If this error persists, please contact the Help Desk.");
    } else {
      cqlCode.setValid(false);
      log.info("Error code is uncaught. General catch, Error: {} status: {}", vsacCode.getErrors().getResultSet().get(0), vsacCode.getStatus());
    }
  }

  public UmlsUser saveUmlsUser(String harpId, String apiKey) {
    Instant now = Instant.now();
    UmlsUser umlsUser =
        UmlsUser.builder().apiKey(apiKey).harpId(harpId).createdAt(now).modifiedAt(now).build();
    return umlsUserRepository.save(umlsUser);
  }

  private List<QdmValueSet> convertToQdmValueSets(
      List<RetrieveMultipleValueSetsResponse> valueSetsResponses) {
    return valueSetsResponses.stream()
        .map(
            response -> {
              var describedValueSet = response.getDescribedValueSet();
              List<QdmValueSet.Concept> concepts = getValueSetConcepts(describedValueSet);
              return QdmValueSet.builder()
                  .oid(describedValueSet.getID())
                  .displayName(describedValueSet.getDisplayName())
                  .version(describedValueSet.getVersion())
                  .concepts(concepts)
                  .build();
            })
        .toList();
  }

  private List<QdmValueSet.Concept> getValueSetConcepts(DescribedValueSet valueSet) {
    var conceptList = valueSet.getConceptList();
    if (conceptList == null) {
      log.info("Empty value set:{}", valueSet.getID());
      return List.of();
    }
    return conceptList.getConcepts().stream()
        .map(
            concept ->
                QdmValueSet.Concept.builder()
                    .code(concept.getCode())
                    .codeSystemName(concept.getCodeSystemName())
                    .codeSystemVersion(concept.getCodeSystemVersion())
                    .codeSystemOid(concept.getCodeSystem())
                    .displayName(concept.getDisplayName())
                    .build())
        .toList();
  }

  public Optional<UmlsUser> findByHarpId(String harpId) {
    return umlsUserRepository.findByHarpId(harpId);
  }
}
