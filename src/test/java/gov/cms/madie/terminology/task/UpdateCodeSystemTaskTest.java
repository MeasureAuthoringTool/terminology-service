package gov.cms.madie.terminology.task;

import gov.cms.madie.terminology.models.CodeSystemJobExecutionState;
import gov.cms.madie.terminology.service.CodeSystemJobExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateCodeSystemTaskTest {

  private static final String HARP_ID = "test-harp-id";

  @Mock private CodeSystemAsyncWorker codeSystemAsyncWorker;
  @Mock private CodeSystemJobExecutionService codeSystemJobExecutionService;

  @InjectMocks private UpdateCodeSystemTask updateCodeSystemTask;

  @Test
  void testStartJobRunsSuccessfully() {
    CodeSystemJobExecutionState execution = CodeSystemJobExecutionState.builder().id("job-1").build();
    when(codeSystemJobExecutionService.start(HARP_ID)).thenReturn(execution);

    boolean result = updateCodeSystemTask.startJob(HARP_ID);

    assertTrue(result);
    assertTrue(updateCodeSystemTask.isRunning());
    verify(codeSystemJobExecutionService, times(1)).start(HARP_ID);
    verify(codeSystemAsyncWorker, times(1)).runJobAsync(any(), eq(execution));
  }

  @Test
  void testUpdateCodeSystemsTriggersWorkerAsSystem() {
    updateCodeSystemTask.updateCodeSystems();

    verify(codeSystemJobExecutionService, times(1))
        .start(UpdateCodeSystemTask.SCHEDULED_TRIGGERED_BY);
    verify(codeSystemAsyncWorker, times(1)).runJobAsync(any(), any());
  }

  @Test
  void testStartJobReturnsFalseWhenAlreadyRunning() {
    boolean firstResult = updateCodeSystemTask.startJob(HARP_ID);
    boolean secondResult = updateCodeSystemTask.startJob("another-harp-id");

    assertTrue(firstResult);
    assertFalse(secondResult);

    verify(codeSystemJobExecutionService, times(1)).start(anyString());
    verify(codeSystemAsyncWorker, times(1)).runJobAsync(any(), any());
  }

  @Test
  void testStartJobReleasesFlagWhenRecordCannotBeCreated() {
    when(codeSystemJobExecutionService.start(HARP_ID))
        .thenThrow(new RuntimeException("Mongo is down"));

    assertThrows(RuntimeException.class, () -> updateCodeSystemTask.startJob(HARP_ID));

    assertFalse(updateCodeSystemTask.isRunning());
    verifyNoInteractions(codeSystemAsyncWorker);
  }

  @Test
  void testStartJobReleasesFlagWhenWorkerCannotBeStarted() {
    doThrow(new RuntimeException("Executor rejected the task"))
        .when(codeSystemAsyncWorker)
        .runJobAsync(any(), any());

    assertThrows(RuntimeException.class, () -> updateCodeSystemTask.startJob(HARP_ID));

    assertFalse(updateCodeSystemTask.isRunning());
  }

  @Test
  void testIsRunningReturnsCorrectInitialState() {
    assertFalse(updateCodeSystemTask.isRunning());
  }
}
