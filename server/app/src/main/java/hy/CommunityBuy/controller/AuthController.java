package hy.CommunityBuy.controller;
import hy.CommunityBuy.dto.*;
import hy.CommunityBuy.dto.Dtos.*;
import hy.CommunityBuy.security.Actor;
import hy.CommunityBuy.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    private final UserService users;
    @PostMapping public Api<Token> login(@Valid @RequestBody Login request) { return Api.ok(auth.login(request)); }
    @PostMapping("/register") public Api<UserView> register(@Valid @RequestBody CreateUser r) { return Api.ok(users.create(r)); }
    @GetMapping public Api<UserView> current() { var a=Actor.current(); return Api.ok(users.get(a,a.id())); }
    @PostMapping("/logout") public Api<Void> logout(@AuthenticationPrincipal Jwt jwt) { auth.logout(jwt); return Api.ok(null); }
    @PostMapping("/reset") public Api<Void> reset(@Valid @RequestBody Reset r) { auth.reset(Actor.current(),r); return Api.ok(null); }
}
