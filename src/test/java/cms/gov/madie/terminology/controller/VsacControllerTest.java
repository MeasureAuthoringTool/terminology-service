package cms.gov.madie.terminology.controller;

import cms.gov.madie.terminology.service.VsacService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
public class VsacControllerTest {

  @Mock private VsacService vsacService;
  @InjectMocks private VsacController vsacController;

  private static final String TEST = "test";

  @Test
  void testGetValueSetFailWhenGettingValueSetFailed() {
    doThrow(new WebClientResponseException(401, "Error", null, null, null))
        .when(vsacService)
        .getValueSet(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    assertThrows(
        WebClientResponseException.class,
        () -> vsacController.getValueSet(TEST, TEST, TEST, TEST, TEST, TEST));
  }
}
