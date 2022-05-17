package cms.gov.madie.terminology.service;

import cms.gov.madie.terminology.dto.CodeSystemEntry;
import cms.gov.madie.terminology.dto.CqlCode;
import cms.gov.madie.terminology.dto.VsacCode;
import cms.gov.madie.terminology.util.TerminologyServiceUtil;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.stereotype.Service;

import cms.gov.madie.terminology.mapper.VsacToFhirValueSetMapper;
import cms.gov.madie.terminology.webclient.TerminologyServiceWebClient;
import lombok.extern.slf4j.Slf4j;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;

import java.util.List;
import java.util.Optional;

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
      String oid,
      String serviceTicket,
      String profile,
      String includeDraft,
      String release,
      String version) {
    return terminologyWebClient.getValueSet(
        oid, serviceTicket, profile, includeDraft, release, version);
  }

  public ValueSet convertToFHIRValueSet(
      RetrieveMultipleValueSetsResponse vsacValuesetResponse, String oid) {
    return vsacToFhirValueSetMapper.convertToFHIRValueSet(vsacValuesetResponse, oid);
  }

  public List<CqlCode> validateCodes(List<CqlCode> cqlCodes, String tgt) {
    List<CodeSystemEntry> codeSystemEntries = mappingService.getCodeSystemEntries();
    for (CqlCode cqlCode : cqlCodes) {
      if (cqlCode.getCodeSystem().getOid() != null && !cqlCode.getCodeSystem().getOid().isBlank()) {
        Optional<CodeSystemEntry> codeSystemEntry1 =
            codeSystemEntry.stream()
                .filter(
                    c ->
                        c.getUrl()
                            .equalsIgnoreCase(cqlCode.getCodeSystem().getOid().replace("'", "")))
                .findFirst();
        if (codeSystemEntry1.isPresent()) {
          if (codeSystemEntry1.get().getOid().contains("NOT.IN.VSAC")) {
            cqlCode.setValid(true);
          } else {
            if (cqlCode.getCodeSystem().getVersion() == null) {
              String mostRecentCodeSystemVersion =
                  codeSystemEntry1.get().getVersion().get(0).getVsac();
              String codePath =
                  TerminologyServiceUtil.buildCodePath(
                      codeSystemEntry1.get().getName(),
                      mostRecentCodeSystemVersion,
                      cqlCode.getCodeId());
              VsacCode vsacCode = terminologyWebClient.getCode(codePath, getServiceTicket(tgt));
              if (vsacCode != null) {
                cqlCode.setValid(true);
              }
            } else {
              Optional<CodeSystemEntry.Version> codeSystemVersion =
                  codeSystemEntry1.get().getVersion().stream()
                      .filter(
                          v -> v.getFhir().equalsIgnoreCase(cqlCode.getCodeSystem().getVersion()))
                      .findFirst();
              if (codeSystemVersion.isPresent()) {
                String codePath =
                    TerminologyServiceUtil.buildCodePath(
                        codeSystemEntry1.get().getName(),
                        codeSystemVersion.get().getVsac(),
                        cqlCode.getCodeId());
                VsacCode vsacCode = terminologyWebClient.getCode(codePath, getServiceTicket(tgt));
                if (vsacCode != null) {
                  cqlCode.setValid(true);
                }
              } else {
                throw new RuntimeException("Error while fetching vsac version");
              }
            }
          }
        } else {
          throw new RuntimeException("Error while fetching codeSystem entry from json");
        }
      }
      cqlCode.setValid(false);
    }
    return cqlCodes;
  }
}
