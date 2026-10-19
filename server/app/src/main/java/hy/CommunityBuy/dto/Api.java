package hy.CommunityBuy.dto;

import org.slf4j.MDC;

/** Stable envelope. code and traceId are additive to the original response contract. */
public record Api<T>(boolean success, String code, String message, T data, String traceId) {
    public static <T> Api<T> ok(T data) { return new Api<>(true,"OK","",data,MDC.get("traceId")); }
    public static Api<Void> error(String message) { return error("INTERNAL_ERROR",message); }
    public static Api<Void> error(String code,String message) { return new Api<>(false,code,message,null,MDC.get("traceId")); }
}
