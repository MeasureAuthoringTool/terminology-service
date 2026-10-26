package gov.cms.madie.terminology.task;

import gov.cms.madie.terminology.service.ValueSetExpansionService;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VsesValuesetScheduledJob {

  private final ValueSetExpansionService valueSetExpansionService;

  @Scheduled(cron = "${vses.valueset-update.cron}", zone = "${vses.valueset-update.zone}")
  public void runWeeklyValuesetUpdate() {
    Instant start = Instant.now();
    log.info(
        "VSES_VALUESET_UPDATE_JOB_START - Weekly scheduled valueset update starting at {}", start);

    try {
      CompletableFuture<Integer> update = valueSetExpansionService.updateValueSetDependencies();
      update.whenComplete(
          (savedValueSetCount, failure) -> {
            long durationMillis = Duration.between(start, Instant.now()).toMillis();
            if (failure == null) {
              log.info(
                  "VSES_VALUESET_UPDATE_JOB_SUCCESS - Weekly valueset update completed "
                      + "successfully: {} value sets saved in {}ms",
                  savedValueSetCount,
                  durationMillis);
            } else {
              log.error(
                  "VSES_VALUESET_UPDATE_JOB_FAILURE - Weekly valueset update failed in {}ms",
                  durationMillis,
                  failure);
            }
          });
    } catch (Exception e) {
      log.error(
          "VSES_VALUESET_UPDATE_JOB_FAILURE - Weekly valueset update failed in {}ms",
          Duration.between(start, Instant.now()).toMillis(),
          e);
    }
  }
}
