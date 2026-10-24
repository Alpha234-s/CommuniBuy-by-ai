package hy.CommunityBuy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import hy.CommunityBuy.exception.ApiErrors;
import hy.CommunityBuy.infrastructure.*;
import hy.CommunityBuy.repository.OrderRepository;
import hy.CommunityBuy.service.*;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.slf4j.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class SafeDiagnosticsTest {
  static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJidXllciJ9.c2lnbmF0dXJl";
  static final String SECRET =
      "password-secret full-token-secret delivery-address-secret request-body-secret"
          + " sql-parameter-secret";

  @RestController
  static class FailingController {
    @PostMapping("/diagnostic-test")
    public void fail() {
      throw failure();
    }
  }

  static RuntimeException failure() {
    return new IllegalStateException(
        SECRET + TOKEN,
        new SQLException(
            "insert into users(password) values ('" + SECRET + TOKEN + "')", "HY000", 1234));
  }

  static class Capture extends ListAppender<ILoggingEvent> implements AutoCloseable {
    final List<Logger> loggers = new ArrayList<>();

    Capture(String... names) {
      start();
      for (String name : names) {
        var logger = (Logger) LoggerFactory.getLogger(name);
        loggers.add(logger);
        logger.addAppender(this);
      }
    }

    @Override
    protected void append(ILoggingEvent event) {
      event.prepareForDeferredProcessing();
      super.append(event);
    }

    String messages() {
      return list.stream()
          .map(ILoggingEvent::getFormattedMessage)
          .collect(java.util.stream.Collectors.joining("\n"));
    }

    public void close() {
      for (var logger : loggers) logger.detachAppender(this);
      stop();
    }
  }

  @Test
  void httpFailureCorrelatesTraceAndCodeLocationWithoutMessagesOrRequestData() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new ApiErrors())
            .addFilters(new TraceFilter())
            .build();
    try (var capture = new Capture(ApiErrors.class.getName(), "http.access")) {
      var response =
          mvc.perform(
                  post("/diagnostic-test?query=query-secret")
                      .header("Authorization", "Bearer " + TOKEN)
                      .header("X-Trace-Id", "untrusted-trace-secret")
                      .contentType("application/json")
                      .content(SECRET))
              .andExpect(status().isInternalServerError())
              .andReturn()
              .getResponse();
      var body = new ObjectMapper().readTree(response.getContentAsString());
      String trace = response.getHeader("X-Trace-Id");
      assertThat(trace).matches("[a-f0-9]{32}");
      assertThat(body.get("traceId").asText()).isEqualTo(trace);
      assertThat(body.get("code").asText()).isEqualTo("INTERNAL_ERROR");
      assertThat(body.get("message").asText()).isEqualTo("Internal server error");
      assertThat(capture.list)
          .hasSize(2)
          .allSatisfy(
              event -> {
                assertThat(event.getMDCPropertyMap().get("traceId")).isEqualTo(trace);
                assertThat(event.getThrowableProxy()).isNull();
              });
      assertThat(capture.messages())
          .contains(
              "event=api.unexpected",
              trace,
              "java.lang.IllegalStateException",
              "java.sql.SQLException[vendor=1234,state=HY000]",
              "SafeDiagnosticsTest#failure:")
          .doesNotContain(
              "password-secret",
              "full-token-secret",
              "delivery-address-secret",
              "request-body-secret",
              "sql-parameter-secret",
              "query-secret",
              "untrusted-trace-secret",
              "Bearer",
              TOKEN,
              "insert into users");
      assertThat(response.getContentAsString())
          .doesNotContain("Exception", "SafeDiagnosticsTest", "secret", "SQLException");
      assertThat(MDC.get("traceId")).isNull();
    }
  }

  @Test
  void diagnosticsBoundCauseDepthHandleCyclesAndDoNotRenderSuppressedOrSqlStatePayloads() {
    Throwable root = new SQLException(SECRET, SECRET, 999);
    root.addSuppressed(new IllegalArgumentException(SECRET));
    assertThat(SafeDiagnostics.describe(root))
        .contains("vendor=999,state=unknown")
        .doesNotContain(SECRET, "password-secret");
    for (int n = 0; n < 20; n++) root = new IllegalStateException(SECRET, root);
    assertThat(SafeDiagnostics.describe(root))
        .contains("truncated/cyclic")
        .hasSizeLessThan(12000)
        .doesNotContain("password-secret");
    var a = new IllegalStateException(SECRET);
    var b = new IllegalArgumentException(SECRET);
    a.initCause(b);
    b.initCause(a);
    assertThat(SafeDiagnostics.describe(a))
        .contains("IllegalStateException", "IllegalArgumentException", "truncated/cyclic")
        .doesNotContain(SECRET);
  }

  @Test
  void expiryFailureHasTraceAndPositionAndDoesNotStopOtherOrders() {
    var repository = mock(OrderRepository.class);
    var service = mock(OrderService.class);
    when(repository.expired(anyLong(), any())).thenReturn(List.of(1L, 2L));
    doThrow(failure()).when(service).expire(1L);
    var task = new OrderExpiry(repository, service, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    try (var capture = new Capture(OrderExpiry.class.getName())) {
      task.sweep();
      verify(service).expire(2L);
      assertThat(capture.messages())
          .contains(
              "event=orders.expiry.deferred",
              "orderId=1",
              "SafeDiagnosticsTest#failure:",
              "SQLException")
          .doesNotContain("password-secret", "delivery-address-secret", "sql-parameter-secret");
      assertThat(capture.list).hasSize(1);
      assertThat(capture.list.getFirst().getMDCPropertyMap().get("traceId"))
          .matches("[a-f0-9]{32}");
      assertThat(capture.list.getFirst().getThrowableProxy()).isNull();
      assertThat(MDC.get("traceId")).isNull();
    }
  }

  @Test
  void expiryScanFailureIsSafeAndNextInvocationRecoversPreservingCallerTrace() {
    var repository = mock(OrderRepository.class);
    var service = mock(OrderService.class);
    when(repository.expired(anyLong(), any())).thenThrow(failure()).thenReturn(List.of(3L));
    var task = new OrderExpiry(repository, service, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    String trace = "ab".repeat(16);
    MDC.put("traceId", trace);
    try (var capture = new Capture(OrderExpiry.class.getName())) {
      task.sweep();
      task.sweep();
      verify(service).expire(3L);
      assertThat(capture.messages())
          .contains("event=orders.expiry.scan", trace, "SafeDiagnosticsTest#failure:")
          .doesNotContain("password-secret", "full-token-secret");
      assertThat(MDC.get("traceId")).isEqualTo(trace);
    } finally {
      MDC.remove("traceId");
    }
  }
}
