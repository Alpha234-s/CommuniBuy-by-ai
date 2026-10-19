package hy.CommunityBuy.service;
import hy.CommunityBuy.dto.Dtos.Stats;
import hy.CommunityBuy.repository.*;
import hy.CommunityBuy.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
@Service @RequiredArgsConstructor
public class AdminService {
    private final UserRepository users;
    private final ItemRepository items;
    private final OrderRepository orders;
    private final Clock clock;
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Stats stats(Actor actor) {
        actor.requireAdmin();
        return new Stats(users.count(),items.count(),orders.count(),orders.revenue(),items.countOngoing(clock.instant().getEpochSecond()));
    }
}
