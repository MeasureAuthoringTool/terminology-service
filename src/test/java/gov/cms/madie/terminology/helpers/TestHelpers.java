package gov.cms.madie.terminology.helpers;

import ca.uhn.fhir.context.FhirContext;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Resource;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Objects;

public class TestHelpers {
  public static File getTestResourceFile(String resourcePath) {
    if (StringUtils.isEmpty(resourcePath)) {
      return null;
    }
    return new File(Objects.requireNonNull(TestHelpers.class.getResource(resourcePath)).getFile());
  }

  public static <T extends Resource> T getFhirTestResource(String resourcePath, Class<T> clazz)
      throws IOException {
    File file = getTestResourceFile(resourcePath);
    String resourceJson = new String(Files.readAllBytes(file.toPath()));
    return FhirContext.forR4().newJsonParser().parseResource(clazz, resourceJson);
  }
}
