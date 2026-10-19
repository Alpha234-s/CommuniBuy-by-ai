package hy.CommunityBuy.controller;
import hy.CommunityBuy.dto.*;
import hy.CommunityBuy.dto.Dtos.Stats;
import hy.CommunityBuy.security.Actor;
import hy.CommunityBuy.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class AdminController {
    private final AdminService admin;
    @GetMapping("/api/v1/stats") public Api<Stats> stats() { return Api.ok(admin.stats(Actor.current())); }
}
