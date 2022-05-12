package cms.gov.madie.terminology.service;

import java.util.concurrent.ExecutionException;

import cms.gov.madie.terminology.dto.CqlCode;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.stereotype.Service;

import cms.gov.madie.terminology.mapper.VsacToFhirValueSetMapper;
import cms.gov.madie.terminology.webclient.TerminologyServiceWebClient;
import lombok.extern.slf4j.Slf4j;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class VsacService {

  private final TerminologyServiceWebClient terminologyWebClient;
  private final VsacToFhirValueSetMapper vsacToFhirValueSetMapper;

  public String getServiceTicket(String tgt) throws InterruptedException, ExecutionException {
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

  public String getCode(CqlCode cqlCode, String tgt)
      throws ExecutionException, InterruptedException {
    // split code system and its version
    // "/CodeSystem/LOINC22/Version/2.67/Code/21112-8/Info";
    String[] parts = cqlCode.getCodeSystem().split(":");
    String codeSystemName = parts[0]; // 004
    codeSystemName = codeSystemName.replaceAll("^\"|\"$", "");
    String codeSystemVersion = parts[1]; // 034556
    codeSystemVersion = codeSystemVersion.replaceAll("^\"|\"$", "");
    String codeId = cqlCode.getCodeId().replaceAll("\'","");
    String path =
        "/CodeSystem/"
            + codeSystemName
            + "/Version/"
            + codeSystemVersion
            + "/Code/"
            + codeId
            + "/Info";
    return terminologyWebClient.getCode(path, tgt);
  }
}
