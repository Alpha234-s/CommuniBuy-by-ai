package hy.CommunityBuy.infrastructure;

import hy.CommunityBuy.dto.*;
import hy.CommunityBuy.dto.Dtos.PageView;
import hy.CommunityBuy.security.Actor;
import jakarta.validation.constraints.*;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Validated
public class AuditController {
  private final JdbcTemplate jdbc;

  @GetMapping("/api/v1/audit")
  public Api<PageView<Map<String, Object>>> audit(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    Actor.current().requireAdmin();
    return Api.ok(
        new PageView<>(
            jdbc.queryForList(
                "select * from audit_events order by id desc limit ? offset ?", size, page * size),
            jdbc.queryForObject("select count(*) from audit_events", Long.class),
            page,
            size));
  }
}
