package hy.CommunityBuy.controller;

import hy.CommunityBuy.dto.*;
import hy.CommunityBuy.dto.Dtos.*;
import hy.CommunityBuy.security.Actor;
import hy.CommunityBuy.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Validated
public class UserController {
  private final UserService users;

  @GetMapping
  public Api<PageView<String>> list(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return Api.ok(users.list(Actor.current(), page, size));
  }

  @PostMapping
  public Api<UserView> create(@Valid @RequestBody CreateUser r) {
    Actor.current().requireAdmin();
    return Api.ok(users.create(r));
  }

  @GetMapping("/{id}")
  public Api<UserView> get(@PathVariable @Pattern(regexp = Dtos.USER_ID) String id) {
    return Api.ok(users.get(Actor.current(), id));
  }

  @PostMapping("/{id}")
  public Api<UserView> update(
      @PathVariable @Pattern(regexp = Dtos.USER_ID) String id, @Valid @RequestBody Profile r) {
    return Api.ok(users.update(Actor.current(), id, r));
  }

  @PostMapping("/{id}/role")
  public Api<UserView> role(
      @PathVariable @Pattern(regexp = Dtos.USER_ID) String id, @Valid @RequestBody Role r) {
    return Api.ok(users.role(Actor.current(), id, r.role()));
  }

  @PostMapping("/{id}/ban")
  public Api<UserView> ban(@PathVariable @Pattern(regexp = Dtos.USER_ID) String id) {
    return Api.ok(users.role(Actor.current(), id, -1));
  }
}
