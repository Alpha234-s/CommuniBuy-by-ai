package hy.CommunityBuy.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import hy.CommunityBuy.dto.Api;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class RequestLimitFilter extends OncePerRequestFilter {
  private final Clock clock;
  private final ObjectMapper json;
  private final MeterRegistry meters;
  private final Map<String, Window> windows = new HashMap<>();

  @Value("${app.rate-limits.enabled:true}")
  private boolean enabled;

  @Value("${app.rate-limits.login-max:10}")
  private int loginMax;

  @Value("${app.rate-limits.registration-max:10}")
  private int registrationMax;

  @Value("${app.rate-limits.mutation-max:300}")
  private int mutationMax;

  @Value("${app.rate-limits.sensitive-max:60}")
  private int sensitiveMax;

  public RequestLimitFilter(Clock clock, ObjectMapper json, MeterRegistry meters) {
    this.clock = clock;
    this.json = json;
    this.meters = meters;
  }

  @jakarta.annotation.PostConstruct
  void validate() {
    for (int n : new int[] {loginMax, registrationMax, mutationMax, sensitiveMax})
      if (n < 1 || n > 1000000) throw new IllegalArgumentException("Invalid request limit");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!enabled || Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
      chain.doFilter(request, response);
      return;
    }
    String path = request.getRequestURI(), key;
    int limit;
    if (path.equals("/api/v1/auth")) {
      key = "login:" + request.getRemoteAddr();
      limit = loginMax;
    } else if (path.equals("/api/v1/auth/register")) {
      key = "registration:" + request.getRemoteAddr();
      limit = registrationMax;
    } else if (path.equals("/api/v1/payments/simulator/notify")
        || path.equals("/api/v1/refunds/simulator/notify")) {
      key = "channel:" + request.getRemoteAddr();
      limit = 300;
    } else {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth == null) {
        chain.doFilter(request, response);
        return;
      }
      boolean sensitive =
          path.startsWith("/api/v1/order")
              || path.equals("/api/v1/auth/reset")
              || path.endsWith("/role")
              || path.endsWith("/ban")
              || path.startsWith("/api/v1/checkout")
              || path.startsWith("/api/v1/payments")
              || path.startsWith("/api/v1/refunds")
              || path.startsWith("/api/v1/settlements");
      key = (sensitive ? "sensitive:" : "mutation:") + auth.getName();
      limit = sensitive ? sensitiveMax : mutationMax;
    }
    long now = clock.instant().getEpochSecond();
    if (!allow(key, limit, now / 60)) {
      response.setStatus(429);
      response.setHeader("Retry-After", String.valueOf(60 - now % 60));
      response.setContentType("application/json");
      meters.counter("communibuy.ratelimit.rejections").increment();
      json.writeValue(
          response.getOutputStream(), Api.error("RATE_LIMITED", "Request limit exceeded"));
      return;
    }
    chain.doFilter(request, response);
  }

  private synchronized boolean allow(String key, int limit, long minute) {
    var current = windows.get(key);
    if (current == null || current.minute != minute) {
      if (windows.size() >= 10000) {
        windows.entrySet().removeIf(e -> e.getValue().minute != minute);
        if (windows.size() >= 10000 && !windows.containsKey(key)) return false;
      }
      current = new Window(minute);
      windows.put(key, current);
    }
    if (current.count >= limit) return false;
    current.count++;
    return true;
  }

  private static final class Window {
    final long minute;
    int count;

    Window(long minute) {
      this.minute = minute;
    }
  }
}
