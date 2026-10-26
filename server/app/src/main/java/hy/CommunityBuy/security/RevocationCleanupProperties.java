package hy.CommunityBuy.security;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.auth.revocation-cleanup")
public record RevocationCleanupProperties(
    @DefaultValue("60000") @Min(1000) @Max(86400000) long delayMs,
    @DefaultValue("500") @Min(1) @Max(10000) int batchSize) {}
