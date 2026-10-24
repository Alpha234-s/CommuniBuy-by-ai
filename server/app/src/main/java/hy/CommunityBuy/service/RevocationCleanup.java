package hy.CommunityBuy.service;

import hy.CommunityBuy.infrastructure.SafeDiagnostics;
import hy.CommunityBuy.repository.BlacklistRepository;
import hy.CommunityBuy.security.RevocationCleanupProperties;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "app.auth.revocation-cleanup.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RevocationCleanup {
  private static final Logger log = LoggerFactory.getLogger(RevocationCleanup.class);
  private final BlacklistRepository blacklist;
  private final Clock clock;
  private final RevocationCleanupProperties settings;

  // The repository proxy opens/commits one independent transaction, including when called by sweep.
  public int cleanBatch() {
    return blacklist.deleteExpired(clock.instant().getEpochSecond(), settings.batchSize());
  }

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(
      initialDelayString = "${app.auth.revocation-cleanup.delay-ms:60000}",
      fixedDelayString = "${app.auth.revocation-cleanup.delay-ms:60000}")
  public void sweep() {
    try (var ignored = SafeDiagnostics.traceScope()) {
      try {
        int deleted = cleanBatch();
        if (deleted > 0)
          log.info(
              "event=auth.revocations.cleanup deleted={} traceId={}", deleted, MDC.get("traceId"));
      } catch (RuntimeException error) {
        log.warn(
            "event=auth.revocations.deferred traceId={} diagnostic={}",
            MDC.get("traceId"),
            SafeDiagnostics.describe(error));
      }
    }
  }
}
