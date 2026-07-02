package gov.cms.madie.terminology.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateCodeSystemTaskTest {

  @Mock private CodeSystemAsyncWorker codeSystemAsyncWorker;

  @InjectMocks private UpdateCodeSystemTask updateCodeSystemTask;

  @Test
  void testStartJobRunsSuccessfully() {
    boolean result = updateCodeSystemTask.startJob();

    assertTrue(result);

    verify(codeSystemAsyncWorker, times(1)).runJobAsync(any());
  }

  @Test
  void testUpdateCodeSystemsTriggersWorker() {
    updateCodeSystemTask.updateCodeSystems();

    verify(codeSystemAsyncWorker, times(1)).runJobAsync(any());
  }

  @Test
  void testStartJobReturnsFalseWhenAlreadyRunning() {
    boolean firstResult = updateCodeSystemTask.startJob();
    boolean secondResult = updateCodeSystemTask.startJob();

    assertTrue(firstResult);
    assertFalse(secondResult);

    verify(codeSystemAsyncWorker, times(1)).runJobAsync(any());
  }

  @Test
  void testIsRunningReturnsCorrectInitialState() {
    assertFalse(updateCodeSystemTask.isRunning());
  }
}
