package hy.CommunityBuy.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer price; // 单位：分

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private Integer status;

    private String ownerId;

    private Long startTime;

    private Long endTime;
}