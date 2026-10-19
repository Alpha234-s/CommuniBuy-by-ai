package hy.CommunityBuy.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import hy.CommunityBuy.dto.Api;
import hy.CommunityBuy.repository.*;
import java.time.*;
import java.util.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecretKey jwtKey(@Value("${app.jwt.secret}") String encoded) {
    byte[] bytes = Base64.getDecoder().decode(encoded);
    if (bytes.length < 32)
      throw new IllegalArgumentException(
          "JWT_SECRET requires at least 32 random bytes, Base64 encoded");
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtEncoder encoder(SecretKey key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder decoder(
      SecretKey key,
      Clock clock,
      UserRepository users,
      BlacklistRepository blacklist,
      @Value("${app.jwt.issuer}") String issuer,
      @Value("${app.jwt.audience}") String audience) {
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(
        jwt -> {
          var now = clock.instant();
          var user =
              jwt.getSubject() == null
                  ? Optional.<hy.CommunityBuy.entity.User>empty()
                  : users.findById(jwt.getSubject());
          Object version = jwt.getClaims().get("ver");
          boolean valid =
              issuer.equals(jwt.getClaimAsString("iss"))
                  && jwt.getAudience() != null
                  && jwt.getAudience().contains(audience)
                  && jwt.getExpiresAt() != null
                  && jwt.getExpiresAt().isAfter(now)
                  && jwt.getIssuedAt() != null
                  && !jwt.getIssuedAt().isAfter(now)
                  && jwt.getNotBefore() != null
                  && !jwt.getNotBefore().isAfter(now)
                  && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                  && jwt.getId() != null
                  && jwt.getId()
                      .matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                  && user.isPresent()
                  && user.get().getRole() >= 0
                  && (version instanceof Long || version instanceof Integer)
                  && ((Number) version).longValue() == user.get().getAuthVersion()
                  && !blacklist.existsById(jwt.getId());
          return valid
              ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(
                  new OAuth2Error("invalid_token", "Invalid or expired token", null));
        });
    return decoder;
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      JwtDecoder decoder,
      UserRepository users,
      ObjectMapper json,
      @org.springframework.beans.factory.annotation.Qualifier("cors")
          org.springframework.web.cors.CorsConfigurationSource cors)
      throws Exception {
    http.cors(x -> x.configurationSource(cors))
        .csrf(x -> x.disable())
        .sessionManagement(x -> x.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            x ->
                x.requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth",
                        "/api/v1/auth/register",
                        "/api/v1/payments/simulator/notify",
                        "/api/v1/refunds/simulator/notify")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            x ->
                x.authenticationEntryPoint(
                        (req, res, e) -> {
                          res.setStatus(401);
                          res.setContentType("application/json");
                          json.writeValue(
                              res.getOutputStream(),
                              Api.error("UNAUTHENTICATED", "Authentication required"));
                        })
                    .accessDeniedHandler(
                        (req, res, e) -> {
                          res.setStatus(403);
                          res.setContentType("application/json");
                          json.writeValue(
                              res.getOutputStream(), Api.error("FORBIDDEN", "Access denied"));
                        }))
        .oauth2ResourceServer(
            x ->
                x.authenticationEntryPoint(
                        (req, res, e) -> {
                          res.setStatus(401);
                          res.setContentType("application/json");
                          json.writeValue(
                              res.getOutputStream(),
                              Api.error("UNAUTHENTICATED", "Invalid or expired token"));
                        })
                    .jwt(
                        j ->
                            j.decoder(decoder)
                                .jwtAuthenticationConverter(
                                    jwt -> {
                                      var user = users.findById(jwt.getSubject()).orElseThrow();
                                      return new JwtAuthenticationToken(
                                          jwt,
                                          List.of(
                                              new SimpleGrantedAuthority(
                                                  user.getRole() > 0 ? "ROLE_ADMIN" : "ROLE_USER")),
                                          user.getId());
                                    })));
    return http.build();
  }
}
