package hy.CommunityBuy.exception;
import hy.CommunityBuy.dto.Api;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.*;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
@RestControllerAdvice
public class ApiErrors {
    private static String code(int status) {
        return switch(status) { case 400 -> "INVALID_REQUEST"; case 401 -> "UNAUTHENTICATED";
            case 403 -> "FORBIDDEN"; case 404 -> "NOT_FOUND"; case 409 -> "CONFLICT"; default -> "INTERNAL_ERROR"; };
    }
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Api<Void>> business(BusinessException e) { return ResponseEntity.status(e.getStatus()).body(Api.error(code(e.getStatus()),e.getMessage())); }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
        HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        MissingRequestHeaderException.class, MissingServletRequestParameterException.class,
        HandlerMethodValidationException.class})
    ResponseEntity<Api<Void>> invalid(Exception e) { return ResponseEntity.badRequest().body(Api.error("INVALID_REQUEST","Invalid request")); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Api<Void>> denied() { return ResponseEntity.status(403).body(Api.error("FORBIDDEN","Access denied")); }
    @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class})
    ResponseEntity<Api<Void>> conflict(Exception e) { return ResponseEntity.status(409).body(Api.error("CONFLICT","Data conflict; refresh and retry")); }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Api<Void>> missing() { return ResponseEntity.status(404).body(Api.error("NOT_FOUND","Resource not found")); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Api<Void>> method() { return ResponseEntity.status(405).body(Api.error("METHOD_NOT_ALLOWED","Method not allowed")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Api<Void>> unexpected(Exception e) {
        log.error("Unhandled API failure ({})", e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(Api.error("INTERNAL_ERROR","Internal server error"));
    }
}
