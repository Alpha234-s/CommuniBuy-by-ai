package hy.CommunityBuy.finance;
import hy.CommunityBuy.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="app.payment.mode",havingValue="simulator")
public class SimulatorChannel implements PaymentChannel {
    private final JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Value("${app.payment.merchant:communibuy-demo}") private String merchant;
    public String name(){return "SIMULATOR";}
    public Receipt charge(String id,long amount){return execute("PAYMENT",id,amount);}
    public Receipt refund(String id,long amount){return execute("REFUND",id,amount);}
    private Receipt execute(String kind,String id,long amount){
        String key=kind+":"+id;
        jdbc.update("insert into simulator_operations(operation_id,amount,status,reference,merchant) values (?,?,'SUCCEEDED',?,?) on duplicate key update operation_id=operation_id",key,amount,"sim-"+key,merchant);
        long stored=jdbc.queryForObject("select amount from simulator_operations where operation_id=?",Long.class,key);
        if(stored!=amount)throw BusinessException.conflict("Simulator operation amount differs");
        return query(kind,id);
    }
    public Receipt query(String kind,String id){
        var rows=jdbc.query("select status,reference,amount,merchant from simulator_operations where operation_id=?",(rs,n)->new Receipt(rs.getString(1),rs.getString(2),rs.getLong(3),rs.getString(4)),kind+":"+id);
        return rows.isEmpty()?new Receipt("PENDING",null,null,merchant):rows.getFirst();
    }
}
