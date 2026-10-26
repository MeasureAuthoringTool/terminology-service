package gov.cms.madie.terminology.task;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import gov.cms.madie.terminology.service.ValueSetExpansionService;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

@ExtendWith(MockitoExtension.class)
class VsesValuesetScheduledJobTest {

  @Mock private ValueSetExpansionService valueSetExpansionService;

  @InjectMocks private VsesValuesetScheduledJob scheduledJob;

  private Logger logger;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(VsesValuesetScheduledJob.class);
    logAppender = new ListAppender<>();
    logAppender.setContext(logger.getLoggerContext());
    logAppender.start();
    logger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(logAppender);
    logAppender.stop();
  }

  @Test
  void runWeeklyValuesetUpdateCallsServiceOnceAndLogsSuccess() {
    // given
    when(valueSetExpansionService.updateValueSetDependencies())
        .thenReturn(CompletableFuture.completedFuture(3));

    // when
    scheduledJob.runWeeklyValuesetUpdate();

    // then
    verify(valueSetExpansionService, times(1)).updateValueSetDependencies();
    assertLogContains("VSES_VALUESET_UPDATE_JOB_START");
    assertLogContains(
        "VSES_VALUESET_UPDATE_JOB_SUCCESS - Weekly valueset update completed successfully: 3 value sets saved");
  }

  @Test
  void runWeeklyValuesetUpdateLogsSynchronousFailureAndReturnsNormally() {
    // given
    RuntimeException failure = new RuntimeException("update failed");
    when(valueSetExpansionService.updateValueSetDependencies()).thenThrow(failure);

    // when
    assertDoesNotThrow(scheduledJob::runWeeklyValuesetUpdate);

    // then
    verify(valueSetExpansionService, times(1)).updateValueSetDependencies();
    ILoggingEvent failureEvent = findLog("VSES_VALUESET_UPDATE_JOB_FAILURE");
    assertNotNull(failureEvent);
    assertNotNull(failureEvent.getThrowableProxy());
  }

  @Test
  void runWeeklyValuesetUpdateLogsAsynchronousFailure() {
    // given
    RuntimeException failure = new RuntimeException("async update failed");
    when(valueSetExpansionService.updateValueSetDependencies())
        .thenReturn(CompletableFuture.failedFuture(failure));

    // when
    assertDoesNotThrow(scheduledJob::runWeeklyValuesetUpdate);

    // then
    ILoggingEvent failureEvent = findLog("VSES_VALUESET_UPDATE_JOB_FAILURE");
    assertNotNull(failureEvent);
    assertNotNull(failureEvent.getThrowableProxy());
  }

  @Test
  void configuredCronRunsAtMondayOneAmInNewYorkTime() throws NoSuchMethodException {
    // given
    YamlPropertiesFactoryBean yamlPropertiesFactory = new YamlPropertiesFactoryBean();
    yamlPropertiesFactory.setResources(new ClassPathResource("application.yml"));
    Properties properties = yamlPropertiesFactory.getObject();
    assertNotNull(properties);

    MutablePropertySources propertySources = new MutablePropertySources();
    propertySources.addFirst(new PropertiesPropertySource("application", properties));
    PropertySourcesPropertyResolver propertyResolver =
        new PropertySourcesPropertyResolver(propertySources);
    String cron =
        propertyResolver.resolveRequiredPlaceholders(
            properties.getProperty("vses.valueset-update.cron"));
    ZoneId zone =
        ZoneId.of(
            propertyResolver.resolveRequiredPlaceholders(
                properties.getProperty("vses.valueset-update.zone")));
    Scheduled scheduled =
        VsesValuesetScheduledJob.class
            .getMethod("runWeeklyValuesetUpdate")
            .getAnnotation(Scheduled.class);

    // when
    ZonedDateTime nextExecution =
        CronExpression.parse(cron).next(ZonedDateTime.of(2026, 10, 23, 2, 0, 0, 0, zone));

    // then
    assertEquals("America/New_York", zone.getId());
    assertEquals("${vses.valueset-update.cron}", scheduled.cron());
    assertEquals("${vses.valueset-update.zone}", scheduled.zone());
    assertNotNull(nextExecution);
    assertEquals(DayOfWeek.MONDAY, nextExecution.getDayOfWeek());
    assertEquals(LocalTime.of(1, 0), nextExecution.toLocalTime());
    assertEquals(ZonedDateTime.of(2026, 10, 26, 1, 0, 0, 0, zone), nextExecution);
  }

  private void assertLogContains(String marker) {
    assertTrue(
        logAppender.list.stream().anyMatch(event -> event.getFormattedMessage().contains(marker)),
        "Expected log marker: " + marker);
  }

  private ILoggingEvent findLog(String marker) {
    return logAppender.list.stream()
        .filter(event -> event.getFormattedMessage().contains(marker))
        .findFirst()
        .orElse(null);
  }
}
