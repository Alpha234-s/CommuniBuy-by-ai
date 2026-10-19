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
    private final hy.CommunityBuy.commerce.OrderLines lines;
    private final hy.CommunityBuy.finance.FinanceBook book;
    private final hy.CommunityBuy.finance.SimulatorSignatures signatures;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Value("${app.orders.payment-timeout-seconds}") private long timeout;
    @PostConstruct void validate() { if (timeout < 1 || timeout > 604800) throw new IllegalArgumentException("Payment timeout must be 1..604800 seconds"); }
    @Transactional(readOnly=true)
    public PageView<OrderView> list(Actor a, String buyer, String seller, Long itemId, Integer status, int page, int size) {
        return PageView.of(orders.findAll((root,q,cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (!a.admin()) p.add(cb.or(cb.equal(root.get("userId"),a.id()),cb.equal(root.get("ownerId"),a.id())));
            if (buyer!=null) p.add(cb.equal(root.get("userId"),buyer));
            if (seller!=null) p.add(cb.equal(root.get("ownerId"),seller));
            if (itemId!=null) {
                var sub=q.subquery(Long.class);var line=sub.from(OrderLineRef.class);
                sub.select(line.get("id")).where(cb.equal(line.get("orderId"),root.get("id")),cb.equal(line.get("itemId"),itemId));p.add(cb.exists(sub));
            }
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
        o.setItemName(i.getName()); o.setItemDescription(i.getDescription()); o.setSnapshotSource("AT_CREATION");
        o.setIdempotencyKey(key); o.setRequestHash(hash);
        orders.saveAndFlush(o);lines.add(o,i,r.quantity());return view(a,o);
    }
    // Every inventory-affecting path locks item BEFORE order. The initial scalar read doesn't load a stale order entity.
    private Locked lock(long id) {
        var entries=lines.get(id);
        if(entries.isEmpty()){orders.itemId(id).orElseThrow(BusinessException::missing);throw BusinessException.conflict("Order lines missing; reconcile legacy order");}
        var locked=new ArrayList<Item>();
        for(var entry:entries)locked.add(items.lock(entry.itemId()).orElseThrow(BusinessException::missing));
        var order=orders.lock(id).orElseThrow(BusinessException::missing);
        return new Locked(locked,order);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public OrderView action(Actor a, long id, String action) {
        // Refund/cancel/settlement paths take the ledger mutex first, including implicit FK locks.
        if(action.equals("cancel"))book.lockLedger();
        var locked=lock(id); var o=locked.order(); authorize(a,o);
        long now=clock.instant().getEpochSecond();
        switch (action) {
            case "pay" -> {
                if (!a.id().equals(o.getUserId())) throw BusinessException.forbidden();
                if (o.getStatus()==1) return view(a,o);
                requireState(o,0);
                if (o.getExpiresAt()<=now) throw BusinessException.conflict("Payment deadline passed");
                if(book.pay(o)){o.setStatus(1); o.setPayTime(now);}
            }
            case "ship" -> {
                if (!a.admin() && !a.id().equals(o.getOwnerId())) throw BusinessException.forbidden();
                if (o.getStatus()==2) return view(a,o);
                if(book.openRefund(id))throw BusinessException.conflict("Refund review is in progress");
                requireState(o,1); o.setStatus(2); o.setShipTime(now);
                for(var item:locked.items())item.setShipmentCount(Math.addExact(item.getShipmentCount(),1));
            }
            case "receive" -> {
                if (!a.admin() && !a.id().equals(o.getUserId())) throw BusinessException.forbidden();
                if (o.getStatus()==3) return view(a,o);
                if(book.openRefund(id))throw BusinessException.conflict("Refund review is in progress");
                requireState(o,2); o.setStatus(3); o.setReceiptTime(now); o.setCloseTime(now);
            }
            case "cancel" -> {
                if (o.getStatus()==-1) return view(a,o);
                if (o.getStatus()==0) {
                    if (!a.admin() && !a.id().equals(o.getUserId())) throw BusinessException.forbidden();
                } else if (o.getStatus()==1) {
                    if (!a.admin() && !a.id().equals(o.getOwnerId())) throw BusinessException.forbidden();
                    book.lockLedger();
                    var refund=book.executeRefund(book.requestRefund(o,"Seller cancellation"));
                    if(!refund.status().equals("SUCCEEDED"))return view(a,o);
                    o.setRefundedTotal(o.getTotal());o.setRefundStatus("SIMULATED_REFUNDED");
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
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public hy.CommunityBuy.finance.FinanceDtos.Payment preparePayment(Actor a,long id) {
        var o=lock(id).order();if(!a.id().equals(o.getUserId()))throw BusinessException.forbidden();
        if(o.getStatus()!=0&&o.getStatus()!=1)throw BusinessException.conflict("Invalid order state");
        if(o.getStatus()==0&&o.getExpiresAt()<=clock.instant().getEpochSecond())throw BusinessException.conflict("Payment deadline passed");
        return book.prepare(o);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public hy.CommunityBuy.finance.FinanceDtos.Payment queryPayment(Actor a,String id){
        var original=book.payment(id);var o=lock(original.orderId()).order();authorize(a,o);
        var current=book.payment(id);if(List.of("SUCCEEDED","CANCELLED").contains(current.status()))return current;
        var result=book.queryReceipt(current);
        if(result.status().equals("SUCCEEDED")){
            if(o.getStatus()!=0||o.getExpiresAt()<=clock.instant().getEpochSecond())throw BusinessException.conflict("Late provider success requires reconciliation");
            o.setStatus(1);o.setPayTime(clock.instant().getEpochSecond());
        }
        return book.recordReceipt(current,result);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public hy.CommunityBuy.finance.FinanceDtos.Payment paymentNotice(hy.CommunityBuy.finance.FinanceDtos.Notice notice,String signature) {
        signatures.verify(notice,signature);var original=book.payment(notice.id());var o=lock(original.orderId()).order();
        if(notice.status().equals("SUCCEEDED")&&o.getPayTime()==null&&(o.getStatus()!=0||o.getExpiresAt()<=clock.instant().getEpochSecond()))
            throw BusinessException.conflict("Late payment notification requires reconciliation");
        var payment=book.notifyPayment(notice);
        if(payment.status().equals("SUCCEEDED")&&o.getStatus()==0){o.setStatus(1);o.setPayTime(clock.instant().getEpochSecond());}
        return payment;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public hy.CommunityBuy.finance.FinanceDtos.Refund refundOperation(Actor a,long id,String operation,String reason) {
        book.lockLedger();var locked=lock(id);var o=locked.order();authorize(a,o);
        if(operation.equals("request")) {
            if(!a.id().equals(o.getUserId()))throw BusinessException.forbidden();
            if(o.getPayTime()==null||!List.of(1,2,3).contains(o.getStatus())&&!o.getRefundStatus().equals("SIMULATED_REFUNDED"))throw BusinessException.conflict("Only paid active/completed orders can request refund");
            return book.requestRefund(o,reason);
        }
        if(!a.admin()&&!a.id().equals(o.getOwnerId()))throw BusinessException.forbidden();
        String refundId=jdbc.queryForObject("select id from refunds where order_id=?",String.class,id);var refund=book.refund(refundId);
        if(!List.of("approve","reject","retry").contains(operation))throw BusinessException.bad("Unknown refund operation");
        if(refund.status().equals("SUCCEEDED")||operation.equals("reject")&&refund.status().equals("REJECTED"))return refund;
        if(operation.equals("reject")){if(!refund.status().equals("REQUESTED"))throw BusinessException.conflict("Refund is not awaiting review");return book.reject(refund);}
        if(operation.equals("approve")&&!refund.status().equals("REQUESTED")||operation.equals("retry")&&!refund.status().equals("FAILED"))throw BusinessException.conflict("Invalid refund state");
        var result=book.executeRefund(refund);if(result.status().equals("SUCCEEDED"))finishRefund(locked);return result;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public hy.CommunityBuy.finance.FinanceDtos.Refund refundNotice(hy.CommunityBuy.finance.FinanceDtos.Notice notice,String signature) {
        signatures.verify(notice,signature);var old=book.refund(notice.id());book.lockLedger();var locked=lock(old.orderId());
        var result=book.notifyRefund(notice);if(result.status().equals("SUCCEEDED")&&!locked.order().getRefundStatus().equals("SIMULATED_REFUNDED"))finishRefund(locked);return result;
    }
    private void finishRefund(Locked locked){
        var o=locked.order();if(o.getRefundStatus().equals("SIMULATED_REFUNDED"))return;
        o.setRefundedTotal(o.getTotal());o.setRefundStatus("SIMULATED_REFUNDED");
        if(o.getStatus()==1)close(locked,clock.instant().getEpochSecond());
        else{o.setStatus(-1);o.setCloseTime(clock.instant().getEpochSecond());}
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public hy.CommunityBuy.finance.FinanceDtos.Review review(Actor a,long id,hy.CommunityBuy.finance.FinanceDtos.ReviewRequest r) {
        var o=lock(id).order();if(!a.id().equals(o.getUserId()))throw BusinessException.forbidden();
        requireState(o,3);if(o.getRefundedTotal()!=0||book.openRefund(id))throw BusinessException.conflict("Refunded or disputed order cannot be reviewed");
        if(lines.get(id).stream().noneMatch(l->l.itemId()==r.itemId()))throw BusinessException.bad("Item is not in this order");
        jdbc.update("insert into reviews(order_id,item_id,user_id,rating,comment,create_time) values (?,?,?,?,?,?)",id,r.itemId(),a.id(),r.rating(),r.comment(),clock.instant().getEpochSecond());
        return jdbc.queryForObject("select id from reviews where order_id=? and item_id=?",(rs,n)->new hy.CommunityBuy.finance.FinanceDtos.Review(rs.getLong(1),id,r.itemId(),a.id(),r.rating(),r.comment(),clock.instant().getEpochSecond()),id,r.itemId());
    }
    private void close(Locked locked, long now) {
        try {
            var entries=lines.get(locked.order().getId());
            for(var item:locked.items()) {
                int quantity=entries.stream().filter(l->l.itemId()==item.getId()).findFirst().orElseThrow().quantity();
                item.setStock(Math.addExact(item.getStock(),quantity));
            }
        }
        catch (ArithmeticException e) { throw BusinessException.conflict("Stock overflow; administrator must reduce available stock"); }
        book.cancelPending(locked.order());
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
    private record Locked(List<Item> items, Order order) {}
}
