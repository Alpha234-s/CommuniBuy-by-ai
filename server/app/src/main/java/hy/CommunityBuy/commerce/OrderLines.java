package hy.CommunityBuy.commerce;

import hy.CommunityBuy.entity.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderLines {
  private final JdbcTemplate jdbc;

  public List<CommerceDtos.Line> get(long id) {
    return jdbc.query(
        "select * from order_lines where order_id=? order by item_id",
        (rs, n) ->
            new CommerceDtos.Line(
                rs.getLong("item_id"),
                rs.getInt("quantity"),
                rs.getInt("price"),
                rs.getLong("total"),
                rs.getString("item_name"),
                rs.getString("item_description"),
                rs.getString("snapshot_source")),
        id);
  }

  public void add(Order order, Item item, int quantity) {
    jdbc.update(
        "insert into"
            + " order_lines(order_id,item_id,quantity,price,total,item_name,item_description,snapshot_source)"
            + " values (?,?,?,?,?,?,?,?)",
        order.getId(),
        item.getId(),
        quantity,
        item.getPrice(),
        (long) item.getPrice() * quantity,
        item.getName(),
        item.getDescription(),
        "AT_CREATION");
  }
}
