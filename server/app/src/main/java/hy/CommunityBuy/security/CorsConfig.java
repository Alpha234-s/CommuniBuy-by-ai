package hy.CommunityBuy.security;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.cors.*;

@Configuration
public class CorsConfig {
  @Bean
  CorsConfigurationSource cors(@Value("${app.cors.allowed-origins:}") String origins) {
    var allowed =
        Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    for (String origin : allowed) {
      var uri = java.net.URI.create(origin);
      if (!Set.of("http", "https").contains(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || !uri.getPath().isEmpty()
          || uri.getQuery() != null
          || uri.getFragment() != null)
        throw new IllegalArgumentException("CORS requires exact HTTP origins");
    }
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(allowed);
    cors.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
    cors.setExposedHeaders(List.of("X-Trace-Id", "Retry-After"));
    cors.setAllowCredentials(false);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return source;
  }
}
