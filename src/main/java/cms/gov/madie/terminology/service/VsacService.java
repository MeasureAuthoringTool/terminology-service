package cms.gov.madie.terminology.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cms.gov.madie.terminology.mapper.VsacToFhirValueSetMapper;
import cms.gov.madie.terminology.models.UmlsUser;
import cms.gov.madie.terminology.repositories.UmlsUserRepository;
import cms.gov.madie.terminology.webclient.TerminologyServiceWebClient;
import lombok.extern.slf4j.Slf4j;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;

@Service
@Slf4j
public class VsacService {

  private final TerminologyServiceWebClient terminologyWebClient;
  private final VsacToFhirValueSetMapper vsacToFhirValueSetMapper;
  private final UmlsUserRepository umlsUserRepository;

  @Autowired
  public VsacService(
      TerminologyServiceWebClient terminologyWebClient,
      VsacToFhirValueSetMapper vsacToFhirValueSetMapper,
      UmlsUserRepository umlsUserRepository) {
    this.terminologyWebClient = terminologyWebClient;
    this.vsacToFhirValueSetMapper = vsacToFhirValueSetMapper;
    this.umlsUserRepository = umlsUserRepository;
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

  public ValueSet convertToFHIRValueSet(
      RetrieveMultipleValueSetsResponse vsacValuesetResponse, String oid) {
    return vsacToFhirValueSetMapper.convertToFHIRValueSet(vsacValuesetResponse, oid);
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
