package hy.CommunityBuy.service;
import hy.CommunityBuy.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
@Component @RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.orders.scheduling-enabled", havingValue="true", matchIfMissing=true)
public class OrderExpiry {
    private static final Logger log=LoggerFactory.getLogger(OrderExpiry.class);
    private final OrderRepository orders;
    private final OrderService service;
    private final Clock clock;
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString="${app.orders.sweep-delay-ms}")
    public void sweep() {
        // Bounded work per sweep; restart compensation uses the persisted deadline, not process timers.
        for (long id : orders.expired(clock.instant().getEpochSecond(),PageRequest.of(0,100))) {
            try { service.expire(id); }
            catch (RuntimeException e) { log.warn("Order expiry deferred for order {} ({})",id,e.getClass().getSimpleName()); }
        }
    }
}
