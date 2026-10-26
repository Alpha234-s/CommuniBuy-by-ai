package hy.CommunityBuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long itemId;

  @Column(nullable = false, length = 32)
  private String userId;

  @Column(nullable = false, length = 32)
  private String ownerId;

  @Column(nullable = false)
  private Integer quantity;

  @Column(nullable = false)
  private Integer price;

  @Column(nullable = false)
  private Long total;

  @Column(nullable = false, length = 255)
  private String address;

  @Column(nullable = false)
  private Integer status;

  @Column(nullable = false)
  private Long createTime;

  private Long payTime;
  private Long shipTime;
  private Long receiptTime;
  private Long closeTime;

  @Column(nullable = false)
  private Long expiresAt;

  @Column(nullable = false, length = 64)
  private String idempotencyKey;

  @Column(nullable = false, length = 64)
  private String requestHash;

  @Column(length = 128)
  private String itemName;

  @Column(columnDefinition = "TEXT")
  private String itemDescription;

  @Column(nullable = false, length = 24)
  private String snapshotSource = "LEGACY_UNKNOWN";

  @Column(nullable = false, length = 24)
  private String refundStatus = "NONE";

  @Column(length = 36)
  private String checkoutId;

  @Column(nullable = false)
  private Long refundedTotal = 0L;
}
