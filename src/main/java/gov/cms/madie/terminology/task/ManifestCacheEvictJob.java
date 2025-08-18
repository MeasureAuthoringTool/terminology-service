package gov.cms.madie.terminology.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class ManifestCacheEvictJob {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /** Clears the manifest-list cache every day at 3:30 AM */
  @CacheEvict(value = "manifest-list", allEntries = true)
  @Scheduled(cron = "0 30 3 * * ?")
  public void clearManifestCache() {
    String timestamp = LocalDateTime.now().format(FORMATTER);
    log.info("manifest-list cache cleared on {}", timestamp);
  }
}
