package hy.CommunityBuy.repository;

import hy.CommunityBuy.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(String userId);
    List<Order> findByOwnerId(String ownerId);
    List<Order> findByItemId(Long itemId);
    List<Order> findByStatus(Integer status);
}