package hy.CommunityBuy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import hy.CommunityBuy.repository.BlacklistRepository;
import hy.CommunityBuy.security.RevocationCleanupProperties;
import hy.CommunityBuy.service.RevocationCleanup;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;

class RevocationCleanupTest {
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(RevocationCleanupProperties.class)
  @Import(RevocationCleanup.class)
  static class Config {
    @Bean
    BlacklistRepository blacklist() {
      return mock(BlacklistRepository.class);
    }

    @Bean
    Clock clock() {
      return Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    }
  }

  final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @ParameterizedTest
  @ValueSource(strings = {"batch-size=0", "batch-size=10001", "delay-ms=999", "delay-ms=86400001"})
  void invalidBatchOrIntervalFailsStartupEvenWhenSchedulingDisabled(String setting) {
    runner
        .withPropertyValues(
            "app.auth.revocation-cleanup.enabled=false", "app.auth.revocation-cleanup." + setting)
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasRootCauseInstanceOf(BindValidationException.class));
  }

  @Test
  void defaultsAndBothValidBoundariesBindAndSchedulingCanBeDisabled() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed().hasSingleBean(RevocationCleanup.class);
          assertThat(context.getBean(RevocationCleanupProperties.class))
              .isEqualTo(new RevocationCleanupProperties(60000, 500));
        });
    for (int[] boundary : new int[][] {{1000, 1}, {86400000, 10000}})
      runner
          .withPropertyValues(
              "app.auth.revocation-cleanup.delay-ms=" + boundary[0],
              "app.auth.revocation-cleanup.batch-size=" + boundary[1],
              "app.auth.revocation-cleanup.enabled=false")
          .run(
              context -> {
                assertThat(context).hasNotFailed().doesNotHaveBean(RevocationCleanup.class);
                assertThat(context.getBean(RevocationCleanupProperties.class))
                    .isEqualTo(new RevocationCleanupProperties(boundary[0], boundary[1]));
              });
  }

  @Test
  void failedSweepLogsSafePositionAndNextRoundRetriesWithBoundedWork() {
    var repository = mock(BlacklistRepository.class);
    when(repository.deleteExpired(anyLong(), anyInt()))
        .thenThrow(SafeDiagnosticsTest.failure())
        .thenReturn(2);
    var job =
        new RevocationCleanup(
            repository,
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            new RevocationCleanupProperties(60000, 2));
    try (var capture = new SafeDiagnosticsTest.Capture(RevocationCleanup.class.getName())) {
      job.sweep();
      job.sweep();
      verify(repository, times(2)).deleteExpired(0, 2);
      assertThat(capture.messages())
          .contains(
              "event=auth.revocations.deferred",
              "SafeDiagnosticsTest#failure:",
              "event=auth.revocations.cleanup deleted=2")
          .doesNotContain("password-secret", "delivery-address-secret", SafeDiagnosticsTest.TOKEN);
      assertThat(capture.list)
          .hasSize(2)
          .allSatisfy(
              event -> {
                assertThat(event.getMDCPropertyMap().get("traceId")).matches("[a-f0-9]{32}");
                assertThat(event.getThrowableProxy()).isNull();
              });
      assertThat(MDC.get("traceId")).isNull();
    }
  }
}
