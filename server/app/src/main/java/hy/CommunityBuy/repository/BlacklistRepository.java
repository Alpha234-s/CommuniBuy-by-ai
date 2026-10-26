package hy.CommunityBuy.repository;

import hy.CommunityBuy.entity.Blacklist;
import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.*;

public interface BlacklistRepository extends JpaRepository<Blacklist, String> {
  // V2 ix_blacklist_expiry also carries the PK in InnoDB; bounded expiry-ordered range deletion.
  @Modifying
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  @Query(
      value =
          "DELETE FROM blacklist WHERE expire_at<=:now ORDER BY expire_at,token_hash LIMIT :batch",
      nativeQuery = true)
  int deleteExpired(long now, int batch);
}
