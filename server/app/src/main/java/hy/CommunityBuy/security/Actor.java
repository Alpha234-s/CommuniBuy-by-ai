package hy.CommunityBuy.security;
import hy.CommunityBuy.exception.BusinessException;
import org.springframework.security.core.context.SecurityContextHolder;
public record Actor(String id, boolean admin) {
    public static Actor current() {
        var a = SecurityContextHolder.getContext().getAuthentication();
        return new Actor(a.getName(), a.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_ADMIN")));
    }
    public void requireAdmin() { if (!admin) throw BusinessException.forbidden(); }
    public void requireSelf(String other) { if (!admin && !id.equals(other)) throw BusinessException.forbidden(); }
}
