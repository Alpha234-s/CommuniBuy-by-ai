package hy.CommunityBuy.finance;
import hy.CommunityBuy.finance.FinanceDtos.*;
import hy.CommunityBuy.commerce.Amounts;
import hy.CommunityBuy.entity.Order;
import hy.CommunityBuy.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.*;

@Component @RequiredArgsConstructor
public class FinanceBook {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PaymentChannel channel;
    @Value("${app.payment.merchant:communibuy-demo}") private String merchant;
    private long now(){return clock.instant().getEpochSecond();}
    public void lockLedger(){jdbc.queryForObject("select id from finance_mutex where id=1 for update",Integer.class);}
    public Payment payment(String id){var result=jdbc.query("select * from payments where id=?",(rs,n)->new Payment(rs.getString("id"),rs.getLong("order_id"),rs.getLong("amount"),rs.getString("merchant"),rs.getString("channel"),rs.getString("status"),rs.getString("provider_reference"),rs.getLong("create_time"),rs.getLong("update_time")),id);if(result.isEmpty())throw BusinessException.missing();return result.getFirst();}
    public Refund refund(String id){var result=jdbc.query("select * from refunds where id=?",(rs,n)->new Refund(rs.getString("id"),rs.getLong("order_id"),rs.getString("payment_id"),rs.getLong("amount"),rs.getString("reason"),rs.getString("status"),rs.getInt("attempts"),rs.getString("provider_reference"),rs.getLong("create_time"),rs.getLong("update_time")),id);if(result.isEmpty())throw BusinessException.missing();return result.getFirst();}
    public Payment prepare(Order order){
        var ids=jdbc.queryForList("select id from payments where order_id=?",String.class,order.getId());
        if(!ids.isEmpty())return payment(ids.getFirst());
        String id=UUID.randomUUID().toString();jdbc.update("insert into payments(id,order_id,merchant,amount,channel,status,create_time,update_time) values (?,?,?,?,?,'PENDING',?,?)",id,order.getId(),merchant,order.getTotal(),channel.name(),now(),now());return payment(id);
    }
    public boolean pay(Order order){
        var p=prepare(order);if(p.status().equals("SUCCEEDED"))return true;
        if(p.status().equals("CANCELLED"))throw BusinessException.conflict("Payment was cancelled");
        var result=channel.charge(p.id(),p.amount());validateReceipt(result,p.amount(),p.merchant());
        jdbc.update("update payments set status=?,provider_reference=?,update_time=? where id=?",result.status(),result.reference(),now(),p.id());return result.status().equals("SUCCEEDED");
    }
    public void cancelPending(Order order){jdbc.update("update payments set status='CANCELLED',update_time=? where order_id=? and status in('PENDING','FAILED')",now(),order.getId());}
    public boolean openRefund(long orderId){return jdbc.queryForObject("select count(*) from refunds where order_id=? and status in('REQUESTED','APPROVED','FAILED')",Long.class,orderId)>0;}
    public Refund requestRefund(Order order,String reason){
        var existing=jdbc.queryForList("select id from refunds where order_id=?",String.class,order.getId());
        if(!existing.isEmpty()){var old=refund(existing.getFirst());if(!old.reason().equals(reason))throw BusinessException.conflict("Refund parameters differ");return old;}
        var payments=jdbc.queryForList("select id from payments where order_id=? and status='SUCCEEDED'",String.class,order.getId());
        if(payments.isEmpty())throw BusinessException.conflict("Payment record missing; reconcile legacy order before refund");
        String id=UUID.randomUUID().toString();jdbc.update("insert into refunds(id,order_id,payment_id,amount,reason,status,create_time,update_time) values (?,?,?,?,?,'REQUESTED',?,?)",id,order.getId(),payments.getFirst(),order.getTotal(),reason,now(),now());return refund(id);
    }
    public Refund executeRefund(Refund r){
        if(r.status().equals("SUCCEEDED"))return r;
        jdbc.update("update refunds set status='APPROVED',attempts=attempts+1,update_time=? where id=?",now(),r.id());
        var result=channel.refund(r.id(),r.amount());validateReceipt(result,r.amount(),payment(r.paymentId()).merchant());
        jdbc.update("update refunds set status=?,provider_reference=?,update_time=? where id=?",result.status().equals("PENDING")?"APPROVED":result.status(),result.reference(),now(),r.id());
        if(result.status().equals("SUCCEEDED"))adjustSettledRefund(r);return refund(r.id());
    }
    public Refund reject(Refund r){jdbc.update("update refunds set status='REJECTED',update_time=? where id=?",now(),r.id());return refund(r.id());}
    private void adjustSettledRefund(Refund r){
        var sellers=jdbc.queryForList("select seller_id from settlement_entries where order_id=? and kind='SALE'",String.class,r.orderId());
        if(!sellers.isEmpty())jdbc.update("insert into settlement_entries(seller_id,order_id,refund_id,kind,amount) values (?,?,?,'REFUND',?)",sellers.getFirst(),r.orderId(),r.id(),-r.amount());
    }
    public boolean event(String kind,Notice n,long expectedAmount,String expectedMerchant){
        if(n.amount()!=expectedAmount||!n.merchant().equals(expectedMerchant))throw BusinessException.conflict("Channel merchant or amount mismatch");
        String hash=Amounts.hash(n.canonical());var old=jdbc.queryForList("select payload_hash from channel_events where kind=? and event_id=?",String.class,kind,n.eventId());
        if(!old.isEmpty()){if(!old.getFirst().equals(hash))throw BusinessException.conflict("Channel event parameters differ");return false;}
        jdbc.update("insert into channel_events(kind,event_id,resource_id,payload_hash,create_time) values (?,?,?,?,?)",kind,n.eventId(),n.id(),hash,now());return true;
    }
    public Payment notifyPayment(Notice n){var p=payment(n.id());if(!event("PAYMENT",n,p.amount(),p.merchant()))return p;
        if(p.status().equals("CANCELLED")||p.status().equals("SUCCEEDED")&&!n.status().equals("SUCCEEDED"))throw BusinessException.conflict("Invalid payment transition");
        jdbc.update("update payments set status=?,provider_reference=coalesce(provider_reference,?),update_time=? where id=?",n.status(),"sim-notice:"+p.id(),now(),p.id());return payment(p.id());}
    public Refund notifyRefund(Notice n){var r=refund(n.id());var p=payment(r.paymentId());if(!event("REFUND",n,r.amount(),p.merchant()))return r;
        if(!List.of("APPROVED","FAILED","SUCCEEDED").contains(r.status())||r.status().equals("SUCCEEDED")&&!n.status().equals("SUCCEEDED"))throw BusinessException.conflict("Refund not approved or invalid transition");
        if(!r.status().equals("SUCCEEDED")&&n.status().equals("SUCCEEDED"))adjustSettledRefund(r);
        jdbc.update("update refunds set status=?,provider_reference=coalesce(provider_reference,?),update_time=? where id=?",n.status(),"sim-notice:"+r.id(),now(),r.id());return refund(r.id());}
    public PaymentChannel.Receipt queryReceipt(Payment p){var result=channel.query("PAYMENT",p.id());validateReceipt(result,p.amount(),p.merchant());return result;}
    public Payment recordReceipt(Payment p,PaymentChannel.Receipt r){jdbc.update("update payments set status=?,provider_reference=?,update_time=? where id=?",r.status(),r.reference(),now(),p.id());return payment(p.id());}
    private void validateReceipt(PaymentChannel.Receipt r,long amount,String expectedMerchant){
        if(!List.of("PENDING","SUCCEEDED","FAILED").contains(r.status())||!expectedMerchant.equals(r.merchant())||!r.status().equals("PENDING")&&(r.amount()==null||r.amount()!=amount))
            throw BusinessException.conflict("Channel merchant or amount mismatch");
    }
}
