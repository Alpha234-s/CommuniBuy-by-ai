package hy.CommunityBuy.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    private String id;

    @Column(nullable = false)
    private String password;

    private String nickname;
    private String description;
    private Integer role; // 0: 普通用户, 1: 管理员, -1: 封禁
    private String address;
}