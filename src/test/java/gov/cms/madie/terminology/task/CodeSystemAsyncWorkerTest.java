package gov.cms.madie.terminology.task;

import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.CodeSystemSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeSystemAsyncWorkerTest {

  @Mock private CodeSystemSyncService codeSystemSyncService;

  @InjectMocks private CodeSystemAsyncWorker codeSystemAsyncWorker;

  private static final String API_KEY = "abc-test";

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(codeSystemAsyncWorker, "apiKey", API_KEY);
  }

  @Test
  void testRunJobAsyncRunsSuccessfully() {
    AtomicBoolean running = new AtomicBoolean(true);

    when(codeSystemSyncService.remoteRetrieveAllCodeSystems(any(UmlsUser.class)))
        .thenReturn(List.of(new CodeSystem()));

    codeSystemAsyncWorker.runJobAsync(running);

    verify(codeSystemSyncService, times(1)).remoteRetrieveAllCodeSystems(any(UmlsUser.class));

    assertFalse(running.get());
  }

  @Test
  void testRunJobAsyncCallsServiceWithApiKeySet() {
    AtomicBoolean running = new AtomicBoolean(true);

    codeSystemAsyncWorker.runJobAsync(running);

    verify(codeSystemSyncService)
        .remoteRetrieveAllCodeSystems(
            argThat(user -> user != null && API_KEY.equals(user.getApiKey())));
  }

  @Test
  void testRunJobAsyncHandlesExceptionAndResetsRunningFlag() {
    AtomicBoolean running = new AtomicBoolean(true);

    when(codeSystemSyncService.remoteRetrieveAllCodeSystems(any(UmlsUser.class)))
        .thenThrow(new RuntimeException("failure"));

    codeSystemAsyncWorker.runJobAsync(running);

    verify(codeSystemSyncService, times(1)).remoteRetrieveAllCodeSystems(any(UmlsUser.class));

    assertFalse(running.get());
  }
}
