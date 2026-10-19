package hy.CommunityBuy.controller;

import hy.CommunityBuy.dto.*;
import hy.CommunityBuy.dto.Dtos.*;
import hy.CommunityBuy.security.Actor;
import hy.CommunityBuy.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
@Validated
public class OrderController {
  private final OrderService orders;

  @GetMapping
  public Api<PageView<OrderView>> list(
      @RequestParam(required = false) @Pattern(regexp = Dtos.USER_ID) String user,
      @RequestParam(required = false) @Pattern(regexp = Dtos.USER_ID) String owner,
      @RequestParam(required = false) @Positive Long item,
      @RequestParam(required = false) @Min(-1) @Max(3) Integer status,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return Api.ok(orders.list(Actor.current(), user, owner, item, status, page, size));
  }

  @PostMapping
  public Api<OrderView> create(
      @Valid @RequestBody NewOrder r,
      @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String key) {
    return Api.ok(orders.create(Actor.current(), r, key));
  }

  @GetMapping("/{id}")
  public Api<OrderView> get(@PathVariable @Positive long id) {
    return Api.ok(orders.get(Actor.current(), id));
  }

  // The former generic mutation endpoint now accepts address ONLY, using the same rule as /address.
  @PostMapping({"/{id}", "/{id}/address"})
  public Api<OrderView> address(@PathVariable @Positive long id, @Valid @RequestBody Address r) {
    return Api.ok(orders.address(Actor.current(), id, r));
  }

  @PostMapping("/{id}/{action:pay|ship|receive|cancel}")
  public Api<OrderView> action(@PathVariable @Positive long id, @PathVariable String action) {
    return Api.ok(orders.action(Actor.current(), id, action));
  }
}
