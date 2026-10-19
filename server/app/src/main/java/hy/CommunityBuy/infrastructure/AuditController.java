package hy.CommunityBuy.infrastructure;

import hy.CommunityBuy.dto.*;
import hy.CommunityBuy.dto.Dtos.PageView;
import hy.CommunityBuy.security.Actor;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Validated
public class AuditController {
  private final JdbcTemplate jdbc;

  public record AuditView(
      long id,
      long createTime,
      String traceId,
      String actorHash,
      String action,
      String resourceHash,
      String outcome,
      int httpStatus) {}

  @GetMapping("/api/v1/audit")
  public Api<PageView<AuditView>> audit(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    Actor.current().requireAdmin();
    return Api.ok(
        new PageView<>(
            jdbc.query(
                "select id,create_time,trace_id,actor_hash,action,resource_hash,outcome,http_status"
                    + " from audit_events order by id desc limit ? offset ?",
                (rs, row) ->
                    new AuditView(
                        rs.getLong(1),
                        rs.getLong(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getString(7),
                        rs.getInt(8)),
                size,
                page * size),
            jdbc.queryForObject("select count(*) from audit_events", Long.class),
            page,
            size));
  }
}
