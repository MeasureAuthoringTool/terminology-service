package gov.cms.madie.terminology.task;

import gov.cms.madie.terminology.models.CodeSystemJobExecutionState;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.CodeSystemJobExecutionService;
import gov.cms.madie.terminology.service.CodeSystemSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeSystemAsyncWorkerTest {

  @Mock private CodeSystemSyncService codeSystemSyncService;
  @Mock private CodeSystemJobExecutionService codeSystemJobExecutionService;

  @InjectMocks private CodeSystemAsyncWorker codeSystemAsyncWorker;

  private static final String API_KEY = "abc-test";

  private final CodeSystemJobExecutionState execution =
      CodeSystemJobExecutionState.builder().id("job-1").build();

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(codeSystemAsyncWorker, "apiKey", API_KEY);
  }

  @Test
  void testRunJobAsyncRunsSuccessfully() {
    AtomicBoolean running = new AtomicBoolean(true);

    codeSystemAsyncWorker.runJobAsync(running, execution);

    verify(codeSystemSyncService, times(1)).remoteRetrieveAllCodeSystems(any(UmlsUser.class));
    verify(codeSystemJobExecutionService, times(1)).complete(execution);
    verify(codeSystemJobExecutionService, never()).fail(any());
    assertFalse(running.get());
  }

  @Test
  void testRunJobAsyncCallsServiceWithApiKeySet() {
    AtomicBoolean running = new AtomicBoolean(true);

    codeSystemAsyncWorker.runJobAsync(running, execution);

    verify(codeSystemSyncService)
        .remoteRetrieveAllCodeSystems(
            argThat(user -> user != null && API_KEY.equals(user.getApiKey())));
  }

  @Test
  void testRunJobAsyncHandlesExceptionAndResetsRunningFlag() {
    AtomicBoolean running = new AtomicBoolean(true);

    doThrow(new IllegalStateException("1 code system(s) failed to save"))
        .when(codeSystemSyncService)
        .remoteRetrieveAllCodeSystems(any(UmlsUser.class));

    codeSystemAsyncWorker.runJobAsync(running, execution);

    verify(codeSystemJobExecutionService, times(1)).fail(execution);
    verify(codeSystemJobExecutionService, never()).complete(any());
    assertFalse(running.get());
  }

  @Test
  void testRunJobAsyncFailsJobWhenRecordingCompletionFails() {
    AtomicBoolean running = new AtomicBoolean(true);

    doThrow(new RuntimeException("Mongo is down"))
        .when(codeSystemJobExecutionService)
        .complete(execution);

    codeSystemAsyncWorker.runJobAsync(running, execution);

    verify(codeSystemJobExecutionService, times(1)).fail(execution);
    assertFalse(running.get());
  }

  @Test
  void testRunJobAsyncResetsRunningFlagOnError() {
    AtomicBoolean running = new AtomicBoolean(true);

    doThrow(new StackOverflowError())
        .when(codeSystemSyncService)
        .remoteRetrieveAllCodeSystems(any(UmlsUser.class));

    assertThrows(
        StackOverflowError.class, () -> codeSystemAsyncWorker.runJobAsync(running, execution));

    verifyNoInteractions(codeSystemJobExecutionService);
    assertFalse(running.get());
  }
}
