package hy.CommunityBuy.dto;
import com.fasterxml.jackson.annotation.JsonAlias;
import hy.CommunityBuy.entity.*;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import java.util.List;
public final class Dtos {
    private Dtos() {}
    public static final String USER_ID = "[A-Za-z_][A-Za-z0-9_]{0,31}";
    public record Login(@NotBlank @Pattern(regexp=USER_ID) String user, @NotBlank @Size(max=72) String password) {}
    public record CreateUser(@NotBlank @Pattern(regexp=USER_ID) String user, @NotBlank @Size(min=8,max=72) String password) {}
    public record Reset(@NotBlank @Size(max=72) String oldPassword, @NotBlank @Size(min=8,max=72) String newPassword) {}
    public record Profile(@Size(min=1,max=64) String nickname, @Size(max=255) String description, @Size(min=1,max=255) String address) {}
    public record Role(@NotNull @Min(-1) @Max(1) Integer role) {}
    public record NewItem(@NotBlank @Size(max=128) String name, @Size(max=10000) String description,
        @NotNull @Min(0) Integer price, @NotNull @Min(0) Integer stock,
        @Min(-1) @Max(0) Integer status, @Min(0) Long startTime, @Min(-1) Long endTime) {}
    public record EditItem(@Size(min=1,max=128) String name, @Size(max=10000) String description,
        @Min(0) Integer price, @Min(0) Integer stock, @Min(-1) @Max(0) Integer status,
        @Min(0) Long startTime, @Min(-1) Long endTime, @Min(0) Long version) {}
    public record NewOrder(@JsonAlias("item") @NotNull @Positive Long itemId,
        @NotNull @Positive Integer quantity, @NotBlank @Size(max=255) String address) {}
    public record Address(@NotBlank @Size(max=255) String address) {}
    public record Token(String token, String tokenType, long expiresAt) {}
    public record UserView(String id, String nickname, String description, String address, int role) {
        public static UserView of(User u) { return new UserView(u.getId(),u.getNickname(),u.getDescription(),u.getAddress(),u.getRole()); }
    }
    public record ItemView(long id, String name, String description, int price, int stock, int status,
        String owner, long startTime, Long endTime, long shipmentCount, long version, Long categoryId) {
        public static ItemView of(Item i) { return new ItemView(i.getId(),i.getName(),i.getDescription(),i.getPrice(),i.getStock(),
            i.getStatus(),i.getOwnerId(),i.getStartTime(),i.getEndTime(),i.getShipmentCount(),i.getVersion(),i.getCategoryId()); }
    }
    public record OrderView(long id, long itemId, String user, String owner, int quantity, int price, long total,
        String address, int status, long createTime, Long payTime, Long shipTime, Long receiptTime, Long closeTime,
        long expiresAt, String refundStatus, String itemName, String itemDescription, String snapshotSource, long refundedTotal) {
        public static OrderView of(Order o, boolean addressVisible) { return new OrderView(o.getId(),o.getItemId(),o.getUserId(),o.getOwnerId(),
            o.getQuantity(),o.getPrice(),o.getTotal(),addressVisible ? o.getAddress() : null,o.getStatus(),o.getCreateTime(),o.getPayTime(),
            o.getShipTime(),o.getReceiptTime(),o.getCloseTime(),o.getExpiresAt(),o.getRefundStatus(),o.getItemName(),o.getItemDescription(),o.getSnapshotSource(),o.getRefundedTotal()); }
    }
    public record PageView<T>(List<T> content, long count, int page, int size) {
        public static <T> PageView<T> of(Page<T> p) { return new PageView<>(p.getContent(),p.getTotalElements(),p.getNumber(),p.getSize()); }
    }
    public record Stats(long userCount, long itemCount, long orderCount, java.math.BigInteger totalRevenue, long ongoingItemCount, java.math.BigInteger grossPaidAmount, java.math.BigInteger refundAmount, java.math.BigInteger netPaidAmount, long unreconciledPaidOrderCount) {}
}
