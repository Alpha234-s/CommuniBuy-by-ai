package hy.CommunityBuy;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import hy.CommunityBuy.repository.BlacklistRepository;
import hy.CommunityBuy.security.RevocationCleanupProperties;
import hy.CommunityBuy.service.RevocationCleanup;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class RevocationCleanupIntegrationTest extends IntegrationSupport {
  @Autowired BlacklistRepository blacklist;
  @Autowired JwtDecoder decoder;
  @Autowired ApplicationContext context;

  RevocationCleanup job() {
    return new RevocationCleanup(blacklist, clock, new RevocationCleanupProperties(60000, 2));
  }

  void row(String id, long expiry) {
    jdbc.update("insert into blacklist(token_hash,expire_at) values (?,?)", id, expiry);
  }

  @Test
  void indexedBoundedBatchesDeleteOnlyExpiredIncludingExactSecondBoundary() {
    assertThat(context.getBeansOfType(RevocationCleanup.class)).isEmpty();
    long now = clock.instant().getEpochSecond();
    row("old1", now - 3);
    row("old2", now - 2);
    row("old3", now - 1);
    row("boundary", now);
    row("future", now + 1);
    var plan =
        jdbc.queryForList(
            "explain delete from blacklist where expire_at<="
                + now
                + " order by expire_at,token_hash limit 2");
    assertThat(plan.getFirst().get("key")).isEqualTo("ix_blacklist_expiry");
    assertThat(job().cleanBatch()).isEqualTo(2);
    assertThat(blacklist.count()).isEqualTo(3);
    assertThat(blacklist.existsById("boundary")).isTrue();
    assertThat(blacklist.existsById("future")).isTrue();
    assertThat(job().cleanBatch()).isEqualTo(2);
    assertThat(blacklist.existsById("boundary")).isFalse();
    assertThat(blacklist.existsById("future")).isTrue();
    assertThat(job().cleanBatch()).isZero();
    clock.now.set(Instant.ofEpochSecond(now).plusNanos(999999999));
    assertThat(job().cleanBatch()).isZero();
    clock.now.set(Instant.ofEpochSecond(now + 1));
    assertThat(job().cleanBatch()).isEqualTo(1);
    assertThat(job().cleanBatch()).isZero();
  }

  @Test
  void liveRevocationsStayRejectedAndExpiredJwtStaysRejectedAfterItsRowIsDeleted()
      throws Exception {
    Instant now = clock.instant();
    String early = token("buyer", now, now.plusSeconds(10), "communibuy", "communibuy-api"),
        live = token("buyer");
    var earlyJwt = decoder.decode(early);
    var liveJwt = decoder.decode(live);
    auth.logout(earlyJwt);
    auth.logout(liveJwt);
    for (String jwt : new String[] {early, live})
      mvc.perform(get("/api/v1/user/buyer").header("Authorization", "Bearer " + jwt))
          .andExpect(status().isUnauthorized());
    assertThat(job().cleanBatch()).isZero();
    clock.now.set(now.plusSeconds(10));
    assertThat(job().cleanBatch()).isEqualTo(1);
    assertThat(blacklist.existsById(earlyJwt.getId())).isFalse();
    assertThat(blacklist.existsById(liveJwt.getId())).isTrue();
    for (String jwt : new String[] {early, live})
      mvc.perform(get("/api/v1/user/buyer").header("Authorization", "Bearer " + jwt))
          .andExpect(status().isUnauthorized());
    assertThat(job().cleanBatch()).isZero();
  }

  @Test
  void failedDeleteRollsBackWholeBatchAndCanBeRetriedNextRound() {
    long now = clock.instant().getEpochSecond();
    row("first", now - 2);
    row("second", now - 1);
    // A test-only child FK rejects the second ordered DELETE after the first row was processed.
    // This injects a real InnoDB statement failure without SUPER or global server configuration.
    jdbc.execute(
        "CREATE TABLE cleanup_test_guard(token_hash VARCHAR(64) NOT NULL, FOREIGN KEY(token_hash)"
            + " REFERENCES blacklist(token_hash)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            + " COLLATE=utf8mb4_bin");
    try {
      jdbc.update("insert into cleanup_test_guard(token_hash) values (?)", "second");
      assertThatThrownBy(() -> job().cleanBatch())
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      assertThat(blacklist.count()).isEqualTo(2);
      assertThat(blacklist.existsById("first")).isTrue();
      assertThat(blacklist.existsById("second")).isTrue();
    } finally {
      jdbc.execute("DROP TABLE cleanup_test_guard");
    }
    assertThat(job().cleanBatch()).isEqualTo(2);
    assertThat(job().cleanBatch()).isZero();
  }

  @Test
  void simultaneousCleanupRoundsRemainBoundedAndPreserveFutureRevocations() throws Exception {
    long now = clock.instant().getEpochSecond();
    row("old1", now - 3);
    row("old2", now - 2);
    row("old3", now - 1);
    row("future", now + 1);
    var results =
        this.<Integer>race(java.util.List.of(() -> job().cleanBatch(), () -> job().cleanBatch()));
    assertThat(results).allSatisfy(n -> assertThat(n).isBetween(0, 2));
    assertThat(results.stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
    assertThat(blacklist.count()).isEqualTo(1);
    assertThat(blacklist.existsById("future")).isTrue();
  }
}
