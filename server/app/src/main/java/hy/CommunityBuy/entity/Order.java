package hy.CommunityBuy.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String ownerId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer price;

    private String address;

    @Column(nullable = false)
    private Integer status; // 0:待支付, 1:待发货, 2:待收货, 3:已完成, -1:已取消

    private Long createTime;
    private Long payTime;
    private Long shipTime;
    private Long receiptTime;
    private Long closeTime;
}