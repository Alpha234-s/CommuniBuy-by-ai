package hy.CommunityBuy.controller;

import hy.CommunityBuy.entity.User;
import hy.CommunityBuy.service.AuthService;
import hy.CommunityBuy.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String userId = request.get("user");
        String password = request.get("password");
        String token = authService.login(userId, password);

        Map<String, Object> response = new HashMap<>();
        if (token != null) {
            response.put("success", true);
            response.put("token", token);
        } else {
            response.put("success", false);
            response.put("message", "Invalid username or password");
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestBody Map<String, String> request) {
        // 实际应用中会处理 blacklist
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        // 简化版：实际应校验 token 和旧密码
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getCurrentUser(@RequestHeader("token") String token) {
        String userId = authService.getCurrentUserId(token);
        Map<String, Object> response = new HashMap<>();
        if (userId != null) {
            response.put("success", true);
            response.put("user", userId);
        } else {
            response.put("success", false);
        }
        return ResponseEntity.ok(response);
    }
}