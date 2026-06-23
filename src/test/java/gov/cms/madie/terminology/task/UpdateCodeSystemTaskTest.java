package gov.cms.madie.terminology.task;

import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UpdateCodeSystemTaskTest {
  @Mock private FhirTerminologyService fhirTerminologyService;
  @InjectMocks UpdateCodeSystemTask updateCodeSystemTask;
  String value = "abc" + "-test";
  @BeforeEach
  void setup() {
    // set apiKey manually since @Value won't inject in unit test
    ReflectionTestUtils.setField(updateCodeSystemTask, "apiKey", value);
  }

  @Test
  void testUpdateCodeSystemsRunsSuccessfully() {
    when(fhirTerminologyService.retrieveAllCodeSystems(any(UmlsUser.class)))
            .thenReturn(List.of(new CodeSystem()));

    updateCodeSystemTask.updateCodeSystems();

    verify(fhirTerminologyService, times(1))
            .retrieveAllCodeSystems(any(UmlsUser.class));
  }

  @Test
  void testUpdateCodeSystemsSkipsWhenAlreadyRunning() {
    // running = true
    when(fhirTerminologyService.retrieveAllCodeSystems(any()))
            .thenAnswer(inv -> {
              // still running
              updateCodeSystemTask.updateCodeSystems();
              return List.of();
            });

    updateCodeSystemTask.updateCodeSystems();

    verify(fhirTerminologyService, times(1))
            .retrieveAllCodeSystems(any());
  }

  @Test
  void testRunJobAsyncHandlesExceptionAndResetsRunningFlag() {
    when(fhirTerminologyService.retrieveAllCodeSystems(any()))
            .thenThrow(new RuntimeException("failure"));

    updateCodeSystemTask.updateCodeSystems();

    updateCodeSystemTask.updateCodeSystems();

    verify(fhirTerminologyService, times(2))
            .retrieveAllCodeSystems(any());
  }

  @Test
  void testRunJobAsyncCallsServiceWithApiKeySet() {
    updateCodeSystemTask.updateCodeSystems();

    verify(fhirTerminologyService).retrieveAllCodeSystems(argThat(user ->
            user != null && value.equals(user.getApiKey())
    ));
  }

  @Test
  void testIsRunningReturnsCorrectState() {
    // initially false
    assert !updateCodeSystemTask.isRunning();

    // trigger task
    updateCodeSystemTask.updateCodeSystems();

    assert !updateCodeSystemTask.isRunning();
  }

}
