package hy.CommunityBuy.controller;

import hy.CommunityBuy.entity.Item;
import hy.CommunityBuy.service.ItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/item")
public class ItemController {

    @Autowired
    private ItemService itemService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getItems(
            @RequestHeader("token") String token,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String owner,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "100") Integer size) {
        
        // 简化版：实际应处理分页逻辑
        var items = itemService.getAllItems(status, owner);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("items", items.stream().map(i -> i.getId()).toList());
        response.put("count", items.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getItem(@RequestHeader("token") String token, @PathVariable Long id) {
        return itemService.getItem(id).map(item -> {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            Map<String, Object> data = new HashMap<>();
            data.put("id", item.getId());
            data.put("name", item.getName());
            data.put("description", item.getDescription());
            data.put("price", item.getPrice());
            data.put("stock", item.getStock());
            data.put("status", item.getStatus());
            data.put("owner", item.getOwnerId());
            data.put("startTime", item.getStartTime());
            data.put("endTime", item.getEndTime());
            response.put("data", data);
            return ResponseEntity.ok(response);
        }).orElseGet(() -> {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            return ResponseEntity.ok(response);
        });
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createItem(@RequestHeader("token") String token, @RequestBody Item item) {
        Item created = itemService.createItem(item);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("item", created.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateItem(@RequestHeader("token") String token, @PathVariable Long id, @RequestBody Item item) {
        Item updated = itemService.updateItem(id, item);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Update successful");
        return ResponseEntity.ok(response);
    }
}