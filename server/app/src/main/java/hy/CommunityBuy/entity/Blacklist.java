package hy.CommunityBuy.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
@Entity @Table(name = "blacklist")
@Getter @Setter @NoArgsConstructor
public class Blacklist {
    @Id @Column(length = 64) private String tokenHash;
    @Column(nullable = false) private Long expireAt;
}
