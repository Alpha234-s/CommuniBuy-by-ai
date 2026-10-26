package hy.CommunityBuy;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import hy.CommunityBuy.dto.Dtos.*;
import hy.CommunityBuy.entity.*;
import hy.CommunityBuy.repository.*;
import hy.CommunityBuy.security.Actor;
import hy.CommunityBuy.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;

@SpringBootTest(
    properties = {
      "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
      "app.rate-limits.enabled=false",
      "app.payment.mode=simulator",
      "app.payment.simulator-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
      "app.jwt.ttl-seconds=3600",
      "app.orders.scheduling-enabled=false",
      "app.auth.revocation-cleanup.enabled=false",
      "app.auth.legacy-password-login=true"
    })
@AutoConfigureMockMvc
@Tag("mysql")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(IntegrationSupport.TimeConfig.class)
abstract class IntegrationSupport {
  static final TestDatabase.Connection DB = TestDatabase.connect(false);

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::url);
    r.add("spring.datasource.username", DB::user);
    r.add("spring.datasource.password", DB::password);
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  static class MutableClock extends Clock {
    final AtomicReference<Instant> now =
        new AtomicReference<>(Instant.parse("2026-10-19T00:00:00Z"));

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now.get();
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired ItemRepository items;
  @Autowired OrderRepository orders;
  @Autowired UserService userService;
  @Autowired AuthService auth;
  @Autowired ItemService itemService;
  @Autowired OrderService orderService;
  @Autowired AdminService admin;
  @Autowired JwtEncoder encoder;
  @Autowired PasswordEncoder passwords;
  @Autowired MutableClock clock;
  final Actor buyer = new Actor("buyer", false),
      seller = new Actor("seller", false),
      outsider = new Actor("outsider", false),
      administrator = new Actor("admin", true);
  String hash;

  @BeforeAll
  void hash() {
    hash = passwords.encode("Test-password-42");
  }

  @BeforeEach
  void setup() {
    TestDatabase.reset(jdbc);
    clock.now.set(Instant.parse("2026-10-19T00:00:00Z"));
    for (String id : List.of("buyer", "seller", "outsider", "admin")) {
      var u = new User();
      u.setId(id);
      u.setPassword(hash);
      u.setRole(id.equals("admin") ? 1 : 0);
      u.setAddress("Private " + id);
      users.saveAndFlush(u);
    }
  }

  String token(String id) {
    return token(
        id, clock.instant(), clock.instant().plusSeconds(3600), "communibuy", "communibuy-api");
  }

  String token(String id, Instant issued, Instant expiry, String issuer, String audience) {
    var u = users.findById(id).orElseThrow();
    var c =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .audience(List.of(audience))
            .subject(id)
            .issuedAt(issued)
            .notBefore(issued)
            .expiresAt(expiry)
            .id(UUID.randomUUID().toString())
            .claim("ver", u.getAuthVersion())
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), c))
        .getTokenValue();
  }

  ResultActions request(String user, String method, String path, Object body) throws Exception {
    var b = method.equals("GET") ? get(path) : post(path);
    if (user != null) b.header("Authorization", "Bearer " + token(user));
    if (body != null) b.contentType("application/json").content(json.writeValueAsBytes(body));
    return mvc.perform(b);
  }

  ItemView item(int stock) {
    return itemService.create(
        seller, new NewItem("Apples", "fresh red fruit", 199, stock, 0, 0L, null));
  }

  OrderView order(long item, String key) {
    return orderService.create(buyer, new NewOrder(item, 1, "Buyer address"), key);
  }

  JsonNode data(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
  }

  <T> List<T> race(List<Supplier<T>> operations) throws Exception {
    var pool = Executors.newFixedThreadPool(operations.size());
    var ready = new CountDownLatch(operations.size());
    var go = new CountDownLatch(1);
    try {
      var futures = new ArrayList<Future<T>>();
      for (var op : operations)
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  if (!go.await(10, TimeUnit.SECONDS)) throw new AssertionError("race barrier");
                  return op.get();
                }));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      var result = new ArrayList<T>();
      for (var f : futures) result.add(f.get(30, TimeUnit.SECONDS));
      return result;
    } finally {
      pool.shutdownNow();
    }
  }

  boolean tryOrder(Actor a, long item, String key) {
    try {
      orderService.create(a, new NewOrder(item, 1, "address"), key);
      return true;
    } catch (hy.CommunityBuy.exception.BusinessException e) {
      assertThat(e.getStatus()).isEqualTo(409);
      return false;
    }
  }

  boolean tryAction(Actor a, long id, String action) {
    try {
      orderService.action(a, id, action);
      return true;
    } catch (hy.CommunityBuy.exception.BusinessException e) {
      assertThat(e.getStatus()).isIn(403, 409);
      return false;
    }
  }
}
