package hy.CommunityBuy.controller;

import hy.CommunityBuy.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/stats")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getStats(@RequestHeader("token") String token) {
        // 简化版：实际应校验管理员权限
        Map<String, Object> response = adminService.getGlobalStats();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }
}