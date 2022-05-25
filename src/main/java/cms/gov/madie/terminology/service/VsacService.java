package cms.gov.madie.terminology.service;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import cms.gov.madie.terminology.dto.ValueSetsSearchCriteria;
import cms.gov.madie.terminology.exceptions.VsacGenericException;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cms.gov.madie.terminology.mapper.VsacToFhirValueSetMapper;
import cms.gov.madie.terminology.webclient.TerminologyServiceWebClient;
import lombok.extern.slf4j.Slf4j;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;

@Service
@Slf4j
public class VsacService {

  private final TerminologyServiceWebClient terminologyWebClient;
  private final VsacToFhirValueSetMapper vsacToFhirValueSetMapper;

  @Autowired
  public VsacService(
      TerminologyServiceWebClient terminologyWebClient,
      VsacToFhirValueSetMapper vsacToFhirValueSetMapper) {
    this.terminologyWebClient = terminologyWebClient;
    this.vsacToFhirValueSetMapper = vsacToFhirValueSetMapper;
  }

  public String getServiceTicket(String tgt) throws InterruptedException, ExecutionException {
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
}
