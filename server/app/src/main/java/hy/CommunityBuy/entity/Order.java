package hy.CommunityBuy.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
@Entity @Table(name = "orders")
@Getter @Setter @NoArgsConstructor
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long itemId;
    @Column(nullable = false, length = 32) private String userId;
    @Column(nullable = false, length = 32) private String ownerId;
    @Column(nullable = false) private Integer quantity;
    @Column(nullable = false) private Integer price;
    @Column(nullable = false) private Long total;
    @Column(nullable = false, length = 255) private String address;
    @Column(nullable = false) private Integer status;
    @Column(nullable = false) private Long createTime;
    private Long payTime;
    private Long shipTime;
    private Long receiptTime;
    private Long closeTime;
    @Column(nullable = false) private Long expiresAt;
    @Column(nullable = false, length = 64) private String idempotencyKey;
    @Column(nullable = false, length = 64) private String requestHash;
    @Column(nullable = false, length = 24) private String refundStatus = "NONE";
}
