package hy.CommunityBuy.service;

import hy.CommunityBuy.entity.Order;
import hy.CommunityBuy.entity.Item;
import hy.CommunityBuy.repository.ItemRepository;
import hy.CommunityBuy.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ItemRepository itemRepository;

    public List<Order> getOrders(String userId, String ownerId, Long itemId, Integer status) {
        if (userId != null) return orderRepository.findByUserId(userId);
        if (ownerId != null) return orderRepository.findByOwnerId(ownerId);
        if (itemId != null) return orderRepository.findByItemId(itemId);
        if (status != null) return orderRepository.findByStatus(status);
        return orderRepository.findAll();
    }

    public Optional<Order> getOrder(Long id) {
        return orderRepository.findById(id);
    }

    @Transactional
    public Order createOrder(Long itemId, String userId, String ownerId, Integer quantity, Integer price, String address) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));

        // 校验库存
        if (item.getStock() < quantity) {
            throw new RuntimeException("Insufficient stock");
        }

        // 校验团购状态 (status >= 0)
        if (item.getStatus() < 0) {
            throw new RuntimeException("Item is offline");
        }

        // 扣减库存
        item.setStock(item.getStock() - quantity);
        itemRepository.save(item);

        // 创建订单
        Order order = Order.builder()
                .itemId(itemId)
                .userId(userId)
                .ownerId(ownerId)
                .quantity(quantity)
                .price(price)
                .address(address)
                .status(0) // 待支付
                .createTime(Instant.now().getEpochSecond())
                .build();

        return orderRepository.save(order);
    }

    @Transactional
    public Order updateOrder(Long id, Order updateInfo) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (updateInfo.getAddress() != null) order.setAddress(updateInfo.getAddress());
        if (updateInfo.getStatus() != null) order.setStatus(updateInfo.getStatus());
        if (updateInfo.getPayTime() != null) order.setPayTime(updateInfo.getPayTime());
        if (updateInfo.getShipTime() != null) order.setShipTime(updateInfo.getShipTime());
        if (updateInfo.getReceiptTime() != null) order.setReceiptTime(updateInfo.getReceiptTime());
        if (updateInfo.getCloseTime() != null) order.setCloseTime(updateInfo.getCloseTime());

        return orderRepository.save(order);
    }
}