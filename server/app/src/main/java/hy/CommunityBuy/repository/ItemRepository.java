package hy.CommunityBuy.repository;

import hy.CommunityBuy.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {
    List<Item> findByStatusGreaterThanEqual(Integer status);
    List<Item> findByOwnerId(String ownerId);
}