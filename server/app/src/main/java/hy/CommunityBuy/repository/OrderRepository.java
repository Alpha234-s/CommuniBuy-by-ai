package hy.CommunityBuy.repository;
import hy.CommunityBuy.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByUserIdAndIdempotencyKey(String userId, String key);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id=:id") Optional<Order> lock(Long id);
    @Query("select o.itemId from Order o where o.id=:id") Optional<Long> itemId(Long id);
    @Query("select o.id from Order o where o.status=0 and o.expiresAt<=:now order by o.id")
    List<Long> expired(long now, Pageable page);
    @Query("select coalesce(sum(o.total),0) from Order o where o.status in (1,2,3)") long revenue();
    @Query("select coalesce(sum(o.quantity),0) from Order o where o.itemId=:itemId and o.status in (0,1)") long reserved(long itemId);
}
