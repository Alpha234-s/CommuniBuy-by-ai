package hy.CommunityBuy.entity;

import jakarta.persistence.*;

/** Read-only mapping used for composed order queries; writes use the immutable order line store. */
@Entity
@Table(name = "order_lines")
public class OrderLineRef {
  @Id private Long id;

  @Column(nullable = false)
  private Long orderId;

  @Column(nullable = false)
  private Long itemId;
}
