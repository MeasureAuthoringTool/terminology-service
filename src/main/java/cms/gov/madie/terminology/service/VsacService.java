package cms.gov.madie.terminology.service;

import cms.gov.madie.terminology.dto.VsacCode;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.stereotype.Service;

import cms.gov.madie.terminology.mapper.VsacToFhirValueSetMapper;
import cms.gov.madie.terminology.webclient.TerminologyServiceWebClient;
import lombok.extern.slf4j.Slf4j;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;

@Service
@Slf4j
@RequiredArgsConstructor
public class VsacService {

  private final TerminologyServiceWebClient terminologyWebClient;
  private final VsacToFhirValueSetMapper vsacToFhirValueSetMapper;

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

  public VsacCode getCode(String codePath, String tgt) {
    return terminologyWebClient.getCode(codePath, getServiceTicket(tgt));
  }
}
