package hy.CommunityBuy.commerce;
import hy.CommunityBuy.commerce.CommerceDtos.*;
import hy.CommunityBuy.dto.*;
import hy.CommunityBuy.dto.Dtos.*;
import hy.CommunityBuy.security.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor @Validated
public class ShopController {
    private final ShopService shop;
    @GetMapping("/categories") public Api<PageView<Category>> categories(@RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return Api.ok(shop.categories(page,size));}
    @PostMapping("/categories") public Api<Category> category(@Valid @RequestBody CategoryRequest r){return Api.ok(shop.category(Actor.current(),r));}
    @PutMapping("/item/{id}/category") public Api<ItemView> assign(@PathVariable @Positive long id,@Valid @RequestBody CategoryAssignment r){return Api.ok(shop.assign(Actor.current(),id,r.categoryId()));}
    @GetMapping("/cart") public Api<CartView> cart(){return Api.ok(shop.cart(Actor.current()));}
    @PutMapping("/cart/{id}") public Api<CartView> cartChange(@PathVariable @Positive long id,@Valid @RequestBody CartChange r){return Api.ok(shop.cartChange(Actor.current(),id,r));}
    @DeleteMapping("/cart/{id}") public Api<CartView> cartDelete(@PathVariable @Positive long id){return Api.ok(shop.cartDelete(Actor.current(),id));}
    @PostMapping("/checkout") public Api<CheckoutView> checkout(@Valid @RequestBody Checkout r,@RequestHeader("Idempotency-Key") @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String key){return Api.ok(shop.checkout(Actor.current(),r,key));}
    @GetMapping("/checkout/{id}") public Api<CheckoutView> batch(@PathVariable @Pattern(regexp="[a-f0-9-]{36}") String id){return Api.ok(shop.batch(Actor.current(),id));}
    @GetMapping("/order/{id}/details") public Api<DetailedOrder> details(@PathVariable @Positive long id){return Api.ok(shop.details(Actor.current(),id));}
}
