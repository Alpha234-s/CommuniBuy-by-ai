package hy.CommunityBuy.service;
import hy.CommunityBuy.dto.Dtos.*;
import hy.CommunityBuy.entity.*;
import hy.CommunityBuy.exception.BusinessException;
import hy.CommunityBuy.repository.*;
import hy.CommunityBuy.security.Actor;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.*;
@Service @RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orders;
    private final ItemRepository items;
    private final UserRepository users;
    private final Clock clock;
    @Value("${app.orders.payment-timeout-seconds}") private long timeout;
    @PostConstruct void validate() { if (timeout < 1 || timeout > 604800) throw new IllegalArgumentException("Payment timeout must be 1..604800 seconds"); }
    @Transactional(readOnly=true)
    public PageView<OrderView> list(Actor a, String buyer, String seller, Long itemId, Integer status, int page, int size) {
        return PageView.of(orders.findAll((root,q,cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (!a.admin()) p.add(cb.or(cb.equal(root.get("userId"),a.id()),cb.equal(root.get("ownerId"),a.id())));
            if (buyer!=null) p.add(cb.equal(root.get("userId"),buyer));
            if (seller!=null) p.add(cb.equal(root.get("ownerId"),seller));
            if (itemId!=null) p.add(cb.equal(root.get("itemId"),itemId));
            if (status!=null) p.add(cb.equal(root.get("status"),status));
            return cb.and(p.toArray(Predicate[]::new));
        },PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"))).map(o -> view(a,o)));
    }
    @Transactional(readOnly=true)
    public OrderView get(Actor a, long id) { var o=orders.findById(id).orElseThrow(BusinessException::missing); authorize(a,o); return view(a,o); }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public OrderView create(Actor a, NewOrder r, String key) {
        // Per-buyer row lock serializes the same idempotency key even across different items.
        var buyer=users.lock(a.id()).orElseThrow(BusinessException::missing);
        if (buyer.getRole()<0) throw BusinessException.forbidden();
        String hash=fingerprint(r);
        var previous=orders.findByUserIdAndIdempotencyKey(a.id(),key);
        if (previous.isPresent()) {
            if (!previous.get().getRequestHash().equals(hash)) throw BusinessException.conflict("Idempotency key parameters differ");
            return view(a,previous.get());
        }
        var i=items.lock(r.itemId()).orElseThrow(BusinessException::missing);
        long now=clock.instant().getEpochSecond();
        if (i.getStatus()!=0 || i.getStartTime()>now || (i.getEndTime()!=null && i.getEndTime()<=now))
            throw BusinessException.conflict("Item is not available now");
        var seller=users.findById(i.getOwnerId()).orElseThrow(BusinessException::missing);
        if (seller.getRole()<0 || seller.getId().equals(a.id())) throw BusinessException.conflict("Seller unavailable or self purchase");
        if (r.quantity()<=0 || r.address()==null || r.address().isBlank()) throw BusinessException.bad("Invalid order");
        if (i.getStock()<r.quantity()) throw BusinessException.conflict("Insufficient stock");
        long total;
        try { total=Math.multiplyExact((long)i.getPrice(),r.quantity().longValue()); }
        catch (ArithmeticException e) { throw BusinessException.bad("Order amount overflow"); }
        i.setStock(i.getStock()-r.quantity());
        // Persist the reservation inside this transaction before the order INSERT; an INSERT failure must undo it.
        items.flush();
        var o=new Order(); o.setItemId(i.getId()); o.setUserId(a.id()); o.setOwnerId(i.getOwnerId());
        o.setQuantity(r.quantity()); o.setPrice(i.getPrice()); o.setTotal(total); o.setAddress(r.address());
        o.setStatus(0); o.setCreateTime(now); o.setExpiresAt(Math.addExact(now,timeout));
        o.setIdempotencyKey(key); o.setRequestHash(hash);
        return view(a,orders.saveAndFlush(o));
    }
    // Every inventory-affecting path locks item BEFORE order. The initial scalar read doesn't load a stale order entity.
    private Locked lock(long id) {
        long itemId=orders.itemId(id).orElseThrow(BusinessException::missing);
        var item=items.lock(itemId).orElseThrow(BusinessException::missing);
        var order=orders.lock(id).orElseThrow(BusinessException::missing);
        return new Locked(item,order);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public OrderView action(Actor a, long id, String action) {
        var locked=lock(id); var o=locked.order(); authorize(a,o);
        long now=clock.instant().getEpochSecond();
        switch (action) {
            case "pay" -> {
                if (!a.id().equals(o.getUserId())) throw BusinessException.forbidden();
                if (o.getStatus()==1) return view(a,o);
                requireState(o,0);
                if (o.getExpiresAt()<=now) throw BusinessException.conflict("Payment deadline passed");
                o.setStatus(1); o.setPayTime(now);
            }
            case "ship" -> {
                if (!a.admin() && !a.id().equals(o.getOwnerId())) throw BusinessException.forbidden();
                if (o.getStatus()==2) return view(a,o);
                requireState(o,1); o.setStatus(2); o.setShipTime(now);
                locked.item().setShipmentCount(Math.addExact(locked.item().getShipmentCount(),1));
            }
            case "receive" -> {
                if (!a.admin() && !a.id().equals(o.getUserId())) throw BusinessException.forbidden();
                if (o.getStatus()==3) return view(a,o);
                requireState(o,2); o.setStatus(3); o.setReceiptTime(now); o.setCloseTime(now);
            }
            case "cancel" -> {
                if (o.getStatus()==-1) return view(a,o);
                if (o.getStatus()==0) {
                    if (!a.admin() && !a.id().equals(o.getUserId())) throw BusinessException.forbidden();
                } else if (o.getStatus()==1) {
                    if (!a.admin() && !a.id().equals(o.getOwnerId())) throw BusinessException.forbidden();
                    o.setRefundStatus("SIMULATED_REFUNDED");
                } else throw BusinessException.conflict("Order cannot be cancelled");
                close(locked,now);
            }
            default -> throw BusinessException.bad("Unknown order operation");
        }
        return view(a,o);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public OrderView address(Actor a, long id, Address address) {
        var o=lock(id).order(); authorize(a,o);
        if (!a.admin() && !a.id().equals(o.getUserId())) throw BusinessException.forbidden();
        requireState(o,1); o.setAddress(address.address()); return view(a,o);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void expire(long id) {
        var locked=lock(id); var o=locked.order(); long now=clock.instant().getEpochSecond();
        if (o.getStatus()==0 && o.getExpiresAt()<=now) close(locked,now);
    }
    private void close(Locked locked, long now) {
        try { locked.item().setStock(Math.addExact(locked.item().getStock(),locked.order().getQuantity())); }
        catch (ArithmeticException e) { throw BusinessException.conflict("Stock overflow; administrator must reduce available stock"); }
        locked.order().setStatus(-1); locked.order().setCloseTime(now);
    }
    private void requireState(Order o, int status) { if (o.getStatus()!=status) throw BusinessException.conflict("Invalid order state"); }
    private void authorize(Actor a, Order o) {
        if (!a.admin() && !a.id().equals(o.getUserId()) && !a.id().equals(o.getOwnerId())) throw BusinessException.forbidden();
    }
    private OrderView view(Actor a, Order o) {
        // Sellers need a delivery address only after payment; unrelated users never receive the order.
        return OrderView.of(o,a.admin() || a.id().equals(o.getUserId()) || (a.id().equals(o.getOwnerId()) && o.getPayTime()!=null));
    }
    private String fingerprint(NewOrder r) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
            (r.itemId()+"\n"+r.quantity()+"\n"+r.address()).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private record Locked(Item item, Order order) {}
}
