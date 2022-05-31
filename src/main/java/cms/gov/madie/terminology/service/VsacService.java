package cms.gov.madie.terminology.service;

import cms.gov.madie.terminology.dto.ValueSetsSearchCriteria;
import cms.gov.madie.terminology.exceptions.VsacGenericException;
import cms.gov.madie.terminology.mapper.VsacToFhirValueSetMapper;
import cms.gov.madie.terminology.util.TerminologyServiceUtil;
import cms.gov.madie.terminology.webclient.TerminologyServiceWebClient;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import gov.cms.madiejavamodels.cql.terminology.CqlCode;
import gov.cms.madiejavamodels.cql.terminology.VsacCode;
import gov.cms.madiejavamodels.mappingData.CodeSystemEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class VsacService {

  private final TerminologyServiceWebClient terminologyWebClient;
  private final VsacToFhirValueSetMapper vsacToFhirValueSetMapper;
  private final MappingService mappingService;

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
      throw new VsacGenericException(
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
      ValueSetsSearchCriteria searchCriteria) {
    List<ValueSetsSearchCriteria.ValueSetParams> valueSetParams =
        searchCriteria.getValueSetParams();
    return valueSetParams.stream()
        .map(
            vsParam ->
                getValueSet(
                    vsParam.getOid(),
                    searchCriteria.getTgt(),
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
              cqlCode.setValid(vsacCode != null);
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
    List<CodeSystemEntry.Version> codeSystemEntryVersion = codeSystemEntry.getVersion();
    if (!StringUtils.isBlank(cqlCodeSystemVersion)) { // sanitize before checking for null value
      if (CollectionUtils.isEmpty(codeSystemEntryVersion)) {
        log.debug(
            "CodeSystem {} does not have any known versions", cqlCode.getCodeSystem().getOid());
        return TerminologyServiceUtil.sanitizeInput(cqlCodeSystemVersion);
      } else {
        Optional<CodeSystemEntry.Version> optionalCodeSystemVersion =
            codeSystemEntry.getVersion().stream()
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
}
