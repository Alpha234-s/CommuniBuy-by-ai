package hy.CommunityBuy.controller;

import hy.CommunityBuy.entity.User;
import hy.CommunityBuy.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllUsers(@RequestHeader("token") String token) {
        // 简化版：实际应校验管理员权限
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", userService.getAllUserIds());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getUser(@RequestHeader("token") String token, @PathVariable String userId) {
        User user = userService.getUser(userId);
        Map<String, Object> response = new HashMap<>();
        if (user != null) {
            response.put("success", true);
            Map<String, Object> data = new HashMap<>();
            data.put("nickname", user.getNickname());
            data.put("description", user.getDescription());
            data.put("address", user.getAddress());
            response.put("data", data);
        } else {
            response.put("success", false);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> updateUser(@RequestHeader("token") String token, @PathVariable String userId, @RequestBody User updateInfo) {
        User updatedUser = userService.updateUser(userId, updateInfo);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", updatedUser);
        return ResponseEntity.ok(response);
    }
}