package hy.CommunityBuy.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
@Entity @Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id @Column(length = 32) private String id;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(nullable = false, length = 255) private String password;
    @Column(length = 64) private String nickname;
    @Column(length = 255) private String description;
    @Column(nullable = false) private Integer role = 0;
    @Column(length = 255) private String address;
    @Column(nullable = false) private Long authVersion = 0L;
    @Column(nullable = false, length = 16) private String passwordScheme = "BCRYPT";
    @Column(nullable=false) private Long cartVersion = 0L;
}
