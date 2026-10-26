package hy.CommunityBuy.infrastructure;

import java.util.*;
import org.slf4j.MDC;

/**
 * Structural diagnostics only: never render Throwable messages, toString or suppressed payloads.
 */
public final class SafeDiagnostics {
  private SafeDiagnostics() {}

  public static String describe(Throwable error) {
    var result = new StringBuilder();
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    int depth = 0;
    while (error != null && depth < 8 && seen.add(error)) {
      if (depth > 0) result.append(" <- ");
      result.append(error.getClass().getName());
      if (error instanceof java.sql.SQLException sql)
        result
            .append("[vendor=")
            .append(sql.getErrorCode())
            .append(",state=")
            .append(
                sql.getSQLState() != null && sql.getSQLState().matches("[A-Z0-9]{5}")
                    ? sql.getSQLState()
                    : "unknown")
            .append("]");
      var frames = error.getStackTrace();
      var positions = new LinkedHashSet<StackTraceElement>();
      if (frames.length > 0) positions.add(frames[0]);
      // Retain the throwing location and nearby application frames; bound library/proxy noise.
      for (int n = 0; n < Math.min(frames.length, 256) && positions.size() < 4; n++)
        if (frames[n].getClassName().startsWith("hy.CommunityBuy.")) positions.add(frames[n]);
      for (var frame : positions)
        result
            .append(" at ")
            .append(frame.getClassName())
            .append("#")
            .append(frame.getMethodName())
            .append(":")
            .append(frame.getLineNumber());
      error = error.getCause();
      depth++;
    }
    if (error != null) result.append(" [cause chain truncated/cyclic]");
    return result.toString();
  }

  /** Give background work a trace while preserving any caller's MDC. */
  public static TraceScope traceScope() {
    return new TraceScope();
  }

  public static final class TraceScope implements AutoCloseable {
    private final String previous = MDC.get("traceId");

    private TraceScope() {
      if (previous == null) MDC.put("traceId", UUID.randomUUID().toString().replace("-", ""));
    }

    public void close() {
      if (previous == null) MDC.remove("traceId");
      else MDC.put("traceId", previous);
    }
  }
}
