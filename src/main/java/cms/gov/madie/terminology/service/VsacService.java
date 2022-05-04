package cms.gov.madie.terminology.service;

import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cms.gov.madie.terminology.webclient.TerminologyServiceWebClient;
import lombok.extern.slf4j.Slf4j;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;

@Service
@Slf4j
public class VsacService {

  private final TerminologyServiceWebClient terminologyWebClient;

  @Autowired
  public VsacService(TerminologyServiceWebClient terminologyWebClient) {
    this.terminologyWebClient = terminologyWebClient;
  }

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
}
