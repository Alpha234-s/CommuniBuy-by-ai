package hy.CommunityBuy;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import hy.CommunityBuy.dto.Dtos.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(
    properties = {
      "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
      "app.jwt.ttl-seconds=3600",
      "app.orders.scheduling-enabled=false",
      "app.auth.legacy-password-login=true",
      "app.payment.mode=simulator",
      "app.payment.simulator-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
      "app.rate-limits.enabled=true",
      "app.rate-limits.login-max=3",
      "app.rate-limits.registration-max=3",
      "app.rate-limits.mutation-max=3",
      "app.rate-limits.sensitive-max=3",
      "app.cors.allowed-origins=http://localhost:3000"
    })
@Import(IntegrationSupport.TimeConfig.class)
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
class ReliabilityIntegrationTest extends IntegrationSupport {
  static final AtomicInteger window = new AtomicInteger();

  @BeforeEach
  void newWindow() {
    clock.now.set(
        java.time.Instant.parse("2026-10-19T00:00:00Z")
            .plusSeconds(window.incrementAndGet() * 180L));
  }

  @Test
  void loginLimitsCannotBeBypassedWithForwardedHeadersAndRecoverByClock() throws Exception {
    for (int i = 0; i < 3; i++)
      request(null, "POST", "/api/v1/auth", new Login("buyer", "wrong"))
          .andExpect(status().isUnauthorized());
    var result =
        mvc.perform(
                post("/api/v1/auth")
                    .header("X-Forwarded-For", "203.0.113.11")
                    .contentType("application/json")
                    .content(json.writeValueAsBytes(new Login("buyer", "wrong"))))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
            .andExpect(header().exists("Retry-After"))
            .andReturn();
    assertThat(json.readTree(result.getResponse().getContentAsString()).get("traceId").asText())
        .isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
    clock.now.set(clock.instant().plusSeconds(61));
    request(null, "POST", "/api/v1/auth", new Login("buyer", "wrong"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void mutationLimitsArePerAuthenticatedUserAndReadsAreIndependent() throws Exception {
    for (int i = 0; i < 3; i++)
      request("seller", "POST", "/api/v1/user/seller", Map.of("nickname", "Seller " + i))
          .andExpect(status().isOk());
    request("seller", "POST", "/api/v1/user/seller", Map.of("nickname", "Rejected"))
        .andExpect(status().isTooManyRequests());
    request("seller", "GET", "/api/v1/item", null).andExpect(status().isOk());
    request("buyer", "POST", "/api/v1/user/buyer", Map.of("nickname", "Independent"))
        .andExpect(status().isOk());
  }

  @Test
  void auditRecordsCorrelateTraceAndHideIdentifiersAndPrivateValues() throws Exception {
    var result =
        request("admin", "POST", "/api/v1/user/buyer/role", new Role(0))
            .andExpect(status().isOk())
            .andReturn();
    String trace = result.getResponse().getHeader("X-Trace-Id");
    var rows =
        jdbc.queryForList(
            "select * from audit_events where action='USER_ROLE' and trace_id=?", trace);
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().get("actor_hash").toString()).matches("[a-f0-9]{64}");
    assertThat(rows.toString()).doesNotContain("Private buyer", "Test-password", "Bearer");
    request("buyer", "POST", "/api/v1/user/seller/role", new Role(1))
        .andExpect(status().isForbidden());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_events where outcome='FAILURE' and http_status=403",
                Long.class))
        .isEqualTo(1);
    request("buyer", "GET", "/api/v1/audit", null).andExpect(status().isForbidden());
    request("admin", "GET", "/api/v1/audit", null).andExpect(status().isOk());
  }

  @Test
  void mandatorySuccessAuditFailureRollsBackInventoryAndOrder() {
    long item = item(1).id();
    jdbc.execute(
        "alter table audit_events add constraint ck_test_audit_failure"
            + " check(action<>'ORDER_CREATE')");
    try {
      assertThatThrownBy(() -> order(item, "audit-failure"))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    } finally {
      jdbc.execute("alter table audit_events drop check ck_test_audit_failure");
    }
    assertThat(orders.count()).isZero();
    assertThat(items.findById(item).orElseThrow().getStock()).isEqualTo(1);
  }

  @Test
  void healthMetricsAndPrometheusAreProtectedAndContainDatabaseAndRequestMetrics()
      throws Exception {
    request(null, "GET", "/actuator/health", null).andExpect(status().isUnauthorized());
    request("buyer", "GET", "/actuator/health", null).andExpect(status().isForbidden());
    request("admin", "GET", "/actuator/health", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    request("buyer", "GET", "/api/v1/item", null).andExpect(status().isOk());
    String metrics =
        request("admin", "GET", "/actuator/metrics", null)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(metrics)
        .contains("jvm.memory.used", "hikaricp.connections.active", "http.server.requests");
    String prometheus =
        request("admin", "GET", "/actuator/prometheus", null)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(prometheus).contains("jvm_memory_used_bytes", "hikaricp_connections_active");
  }

  @Test
  void corsRequiresExactOriginAndDoesNotEnableCookieCredentials() throws Exception {
    mvc.perform(
            options("/api/v1/cart/1")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    mvc.perform(
            options("/api/v1/cart/1")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "PUT"))
        .andExpect(status().isForbidden());
  }

  @Test
  void rawHtmlIsRejectedAndKeywordSqlIsParameterized() throws Exception {
    request(
            "seller",
            "POST",
            "/api/v1/item",
            new NewItem("Title", "<script>alert(1)</script>", 1, 1, 0, 0L, null))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/item")
                .param("keyword", "' OR 1=1 --")
                .header("Authorization", "Bearer " + token("buyer")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.count").value(0));
    request("buyer", "POST", "/api/v1/user/buyer", Map.of("description", "<img src=x>"))
        .andExpect(status().isBadRequest());
  }
}
