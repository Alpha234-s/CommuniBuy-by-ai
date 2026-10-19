package hy.CommunityBuy.exception;
import lombok.Getter;
@Getter
public class BusinessException extends RuntimeException {
    private final int status;
    public BusinessException(int status, String message) { super(message); this.status = status; }
    public static BusinessException missing() { return new BusinessException(404, "Resource not found"); }
    public static BusinessException forbidden() { return new BusinessException(403, "Access denied"); }
    public static BusinessException conflict(String message) { return new BusinessException(409, message); }
    public static BusinessException bad(String message) { return new BusinessException(400, message); }
}
