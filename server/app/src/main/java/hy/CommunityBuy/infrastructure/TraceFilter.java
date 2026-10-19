package hy.CommunityBuy.infrastructure;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger("http.access");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String traceId = UUID.randomUUID().toString().replace("-", "");
    long start = System.nanoTime();
    MDC.put("traceId", traceId);
    response.setHeader("X-Trace-Id", traceId);
    try {
      chain.doFilter(request, response);
    } finally {
      // Route template only: never log URI parameters, query, body, credentials or Authorization.
      Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      log.info(
          "method={} route={} status={} durationMs={}",
          request.getMethod(),
          route == null ? "unmapped" : route,
          response.getStatus(),
          (System.nanoTime() - start) / 1_000_000);
      MDC.remove("traceId");
    }
  }
}
