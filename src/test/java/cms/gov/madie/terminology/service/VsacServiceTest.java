package cms.gov.madie.terminology.service;

import cms.gov.madie.terminology.dto.SearchParamsDTO;
import cms.gov.madie.terminology.exceptions.VsacGenericException;
import cms.gov.madie.terminology.helpers.TestHelpers;
import cms.gov.madie.terminology.webclient.TerminologyServiceWebClient;
import generated.vsac.nlm.nih.gov.RetrieveMultipleValueSetsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class VsacServiceTest {
  @Mock private TerminologyServiceWebClient terminologyWebClient;

  @InjectMocks private VsacService vsacService;

  private RetrieveMultipleValueSetsResponse svsValueSet;
  private SearchParamsDTO searchParamsDTO;

  @BeforeEach
  public void setup() throws JAXBException {
    File file = TestHelpers.getTestResourceFile("/value-sets/svs_office_visit.xml");
    JAXBContext jaxbContext = JAXBContext.newInstance(RetrieveMultipleValueSetsResponse.class);
    Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
    svsValueSet = (RetrieveMultipleValueSetsResponse) jaxbUnmarshaller.unmarshal(file);
    SearchParamsDTO.ValueSetParams valueSetParams = new SearchParamsDTO.ValueSetParams();
    valueSetParams.setOid("2.16.840.1.113883.3.464.1003.101.12.1001");
    searchParamsDTO =
        SearchParamsDTO.builder()
            .tgt("TGT-Xy4z-pQr-FaK3")
            .profile("eCQM Update 2030-05-05")
            .valueSetParams(List.of(valueSetParams))
            .build();
  }

  @Test
  public void testGetValueSets() throws ExecutionException, InterruptedException {
    when(terminologyWebClient.getServiceTicket(anyString())).thenReturn("ST-fake");
    when(terminologyWebClient.getValueSet(any(), any(), any(), any(), any(), any()))
        .thenReturn(svsValueSet);

    List<RetrieveMultipleValueSetsResponse> vsacValueSets =
        vsacService.getValueSets(searchParamsDTO);

    RetrieveMultipleValueSetsResponse.DescribedValueSet describedValueSet =
        vsacValueSets.get(0).getDescribedValueSet();
    assertThat(
        describedValueSet.getID(),
        is(equalTo(searchParamsDTO.getValueSetParams().get(0).getOid())));
    assertThat(describedValueSet.getDisplayName(), is(equalTo("Office Visit")));
    assertThat(describedValueSet.getConceptList().getConcepts().size(), is(equalTo(16)));
  }

  @Test
  public void testGetValueSetsWhenErrorOccurredWhileFetchingServiceTicket()
      throws ExecutionException, InterruptedException {
    doThrow(new InterruptedException()).when(terminologyWebClient).getServiceTicket(anyString());

    VsacGenericException exception =
        assertThrows(VsacGenericException.class, () -> vsacService.getValueSets(searchParamsDTO));

    assertThat(
        exception.getMessage(),
        is(
            equalTo(
                "Error occurred while fetching service ticket. Please make sure you are logged in to UMLS.")));
  }
}
