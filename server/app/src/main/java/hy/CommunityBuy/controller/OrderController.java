package hy.CommunityBuy.controller;

import hy.CommunityBuy.entity.Order;
import hy.CommunityBuy.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getOrders(
            @RequestHeader("token") String token,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) Long item,
            @RequestParam(required = false) Integer status) {
        
        var orders = orderService.getOrders(user, owner, item, status);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", orders.stream().map(o -> o.getId()).toList());
        response.put("count", orders.size());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(@RequestHeader("token") String token, @RequestBody Order orderReq) {
        // 简化版：实际应从 token 获取 userId，从 item 获取 ownerId 和价格
        // 这里假设参数已完整传入
        Order order = orderService.createOrder(
                orderReq.getItemId(),
                "user_id_from_token", // 模拟
                "owner_id_from_item",  // 模拟
                orderReq.getQuantity(),
                orderReq.getPrice(),
                orderReq.getAddress()
        );
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("order", order.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getOrderDetail(@RequestHeader("token") String token, @PathVariable Long id) {
        return orderService.getOrder(id).map(order -> {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            Map<String, Object> data = new HashMap<>();
            data.put("id", order.getId());
            data.put("item", order.getItemId());
            data.put("user", order.getUserId());
            data.put("owner", order.getOwnerId());
            data.put("quantity", order.getQuantity());
            data.put("price", order.getPrice());
            data.put("address", order.getAddress());
            data.put("status", order.getStatus());
            data.put("createTime", order.getCreateTime());
            response.put("data", data);
            return ResponseEntity.ok(response);
        }).orElseGet(() -> {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            return ResponseEntity.ok(response);
        });
    }

    @PostMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateOrder(@RequestHeader("token") String token, @PathVariable Long id, @RequestBody Order updateInfo) {
        orderService.updateOrder(id, updateInfo);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }
}