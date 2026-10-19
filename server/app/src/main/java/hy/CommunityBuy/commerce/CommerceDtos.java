package hy.CommunityBuy.commerce;
import hy.CommunityBuy.dto.Dtos.OrderView;
import jakarta.validation.constraints.*;
import java.util.List;
public final class CommerceDtos {
    private CommerceDtos() {}
    public record Category(long id,String name) {}
    public record CategoryRequest(@NotBlank @Size(max=64) String name) {}
    public record CategoryAssignment(@NotNull @Positive Long categoryId) {}
    public record CartChange(@NotNull @Positive Integer quantity,@NotNull Boolean selected) {}
    public record CartLine(long itemId,int quantity,boolean selected,String name,int price,int stock,int status,long startTime,Long endTime) {}
    public record CartView(long version,List<CartLine> lines) {}
    public record Checkout(@NotBlank @Size(max=255) String address,@NotNull @Min(0) Long cartVersion,@Min(0) Long expectedTotal) {}
    public record Line(long itemId,int quantity,int price,long total,String itemName,String itemDescription,String snapshotSource) {}
    public record DetailedOrder(OrderView order,List<Line> lines) {}
    public record CheckoutView(String id,long total,List<DetailedOrder> orders) {}
}
