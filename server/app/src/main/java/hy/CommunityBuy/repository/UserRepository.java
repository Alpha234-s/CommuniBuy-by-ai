package hy.CommunityBuy.repository;

import hy.CommunityBuy.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface UserRepository extends JpaRepository<User, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.role=1 order by u.id")
  java.util.List<User> lockAdministrators();

  @Modifying
  @Query(
      value =
          "INSERT INTO users (id,password,role,auth_version,password_scheme) VALUES"
              + " (:id,:password,:role,0,'BCRYPT')",
      nativeQuery = true)
  void insert(String id, String password, int role);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :id")
  Optional<User> lock(String id);
}
