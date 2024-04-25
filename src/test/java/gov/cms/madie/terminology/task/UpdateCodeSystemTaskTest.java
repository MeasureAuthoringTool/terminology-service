package gov.cms.madie.terminology.task;

import gov.cms.madie.terminology.models.CodeSystem;
import gov.cms.madie.terminology.models.UmlsUser;
import gov.cms.madie.terminology.service.FhirTerminologyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UpdateCodeSystemTaskTest {
  @Mock private FhirTerminologyService fhirTerminologyService;
  @InjectMocks UpdateCodeSystemTask updateCodeSystemTask;
  @Test
  void updateCodeSystemTaskTest() {
    UmlsUser umlsUser = new UmlsUser();
    List<CodeSystem> codeSystems = Arrays.asList(new CodeSystem(), new CodeSystem());
    updateCodeSystemTask.updateCodeSystems();
    verify(fhirTerminologyService).retrieveAllCodeSystems(umlsUser);
  }
}
