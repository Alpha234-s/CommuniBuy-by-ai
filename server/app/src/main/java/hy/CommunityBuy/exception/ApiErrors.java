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
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Api<Void>> business(BusinessException e) { return ResponseEntity.status(e.getStatus()).body(Api.error(e.getMessage())); }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
        HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        MissingRequestHeaderException.class, MissingServletRequestParameterException.class,
        HandlerMethodValidationException.class})
    ResponseEntity<Api<Void>> invalid(Exception e) { return ResponseEntity.badRequest().body(Api.error("Invalid request")); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Api<Void>> denied() { return ResponseEntity.status(403).body(Api.error("Access denied")); }
    @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class})
    ResponseEntity<Api<Void>> conflict(Exception e) { return ResponseEntity.status(409).body(Api.error("Data conflict; refresh and retry")); }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Api<Void>> missing() { return ResponseEntity.status(404).body(Api.error("Resource not found")); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Api<Void>> method() { return ResponseEntity.status(405).body(Api.error("Method not allowed")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Api<Void>> unexpected(Exception e) {
        log.error("Unhandled API failure ({})", e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(Api.error("Internal server error"));
    }
}
