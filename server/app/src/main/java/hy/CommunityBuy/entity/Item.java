package hy.CommunityBuy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "items")
@Getter
@Setter
@NoArgsConstructor
public class Item {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false)
  private Integer price;

  @Column(nullable = false)
  private Integer stock;

  @Column(nullable = false)
  private Integer status;

  @Column(nullable = false, length = 32)
  private String ownerId;

  @Column(nullable = false)
  private Long startTime;

  private Long endTime;

  @Column(nullable = false)
  private Long shipmentCount = 0L;

  @Version
  @Column(nullable = false)
  private Long version = 0L;

  private Long categoryId;
}
