package hy.CommunityBuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "blacklist")
@Getter
@Setter
@NoArgsConstructor
public class Blacklist {
  @Id
  @Column(length = 64)
  private String tokenHash;

  @Column(nullable = false)
  private Long expireAt;
}
