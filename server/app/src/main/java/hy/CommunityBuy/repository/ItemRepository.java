package hy.CommunityBuy.repository;

import hy.CommunityBuy.entity.Item;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface ItemRepository extends JpaRepository<Item, Long>, JpaSpecificationExecutor<Item> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Item i where i.id = :id")
  Optional<Item> lock(Long id);

  @Query(
      "select count(i) from Item i where i.status=0 and i.startTime<=:now and (i.endTime is null or"
          + " i.endTime>:now)")
  long countOngoing(long now);
}
