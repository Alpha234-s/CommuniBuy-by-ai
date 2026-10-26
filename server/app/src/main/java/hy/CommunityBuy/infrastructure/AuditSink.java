package hy.CommunityBuy.infrastructure;

import hy.CommunityBuy.commerce.Amounts;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@RequiredArgsConstructor
public class AuditSink {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public void success(String actor, String action, String resource) {
    record(actor, action, resource, "SUCCESS", 200);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failure(String actor, String action, String resource, int status) {
    record(actor, action, resource, "FAILURE", status);
  }

  private void record(String actor, String action, String resource, String outcome, int status) {
    String trace = MDC.get("traceId");
    if (trace == null) trace = UUID.randomUUID().toString().replace("-", "");
    jdbc.update(
        "insert into"
            + " audit_events(create_time,trace_id,actor_hash,action,resource_hash,outcome,http_status)"
            + " values (?,?,?,?,?,?,?)",
        clock.instant().getEpochSecond(),
        trace,
        Amounts.hash(actor),
        action,
        Amounts.hash(resource),
        outcome,
        status);
  }
}
