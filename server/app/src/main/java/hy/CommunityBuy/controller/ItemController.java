package hy.CommunityBuy.controller;
import hy.CommunityBuy.dto.*;
import hy.CommunityBuy.dto.Dtos.*;
import hy.CommunityBuy.security.Actor;
import hy.CommunityBuy.service.ItemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/item") @RequiredArgsConstructor @Validated
public class ItemController {
    private final ItemService items;
    @GetMapping public Api<PageView<ItemView>> list(@RequestParam(required=false) @Size(max=128) String keyword,
        @RequestParam(required=false) @Min(-1) @Max(0) Integer status,
        @RequestParam(required=false) @Pattern(regexp=Dtos.USER_ID) String owner,
        @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return Api.ok(items.list(keyword,status,owner,page,size)); }
    @GetMapping("/{id}") public Api<ItemView> get(@PathVariable @Positive long id) { return Api.ok(items.get(id)); }
    @PostMapping public Api<ItemView> create(@Valid @RequestBody NewItem r) { return Api.ok(items.create(Actor.current(),r)); }
    @PostMapping("/{id}") public Api<ItemView> update(@PathVariable @Positive long id, @Valid @RequestBody EditItem r) { return Api.ok(items.update(Actor.current(),id,r)); }
}
