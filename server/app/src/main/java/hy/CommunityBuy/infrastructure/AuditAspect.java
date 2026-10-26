package hy.CommunityBuy.infrastructure;

import hy.CommunityBuy.exception.BusinessException;
import hy.CommunityBuy.security.Actor;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(10)
@RequiredArgsConstructor
public class AuditAspect {
  private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
  private final AuditSink sink;

  @Around("@annotation(audit)")
  public Object around(ProceedingJoinPoint call, BusinessAudit audit) throws Throwable {
    var args = call.getArgs();
    String actor = "SYSTEM";
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && !(auth instanceof AnonymousAuthenticationToken)) actor = auth.getName();
    else if (args.length > 0 && args[0] instanceof Actor a) actor = a.id();
    String action = audit.value(), resource = "UNASSIGNED";
    if (audit.operationArgument() >= 0
        && args[audit.operationArgument()] instanceof String op
        && op.matches("[a-z]{1,12}")) action += ":" + op;
    if (audit.resourceArgument() >= 0) resource = String.valueOf(args[audit.resourceArgument()]);
    try {
      var result = call.proceed();
      if (audit.resourceArgument() < 0 && result != null && result.getClass().isRecord()) {
        try {
          resource = String.valueOf(result.getClass().getMethod("id").invoke(result));
        } catch (ReflectiveOperationException ignored) {
          /* no identifier, never inspect payload */
        }
      }
      sink.success(actor, action, resource);
      return result;
    } catch (Throwable error) {
      try {
        int status =
            error instanceof BusinessException e
                ? e.getStatus()
                : error instanceof org.springframework.dao.DataIntegrityViolationException
                        || error instanceof org.springframework.dao.ConcurrencyFailureException
                    ? 409
                    : 500;
        sink.failure(actor, action, resource, status);
      } catch (RuntimeException unavailable) {
        log.warn(
            "event=audit.storage action={} traceId={} diagnostic={}",
            action,
            MDC.get("traceId"),
            SafeDiagnostics.describe(unavailable));
      }
      throw error;
    }
  }
}
