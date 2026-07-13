package gov.cms.madie.terminology.config.migrations;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.util.ClasspathUtil;
import gov.cms.madie.terminology.models.MadieValueSet;
import gov.cms.madie.terminology.repositories.ValueSetExpansionRepository;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.ValueSet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@ChangeUnit(id = "load-value-sets-from-tx-qicore6-zip", order = "1", author = "madie-dev")
@Slf4j
public class LoadValueSetsFromCustomZip {

  private final List<MadieValueSet> existingValueSets = new ArrayList<>();

  @Execution
  public void apply(ValueSetExpansionRepository vsesRepo) throws IOException {
    Instant now = Instant.now();
    IParser xmlParser = FhirContext.forR4().newXmlParser();
    IParser jsonParser = FhirContext.forR4().newJsonParser();

    // Backup existing VSES stored value sets
    existingValueSets.addAll(vsesRepo.findAll());

    // Load value sets from the custom zip file
    List<ValueSet> zippedExpansions = loadValueSetsFromCustomZip(xmlParser);
    List<MadieValueSet> madieValueSets = new ArrayList<>();
    for (ValueSet valueSet : zippedExpansions) {
      if (vsesRepo.findByUrlAndVersion(valueSet.getUrl(), valueSet.getVersion()).isEmpty()) {
        MadieValueSet madieValueSet =
            MadieValueSet.builder()
                .url(valueSet.getUrl())
                .version(valueSet.getVersion())
                .valueSet(jsonParser.encodeResourceToString(valueSet))
                .manuallyModified(true)
                .lastUpdated(now)
                .build();
        vsesRepo.save(madieValueSet);
        madieValueSets.add(madieValueSet);
      }
    }
    // Reduce log noise by looping over the value sets post serialization.
    for (MadieValueSet madieValueSet : madieValueSets) {
      log.info("Loading ValueSet {}|{}", madieValueSet.getUrl(), madieValueSet.getVersion());
    }
    log.info("Loaded {} ValueSets", madieValueSets.size());
  }

  private List<ValueSet> loadValueSetsFromCustomZip(IParser xmlParser) throws IOException {
    List<ValueSet> valueSets = new ArrayList<>();
    String FILE_PATH = "/tx-qicore-6.0.0.zip";
    try (InputStream is = ClasspathUtil.loadResourceAsStream(FILE_PATH);
        ZipInputStream zipInputStream = new ZipInputStream(is)) {
      ZipEntry entry;
      while ((entry = zipInputStream.getNextEntry()) != null) {
        if (!entry.isDirectory()
            && !entry.getName().startsWith("__MACOSX")
            && !entry.getName().endsWith(".DS_Store")) {

          StringBuilder fileContent = new StringBuilder();
          byte[] buffer = new byte[1024];
          int read;
          while ((read = zipInputStream.read(buffer)) != -1) {
            fileContent.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
          }

          String xmlContent = fileContent.toString();
          if (xmlContent.startsWith("\uFEFF")) {
            xmlContent = xmlContent.substring(1);
          }
          IBaseResource baseResource = xmlParser.parseResource(xmlContent);
          if (baseResource instanceof ValueSet valueset) {
            valueSets.add(valueset);
          }
        }
        zipInputStream.closeEntry();
      }
    }
    return valueSets;
  }

  @RollbackExecution
  public void rollback(ValueSetExpansionRepository vsesRepo) {
    if (CollectionUtils.isNotEmpty(existingValueSets)) {
      vsesRepo.deleteAll(vsesRepo.findAll());
      vsesRepo.saveAll(existingValueSets);
    }
  }
}
