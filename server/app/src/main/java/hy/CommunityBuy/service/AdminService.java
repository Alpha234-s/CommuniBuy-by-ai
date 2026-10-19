package hy.CommunityBuy.service;

import hy.CommunityBuy.dto.Dtos.Stats;
import hy.CommunityBuy.repository.*;
import hy.CommunityBuy.security.Actor;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {
  private final UserRepository users;
  private final ItemRepository items;
  private final OrderRepository orders;
  private final Clock clock;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public Stats stats(Actor actor) {
    actor.requireAdmin();
    var paid = amount("select coalesce(sum(amount),0) from payments where status='SUCCEEDED'");
    var refunded = amount("select coalesce(sum(amount),0) from refunds where status='SUCCEEDED'");
    var revenue = amount("select coalesce(sum(total),0) from orders where status in(1,2,3)");
    long legacy =
        jdbc.queryForObject(
            "select count(*) from orders o where o.pay_time is not null and not exists(select 1"
                + " from payments p where p.order_id=o.id)",
            Long.class);
    return new Stats(
        users.count(),
        items.count(),
        orders.count(),
        revenue,
        items.countOngoing(clock.instant().getEpochSecond()),
        paid,
        refunded,
        paid.subtract(refunded),
        legacy);
  }

  private java.math.BigInteger amount(String sql) {
    return jdbc.queryForObject(sql, java.math.BigDecimal.class).toBigIntegerExact();
  }
}
