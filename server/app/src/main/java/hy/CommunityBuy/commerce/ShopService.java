package hy.CommunityBuy.commerce;

import hy.CommunityBuy.commerce.CommerceDtos.*;
import hy.CommunityBuy.dto.Dtos.*;
import hy.CommunityBuy.entity.*;
import hy.CommunityBuy.exception.BusinessException;
import hy.CommunityBuy.repository.*;
import hy.CommunityBuy.security.Actor;
import hy.CommunityBuy.service.OrderService;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ShopService {
  private final UserRepository users;
  private final ItemRepository items;
  private final OrderRepository orders;
  private final OrderLines lines;
  private final OrderService orderService;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  @Value("${app.orders.payment-timeout-seconds}")
  private long timeout;

  @hy.CommunityBuy.infrastructure.BusinessAudit("CATEGORY_CREATE")
  public Category category(Actor actor, CategoryRequest r) {
    actor.requireAdmin();
    jdbc.update("insert into categories(name) values (?)", r.name());
    return jdbc.queryForObject(
        "select id,name from categories where name=?",
        (rs, n) -> new Category(rs.getLong(1), rs.getString(2)),
        r.name());
  }

  @Transactional(readOnly = true)
  public PageView<Category> categories(int page, int size) {
    return new PageView<>(
        jdbc.query(
            "select id,name from categories order by id limit ? offset ?",
            (rs, n) -> new Category(rs.getLong(1), rs.getString(2)),
            size,
            page * size),
        jdbc.queryForObject("select count(*) from categories", Long.class),
        page,
        size);
  }

  @hy.CommunityBuy.infrastructure.BusinessAudit(value = "ITEM_CATEGORY", resourceArgument = 1)
  public ItemView assign(Actor actor, long itemId, long categoryId) {
    var i = items.lock(itemId).orElseThrow(BusinessException::missing);
    actor.requireSelf(i.getOwnerId());
    if (jdbc.queryForObject("select count(*) from categories where id=?", Long.class, categoryId)
        == 0) throw BusinessException.missing();
    i.setCategoryId(categoryId);
    items.flush();
    return ItemView.of(i);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CartView cart(Actor actor) {
    var user = users.findById(actor.id()).orElseThrow(BusinessException::missing);
    return new CartView(
        user.getCartVersion(),
        jdbc.query(
            "select c.*,i.name,i.price,i.stock,i.status,i.start_time,i.end_time from cart_entries c"
                + " join items i on i.id=c.item_id where c.user_id=? order by c.item_id",
            (rs, n) ->
                new CartLine(
                    rs.getLong("item_id"),
                    rs.getInt("quantity"),
                    rs.getBoolean("selected"),
                    rs.getString("name"),
                    rs.getInt("price"),
                    rs.getInt("stock"),
                    rs.getInt("status"),
                    rs.getLong("start_time"),
                    (Long) rs.getObject("end_time")),
            actor.id()));
  }

  @hy.CommunityBuy.infrastructure.BusinessAudit(value = "CART_UPDATE", resourceArgument = 1)
  public CartView cartChange(Actor actor, long itemId, CartChange r) {
    var user = activeBuyer(actor);
    items.findById(itemId).orElseThrow(BusinessException::missing);
    long count =
        jdbc.queryForObject(
            "select count(*) from cart_entries where user_id=? and item_id<>?",
            Long.class,
            actor.id(),
            itemId);
    if (count >= 50) throw BusinessException.conflict("Cart limit is 50 distinct items");
    jdbc.update(
        "insert into cart_entries(user_id,item_id,quantity,selected) values (?,?,?,?) on duplicate"
            + " key update quantity=?,selected=?",
        actor.id(),
        itemId,
        r.quantity(),
        r.selected(),
        r.quantity(),
        r.selected());
    user.setCartVersion(user.getCartVersion() + 1);
    users.flush();
    return cart(actor);
  }

  @hy.CommunityBuy.infrastructure.BusinessAudit(value = "CART_DELETE", resourceArgument = 1)
  public CartView cartDelete(Actor actor, long itemId) {
    var user = activeBuyer(actor);
    if (jdbc.update("delete from cart_entries where user_id=? and item_id=?", actor.id(), itemId)
        > 0) user.setCartVersion(user.getCartVersion() + 1);
    users.flush();
    return cart(actor);
  }

  @hy.CommunityBuy.infrastructure.BusinessAudit("CHECKOUT")
  public CheckoutView checkout(Actor actor, Checkout request, String key) {
    var buyer = activeBuyer(actor);
    String hash =
        Amounts.hash(
            request.cartVersion() + "\n" + request.expectedTotal() + "\n" + request.address());
    var old =
        jdbc.queryForList(
            "select id,request_hash from checkout_batches where user_id=? and idempotency_key=?",
            actor.id(),
            key);
    if (!old.isEmpty()) {
      if (!hash.equals(old.getFirst().get("request_hash")))
        throw BusinessException.conflict("Idempotency key parameters differ");
      return batch(actor, old.getFirst().get("id").toString());
    }
    if (!buyer.getCartVersion().equals(request.cartVersion()))
      throw BusinessException.conflict("Cart changed; refresh before checkout");
    var selected =
        jdbc.query(
            "select item_id,quantity from cart_entries where user_id=? and selected=1 order by"
                + " item_id",
            (rs, n) -> new Selected(rs.getLong(1), rs.getInt(2)),
            actor.id());
    if (selected.isEmpty()) throw BusinessException.conflict("No selected cart items");
    long now = clock.instant().getEpochSecond(), total = 0;
    var locked = new LinkedHashMap<Long, Item>();
    var groups = new TreeMap<String, List<Selected>>();
    for (var selection : selected) {
      var item = items.lock(selection.id()).orElseThrow(BusinessException::missing);
      var seller = users.findById(item.getOwnerId()).orElseThrow(BusinessException::missing);
      if (seller.getRole() < 0 || seller.getId().equals(actor.id()))
        throw BusinessException.conflict("Seller unavailable or self purchase");
      if (item.getStatus() != 0
          || item.getStartTime() > now
          || (item.getEndTime() != null && item.getEndTime() <= now))
        throw BusinessException.conflict("Item is not available now");
      if (item.getStock() < selection.quantity())
        throw BusinessException.conflict("Insufficient stock");
      total = Amounts.add(total, (long) item.getPrice() * selection.quantity());
      locked.put(item.getId(), item);
      groups.computeIfAbsent(item.getOwnerId(), ignored -> new ArrayList<>()).add(selection);
    }
    if (request.expectedTotal() != null && request.expectedTotal() != total)
      throw BusinessException.conflict("Price changed; confirm current total");
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "insert into checkout_batches(id,user_id,idempotency_key,request_hash,total,create_time)"
            + " values (?,?,?,?,?,?)",
        id,
        actor.id(),
        key,
        hash,
        total,
        now);
    for (var selection : selected) {
      var item = locked.get(selection.id());
      item.setStock(item.getStock() - selection.quantity());
    }
    items.flush();
    for (var group : groups.entrySet()) {
      var first = locked.get(group.getValue().getFirst().id());
      var order = new Order();
      order.setItemId(first.getId());
      order.setUserId(actor.id());
      order.setOwnerId(group.getKey());
      order.setAddress(request.address());
      order.setStatus(0);
      order.setPrice(first.getPrice());
      order.setItemName(first.getName());
      order.setItemDescription(first.getDescription());
      order.setSnapshotSource("AT_CREATION");
      long amount = 0;
      int quantity = 0;
      for (var selection : group.getValue()) {
        amount =
            Amounts.add(
                amount, (long) locked.get(selection.id()).getPrice() * selection.quantity());
        quantity = Amounts.quantity(quantity, selection.quantity());
      }
      order.setTotal(amount);
      order.setQuantity(quantity);
      order.setCreateTime(now);
      order.setExpiresAt(Math.addExact(now, timeout));
      order.setCheckoutId(id);
      order.setIdempotencyKey(
          "checkout_" + Amounts.hash(id + "|" + group.getKey()).substring(0, 48));
      order.setRequestHash(hash);
      orders.saveAndFlush(order);
      for (var selection : group.getValue())
        lines.add(order, locked.get(selection.id()), selection.quantity());
    }
    jdbc.update("delete from cart_entries where user_id=? and selected=1", actor.id());
    buyer.setCartVersion(buyer.getCartVersion() + 1);
    users.flush();
    return batch(actor, id);
  }

  @Transactional(readOnly = true)
  public CheckoutView batch(Actor actor, String id) {
    var rows = jdbc.queryForList("select user_id,total from checkout_batches where id=?", id);
    if (rows.isEmpty()) throw BusinessException.missing();
    actor.requireSelf(rows.getFirst().get("user_id").toString());
    return new CheckoutView(
        id,
        ((Number) rows.getFirst().get("total")).longValue(),
        orders.findByCheckoutIdOrderByIdAsc(id).stream()
            .map(o -> details(actor, o.getId()))
            .toList());
  }

  @Transactional(readOnly = true)
  public DetailedOrder details(Actor actor, long id) {
    return new DetailedOrder(orderService.get(actor, id), lines.get(id));
  }

  private User activeBuyer(Actor actor) {
    var u = users.lock(actor.id()).orElseThrow(BusinessException::missing);
    if (u.getRole() < 0) throw BusinessException.forbidden();
    return u;
  }

  private record Selected(long id, int quantity) {}
}
