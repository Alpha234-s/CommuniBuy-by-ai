package hy.CommunityBuy;
import com.fasterxml.jackson.databind.*;
import hy.CommunityBuy.dto.Dtos.*;
import hy.CommunityBuy.entity.*;
import hy.CommunityBuy.repository.*;
import hy.CommunityBuy.security.Actor;
import hy.CommunityBuy.service.*;
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
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={
    "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "app.jwt.ttl-seconds=3600", "app.orders.scheduling-enabled=false", "app.auth.legacy-password-login=true"})
@AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackendIntegrationTest {
    static final TestDatabase.Connection DB=TestDatabase.connect(false);
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",DB::url); r.add("spring.datasource.username",DB::user); r.add("spring.datasource.password",DB::password);
    }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        final AtomicReference<Instant> now=new AtomicReference<>(Instant.parse("2026-10-19T00:00:00Z"));
        public ZoneId getZone(){ return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone){ return this; }
        public Instant instant(){ return now.get(); }
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
    final Actor buyer=new Actor("buyer",false), seller=new Actor("seller",false), outsider=new Actor("outsider",false), administrator=new Actor("admin",true);
    String hash;
    @BeforeAll void hash() { hash=passwords.encode("Test-password-42"); }
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM orders"); jdbc.update("DELETE FROM items"); jdbc.update("DELETE FROM blacklist"); jdbc.update("DELETE FROM users");
        clock.now.set(Instant.parse("2026-10-19T00:00:00Z"));
        for (String id:List.of("buyer","seller","outsider","admin")) {
            var u=new User(); u.setId(id); u.setPassword(hash); u.setRole(id.equals("admin") ? 1:0); u.setAddress("Private "+id); users.saveAndFlush(u);
        }
    }
    String token(String id) { return token(id,clock.instant(),clock.instant().plusSeconds(3600),"communibuy","communibuy-api"); }
    String token(String id,Instant issued,Instant expiry,String issuer,String audience) {
        var u=users.findById(id).orElseThrow();
        var c=JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject(id).issuedAt(issued).notBefore(issued)
            .expiresAt(expiry).id(UUID.randomUUID().toString()).claim("ver",u.getAuthVersion()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),c)).getTokenValue();
    }
    ResultActions request(String user,String method,String path,Object body) throws Exception {
        var b=method.equals("GET") ? get(path) : post(path);
        if (user!=null) b.header("Authorization","Bearer "+token(user));
        if (body!=null) b.contentType("application/json").content(json.writeValueAsBytes(body));
        return mvc.perform(b);
    }
    ItemView item(int stock) { return itemService.create(seller,new NewItem("Apples","fresh red fruit",199,stock,0,0L,null)); }
    OrderView order(long item,String key) { return orderService.create(buyer,new NewOrder(item,1,"Buyer address"),key); }
    JsonNode data(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()).get("data"); }
    @Test void missingForgedAndLegacyTokensAreRejected() throws Exception {
        mvc.perform(get("/api/v1/item")).andExpect(status().isUnauthorized());
        for(String value:List.of("mock-jwt-token-for-buyer","abc.def.ghi",token("buyer").replaceFirst("^ey","ex")))
            mvc.perform(get("/api/v1/auth").header("Authorization","Bearer "+value)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth").header("token",token("buyer"))).andExpect(status().isUnauthorized());
    }
    @Test void expiredFutureIssuerAndAudienceTokensAreRejected() throws Exception {
        Instant now=clock.instant();
        for(String t:List.of(token("buyer",now.minusSeconds(100),now.minusSeconds(1),"communibuy","communibuy-api"),
            token("buyer",now.plusSeconds(1),now.plusSeconds(100),"communibuy","communibuy-api"),
            token("buyer",now,now.plusSeconds(100),"wrong","communibuy-api"),token("buyer",now,now.plusSeconds(100),"communibuy","wrong")))
            mvc.perform(get("/api/v1/auth").header("Authorization","Bearer "+t)).andExpect(status().isUnauthorized());
    }
    @Test void logoutRevokesTokenAcrossRequests() throws Exception {
        String t=token("buyer");
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+t)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth").header("Authorization","Bearer "+t)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from blacklist",Long.class)).isEqualTo(1);
    }
    @Test void resetRevokesAllOldSessionsAndUsesBcrypt() throws Exception {
        String t=token("buyer"), other=token("buyer");
        mvc.perform(post("/api/v1/auth/reset").header("Authorization","Bearer "+t).contentType("application/json")
            .content(json.writeValueAsBytes(new Reset("Test-password-42","New-password-43")))).andExpect(status().isOk());
        for(String old:List.of(t,other)) mvc.perform(get("/api/v1/auth").header("Authorization","Bearer "+old)).andExpect(status().isUnauthorized());
        request(null,"POST","/api/v1/auth",new Login("buyer","Test-password-42")).andExpect(status().isUnauthorized());
        assertThat(data(request(null,"POST","/api/v1/auth",new Login("buyer","New-password-43")).andExpect(status().isOk())).get("token").asText()).isNotBlank();
        assertThat(passwords.matches("New-password-43",users.findById("buyer").orElseThrow().getPassword())).isTrue();
    }
    @Test void bannedUsersCannotUseExistingTokenOrLogin() throws Exception {
        String t=token("buyer"); userService.role(administrator,"buyer",-1);
        mvc.perform(get("/api/v1/item").header("Authorization","Bearer "+t)).andExpect(status().isUnauthorized());
        request(null,"POST","/api/v1/auth",new Login("buyer","Test-password-42")).andExpect(status().isUnauthorized());
    }
    @Test void privateProfilesAndAdminCapabilitiesAreProtected() throws Exception {
        request("buyer","GET","/api/v1/user/seller",null).andExpect(status().isForbidden());
        request("buyer","POST","/api/v1/user/seller",Map.of("address","stolen")).andExpect(status().isForbidden());
        for(String path:List.of("/api/v1/user","/api/v1/stats")) request("buyer","GET",path,null).andExpect(status().isForbidden());
        request("buyer","POST","/api/v1/user/buyer/role",Map.of("role",1)).andExpect(status().isForbidden());
        request("buyer","POST","/api/v1/user/buyer",Map.of("role",1)).andExpect(status().isBadRequest());
        request("buyer","POST","/api/v1/user",new CreateUser("injected","Test-password-42")).andExpect(status().isForbidden());
    }
    @Test void userResponsesContainNeitherPasswordNorHash() throws Exception {
        for(String path:List.of("/api/v1/auth","/api/v1/user/buyer")) {
            String body=request("buyer","GET",path,null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("password","$2","authVersion","passwordScheme");
        }
        var response=request("buyer","POST","/api/v1/user/buyer",Map.of("nickname","New name")).andExpect(status().isOk());
        assertThat(response.andReturn().getResponse().getContentAsString()).doesNotContain("password","$2");
    }
    @Test void registrationDoesNotOverwriteExistingIdAndIsCaseSensitive() throws Exception {
        request(null,"POST","/api/v1/auth/register",new CreateUser("buyer","Changed-secret")).andExpect(status().isConflict());
        assertThat(users.findById("buyer").orElseThrow().getPassword()).isEqualTo(hash);
        request(null,"POST","/api/v1/auth/register",new CreateUser("Buyer","Changed-secret")).andExpect(status().isOk());
        assertThat(users.count()).isEqualTo(5);
        request("admin","POST","/api/v1/user",new CreateUser("another","Changed-secret")).andExpect(status().isOk());
        request(null,"POST","/api/v1/auth/register",Map.of("user","bad","password","Changed-secret","role",1)).andExpect(status().isBadRequest());
    }
    @Test void legacyPasswordsUpgradeExactlyOnce() {
        jdbc.update("update users set password=?, password_scheme='LEGACY_PLAIN' where id='buyer'","Legacy-secret");
        auth.login(new Login("buyer","Legacy-secret")); var migrated=users.findById("buyer").orElseThrow();
        assertThat(migrated.getPasswordScheme()).isEqualTo("BCRYPT"); assertThat(passwords.matches("Legacy-secret",migrated.getPassword())).isTrue();
        auth.login(new Login("buyer","Legacy-secret")); assertThat(users.findById("buyer").orElseThrow().getPassword()).isEqualTo(migrated.getPassword());
    }
    @Test void maliciousOrderFieldsAndClientTimestampsAreRejected() throws Exception {
        long item=item(2).id();
        for(String field:List.of("userId","ownerId","price","status","payTime","createTime")) {
            var body=new HashMap<String,Object>(Map.of("itemId",item,"quantity",1,"address","valid")); body.put(field,1);
            mvc.perform(post("/api/v1/order").header("Authorization","Bearer "+token("buyer")).header("Idempotency-Key","bad")
                .contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest());
        }
        assertThat(items.findById(item).orElseThrow().getStock()).isEqualTo(2); assertThat(orders.count()).isZero();
        long id=order(item,"real").id();
        request("buyer","POST","/api/v1/order/"+id,Map.of("status",3,"receiptTime",1)).andExpect(status().isBadRequest());
    }
    @Test void invalidInputsAndPaginationAreRejected() throws Exception {
        long item=item(3).id();
        for(Object qty:Arrays.asList(null,0,-1,1.5)) {
            var body=new HashMap<String,Object>(); body.put("itemId",item); body.put("quantity",qty); body.put("address","valid");
            mvc.perform(post("/api/v1/order").header("Authorization","Bearer "+token("buyer")).header("Idempotency-Key","bad")
                .contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest());
        }
        for(String path:List.of("/api/v1/item?size=0","/api/v1/item?size=101","/api/v1/order?page=-1","/api/v1/order/0"))
            request("buyer","GET",path,null).andExpect(status().isBadRequest());
        request("seller","POST","/api/v1/item",Map.of("name","bad","price",-1,"stock",1)).andExpect(status().isBadRequest());
        request("seller","POST","/api/v1/item",Map.of("name","bad","price",1,"stock",1,"startTime",10,"endTime",10)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/order").header("Authorization","Bearer "+token("buyer")).header("Idempotency-Key","blank-address")
            .contentType("application/json").content(json.writeValueAsBytes(new NewOrder(item,1," ")))).andExpect(status().isBadRequest());
    }
    @Test void itemOwnershipAndStockVersionAreEnforced() throws Exception {
        var i=item(2);
        request("buyer","POST","/api/v1/item/"+i.id(),Map.of("price",1)).andExpect(status().isForbidden());
        order(i.id(),"reserve");
        request("seller","POST","/api/v1/item/"+i.id(),Map.of("stock",99,"version",i.version())).andExpect(status().isConflict());
        request("seller","POST","/api/v1/item/"+i.id(),Map.of("stock",99)).andExpect(status().isConflict());
        var latest=itemService.get(i.id());
        request("seller","POST","/api/v1/item/"+i.id(),Map.of("stock",5,"version",latest.version())).andExpect(status().isOk());
        assertThat(items.findById(i.id()).orElseThrow().getStock()).isEqualTo(5);
    }
    @Test void priceAndIdentityAreDatabaseSnapshotsWithLongTotal() {
        var i=itemService.create(seller,new NewItem("Large","",Integer.MAX_VALUE,3,0,0L,null));
        var o=orderService.create(buyer,new NewOrder(i.id(),2,"address"),"snapshot");
        assertThat(o.user()).isEqualTo("buyer"); assertThat(o.owner()).isEqualTo("seller"); assertThat(o.total()).isEqualTo(4294967294L);
        itemService.update(seller,i.id(),new EditItem(null,null,1,null,null,null,null,null));
        assertThat(orderService.get(buyer,o.id()).price()).isEqualTo(Integer.MAX_VALUE);
    }
    @Test void activityStartInclusiveEndExclusiveAndOfflineChecked() {
        long now=clock.instant().getEpochSecond();
        var future=itemService.create(seller,new NewItem("Future",null,1,2,0,now+1,now+10));
        assertThatThrownBy(()->order(future.id(),"future")).hasMessageContaining("available");
        clock.now.set(Instant.ofEpochSecond(now+1)); order(future.id(),"start");
        clock.now.set(Instant.ofEpochSecond(now+10)); assertThatThrownBy(()->order(future.id(),"end")).hasMessageContaining("available");
        var offline=itemService.create(seller,new NewItem("Offline",null,1,2,-1,0L,null));
        assertThatThrownBy(()->order(offline.id(),"offline")).hasMessageContaining("available");
    }
    <T> List<T> race(List<Supplier<T>> operations) throws Exception {
        var pool=Executors.newFixedThreadPool(operations.size()); var ready=new CountDownLatch(operations.size()); var go=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<T>>();
            for(var op:operations) futures.add(pool.submit(()->{ready.countDown(); if(!go.await(10,TimeUnit.SECONDS)) throw new AssertionError("race barrier"); return op.get();}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); go.countDown();
            var result=new ArrayList<T>(); for(var f:futures) result.add(f.get(30,TimeUnit.SECONDS)); return result;
        } finally { pool.shutdownNow(); }
    }
    @Test void lastUnitCannotBeOversoldAcrossBuyers() throws Exception {
        long item=item(1).id();
        var outcomes=race(List.of(()->tryOrder(buyer,item,"a"),()->tryOrder(outsider,item,"b")));
        assertThat(outcomes).containsExactlyInAnyOrder(true,false);
        assertThat(orders.count()).isEqualTo(1); assertThat(items.findById(item).orElseThrow().getStock()).isZero();
    }
    boolean tryOrder(Actor a,long item,String key) { try {orderService.create(a,new NewOrder(item,1,"address"),key); return true;} catch(hy.CommunityBuy.exception.BusinessException e){assertThat(e.getStatus()).isEqualTo(409);return false;} }
    @Test void sequentialAndConcurrentRetriesCreateOneOrderAndRejectConflicts() throws Exception {
        long item=item(5).id(); var r=new NewOrder(item,1,"Buyer address");
        var outcomes=race(List.of(()->orderService.create(buyer,r,"retry").id(),()->orderService.create(buyer,r,"retry").id(),()->orderService.create(buyer,r,"retry").id()));
        assertThat(new HashSet<>(outcomes)).hasSize(1); assertThat(order(item,"retry").id()).isEqualTo(outcomes.getFirst());
        assertThat(orders.count()).isEqualTo(1); assertThat(items.findById(item).orElseThrow().getStock()).isEqualTo(4);
        assertThatThrownBy(()->orderService.create(buyer,new NewOrder(item,2,"Buyer address"),"retry")).hasMessageContaining("differ");
    }
    @Test void sameKeyAcrossDifferentItemsStillCreatesOnlyOneOrder() throws Exception {
        long first=item(2).id(),second=item(2).id();
        var outcomes=race(List.of(()->tryOrder(buyer,first,"same"),()->tryOrder(buyer,second,"same")));
        assertThat(outcomes).containsExactlyInAnyOrder(true,false); assertThat(orders.count()).isEqualTo(1);
        assertThat(items.findAll().stream().mapToInt(Item::getStock).sum()).isEqualTo(3);
    }
    @Test void failedOrderInsertRollsBackInventory() {
        long item=item(1).id();
        // A real MySQL CHECK failure after in-memory inventory reservation, not a mocked repository.
        jdbc.execute("ALTER TABLE orders ADD CONSTRAINT ck_test_failure CHECK (quantity <> 1)");
        try { assertThatThrownBy(()->order(item,"fail")).isInstanceOf(org.springframework.dao.DataAccessException.class).hasMessageContaining("ck_test_failure"); }
        finally { jdbc.execute("ALTER TABLE orders DROP CHECK ck_test_failure"); }
        assertThat(orders.count()).isZero(); assertThat(items.findById(item).orElseThrow().getStock()).isEqualTo(1);
    }
    @Test void fullPaymentShippingReceiptFlowAndIllegalTransitions() {
        long item=item(2).id(); var o=order(item,"flow");
        assertThatThrownBy(()->orderService.action(seller,o.id(),"ship")).hasMessageContaining("state");
        assertThatThrownBy(()->orderService.action(seller,o.id(),"pay")).hasMessageContaining("denied");
        assertThat(orderService.get(seller,o.id()).address()).isNull();
        var paid=orderService.action(buyer,o.id(),"pay"); assertThat(paid.status()).isEqualTo(1); assertThat(paid.payTime()).isEqualTo(clock.instant().getEpochSecond());
        orderService.address(buyer,o.id(),new Address("New address")); assertThat(orderService.get(seller,o.id()).address()).isEqualTo("New address");
        orderService.action(seller,o.id(),"ship"); orderService.action(seller,o.id(),"ship");
        assertThatThrownBy(()->orderService.address(buyer,o.id(),new Address("too late"))).hasMessageContaining("state");
        var completed=orderService.action(buyer,o.id(),"receive"); assertThat(completed.status()).isEqualTo(3); assertThat(completed.closeTime()).isNotNull();
        assertThat(items.findById(item).orElseThrow().getShipmentCount()).isEqualTo(1);
        assertThatThrownBy(()->orderService.action(buyer,o.id(),"cancel")).hasMessageContaining("cannot");
    }
    @Test void unrelatedUsersCannotReadChangeOrFilterAroundOrderScope() throws Exception {
        var o=order(item(2).id(),"privacy");
        request("outsider","GET","/api/v1/order/"+o.id(),null).andExpect(status().isForbidden());
        request("outsider","POST","/api/v1/order/"+o.id()+"/cancel",null).andExpect(status().isForbidden());
        assertThat(data(request("outsider","GET","/api/v1/order?user=buyer&owner=seller&status=0",null)).get("count").asLong()).isZero();
        request("seller","POST","/api/v1/order/"+o.id()+"/address",new Address("bad")).andExpect(status().isForbidden());
    }
    @Test void concurrentCancellationRestoresStockExactlyOnce() throws Exception {
        long item=item(1).id(), id=order(item,"cancel").id();
        race(List.of(()->orderService.action(buyer,id,"cancel"),()->orderService.action(buyer,id,"cancel")));
        orderService.action(buyer,id,"cancel"); assertThat(items.findById(item).orElseThrow().getStock()).isEqualTo(1);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(-1);
    }
    @Test void paymentAndBuyerCancellationHaveOneWinner() throws Exception {
        long item=item(1).id(), id=order(item,"compete").id();
        Supplier<Boolean> pay=()->tryAction(buyer,id,"pay"),cancel=()->tryAction(buyer,id,"cancel");
        assertThat(race(List.of(pay,cancel))).containsExactlyInAnyOrder(true,false);
        var o=orders.findById(id).orElseThrow(); assertThat(o.getStatus()).isIn(-1,1);
        assertThat(items.findById(item).orElseThrow().getStock()).isEqualTo(o.getStatus()==-1 ? 1:0);
    }
    boolean tryAction(Actor a,long id,String action) { try {orderService.action(a,id,action);return true;} catch(hy.CommunityBuy.exception.BusinessException e){assertThat(e.getStatus()).isIn(403,409);return false;} }
    @Test void sellerCancellationOfPaidOrderRecordsRefundAndExcludesRevenue() {
        long item=item(1).id(), id=order(item,"refund").id(); orderService.action(buyer,id,"pay");
        assertThat(admin.stats(administrator).totalRevenue()).isEqualTo(199);
        var cancelled=orderService.action(seller,id,"cancel"); assertThat(cancelled.refundStatus()).isEqualTo("SIMULATED_REFUNDED");
        orderService.action(seller,id,"cancel"); assertThat(admin.stats(administrator).totalRevenue()).isZero();
        assertThat(items.findById(item).orElseThrow().getStock()).isEqualTo(1);
    }
    @Test void timeoutSweepCompensatesPersistedDeadlineAndIsRepeatable() throws Exception {
        long item=item(1).id(), id=order(item,"timeout").id(); long expires=orders.findById(id).orElseThrow().getExpiresAt();
        clock.now.set(Instant.ofEpochSecond(expires)); assertThatThrownBy(()->orderService.action(buyer,id,"pay")).hasMessageContaining("deadline");
        var restarted=new OrderExpiry(orders,orderService,clock); restarted.sweep(); restarted.sweep();
        race(List.of(()->{orderService.expire(id);return true;},()->{orderService.expire(id);return true;}));
        assertThat(items.findById(item).orElseThrow().getStock()).isEqualTo(1); assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(-1);
    }
    @Test void compoundFiltersPaginationAndStatisticsUseRealData() throws Exception {
        long first=item(5).id(),second=item(5).id();
        itemService.create(outsider,new NewItem("Apples",null,1,1,0,0L,null));
        long id=order(first,"one").id(); order(first,"two"); order(second,"three"); orderService.action(buyer,id,"pay");
        var filtered=data(request("buyer","GET","/api/v1/order?user=buyer&owner=seller&item="+first+"&status=0&size=1",null));
        assertThat(filtered.get("count").asLong()).isEqualTo(1); assertThat(filtered.get("content").size()).isEqualTo(1);
        var list=data(request("buyer","GET","/api/v1/item?keyword=Apples&owner=seller&status=0&size=1",null));
        assertThat(list.get("count").asLong()).isEqualTo(2); assertThat(list.get("content").size()).isEqualTo(1);
        var page2=data(request("buyer","GET","/api/v1/item?keyword=Apples&owner=seller&status=0&size=1&page=1",null));
        assertThat(page2.get("content").get(0).get("id").asLong()).isLessThan(list.get("content").get(0).get("id").asLong());
        var stats=admin.stats(administrator); assertThat(stats.userCount()).isEqualTo(4); assertThat(stats.orderCount()).isEqualTo(3);
        assertThat(stats.totalRevenue()).isEqualTo(199); assertThat(stats.ongoingItemCount()).isEqualTo(3);
        clock.now.set(clock.instant().plusSeconds(100));
        itemService.create(seller,new NewItem("Future",null,1,1,0,clock.instant().getEpochSecond()+10,null));
        itemService.create(seller,new NewItem("Ended",null,1,1,0,0L,1L));
        assertThat(admin.stats(administrator).ongoingItemCount()).isEqualTo(3);
    }
    @Test void emptyUpdatesNeverDeleteAndNoDeleteEndpointExists() throws Exception {
        long item=item(2).id(), id=order(item,"keep").id();
        for(String path:List.of("/api/v1/user/buyer","/api/v1/item/"+item,"/api/v1/order/"+id))
            request("buyer","POST",path,Map.of()).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/order/"+id).header("Authorization","Bearer "+token("admin"))).andExpect(status().isMethodNotAllowed());
        assertThat(orders.count()).isEqualTo(1); assertThat(items.count()).isEqualTo(1); assertThat(users.count()).isEqualTo(4);
    }
    @Test void missingRequiredJwtClaimsAndWrongSignatureAreRejected() throws Exception {
        var now=clock.instant();
        var valid=new LinkedHashMap<String,Object>();
        valid.put("iss","communibuy"); valid.put("aud",List.of("communibuy-api")); valid.put("sub","buyer");
        valid.put("iat",now); valid.put("nbf",now); valid.put("exp",now.plusSeconds(100)); valid.put("jti",UUID.randomUUID().toString()); valid.put("ver",0L);
        for(String claim:valid.keySet()) {
            var fields=new LinkedHashMap<>(valid); fields.remove(claim);
            var claims=JwtClaimsSet.builder().claims(c->c.putAll(fields)).build();
            String t=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
            mvc.perform(get("/api/v1/auth").header("Authorization","Bearer "+t)).andExpect(status().isUnauthorized());
        }
        var wrong=new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(new javax.crypto.spec.SecretKeySpec(new byte[32],"HmacSHA256")));
        var claims=JwtClaimsSet.builder().claims(c->c.putAll(valid)).build();
        String forged=wrong.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        mvc.perform(get("/api/v1/auth").header("Authorization","Bearer "+forged)).andExpect(status().isUnauthorized());
    }
    @Test void concurrentAccountCreationNeverOverwrites() throws Exception {
        Supplier<Boolean> create=()->{try {userService.create(new CreateUser("new_user","Secret-password"));return true;}
            catch(org.springframework.dao.DataIntegrityViolationException | hy.CommunityBuy.exception.BusinessException e){return false;}};
        assertThat(race(List.of(create,create))).containsExactlyInAnyOrder(true,false);
        assertThat(users.count()).isEqualTo(5); assertThat(passwords.matches("Secret-password",users.findById("new_user").orElseThrow().getPassword())).isTrue();
    }
    @Test void stockCapacityAlwaysLeavesRoomForCancellation() {
        var i=item(Integer.MAX_VALUE); var o=order(i.id(),"capacity");
        var current=itemService.get(i.id());
        assertThatThrownBy(()->itemService.update(seller,i.id(),new EditItem(null,null,null,Integer.MAX_VALUE,null,null,null,current.version())))
            .hasMessageContaining("capacity");
        orderService.action(buyer,o.id(),"cancel"); assertThat(items.findById(i.id()).orElseThrow().getStock()).isEqualTo(Integer.MAX_VALUE);
    }
    @Test void paymentAndExpiryCannotDoubleReleaseOrKeepAnExpiredPayment() throws Exception {
        long item=item(1).id(),id=order(item,"expiry-race").id();
        clock.now.set(Instant.ofEpochSecond(orders.findById(id).orElseThrow().getExpiresAt()));
        race(List.of(()->tryAction(buyer,id,"pay"),()->{orderService.expire(id);return true;}));
        var o=orders.findById(id).orElseThrow(); assertThat(o.getStatus()).isEqualTo(-1); assertThat(o.getPayTime()).isNull();
        assertThat(items.findById(item).orElseThrow().getStock()).isEqualTo(1);
    }
    @Test void itemUpdateAndOrderCompetitionPreserveStockAndPrice() throws Exception {
        var i=item(2);
        Supplier<Boolean> edit=()->{try {itemService.update(seller,i.id(),new EditItem(null,null,299,4,null,null,null,i.version()));return true;}
            catch(hy.CommunityBuy.exception.BusinessException e){assertThat(e.getStatus()).isEqualTo(409);return false;}};
        race(List.of(()->tryOrder(buyer,i.id(),"edit-race"),edit));
        var o=orders.findAll().getFirst(); var after=items.findById(i.id()).orElseThrow();
        assertThat(after.getStock()).isEqualTo(o.getPrice()==299 ? 3:1);
        assertThat(after.getPrice()).isEqualTo(o.getPrice());
    }
    @Test void missingAddressNullItemAndMalformedIdentifiersAreRejected() throws Exception {
        var i=item(1);
        for(var body:List.of(Map.of("itemId",i.id(),"quantity",1),Map.of("quantity",1,"address","x"),Map.of("itemId",0,"quantity",1,"address","x")))
            mvc.perform(post("/api/v1/order").header("Authorization","Bearer "+token("buyer")).header("Idempotency-Key","validation")
                .contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest());
        request(null,"POST","/api/v1/auth/register",new CreateUser("9invalid","Secret-password")).andExpect(status().isBadRequest());
        request("buyer","GET","/api/v1/order?item=-1",null).andExpect(status().isBadRequest());
        request("buyer","GET","/api/v1/order?status=4",null).andExpect(status().isBadRequest());
    }
    @Test void responseTraceAndErrorCodeAreConsistentWithoutReflectingUntrustedHeader() throws Exception {
        var result=mvc.perform(get("/api/v1/item").header("X-Trace-Id","attacker-controlled"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED")).andReturn();
        String trace=result.getResponse().getHeader("X-Trace-Id");
        assertThat(trace).matches("[a-f0-9]{32}");
        assertThat(json.readTree(result.getResponse().getContentAsString()).get("traceId").asText()).isEqualTo(trace);
        request("buyer","GET","/api/v1/item/-1",null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(org.slf4j.MDC.get("traceId")).isNull();
    }
    @Test void openApiIsGeneratedAndRestrictedToAdministrators() throws Exception {
        request("buyer","GET","/v3/api-docs",null).andExpect(status().isForbidden());
        request("admin","GET","/v3/api-docs",null).andExpect(status().isOk())
            .andExpect(jsonPath("$.openapi").exists()).andExpect(jsonPath("$.paths['/api/v1/order']").exists());
    }

    @Test void accessLogsContainRouteTemplatesAndNeverRequestSecrets() throws Exception {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("http.access");
        var capture=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start(); logger.addAppender(capture);
        try {
            request(null,"POST","/api/v1/auth",new Login("buyer","Test-password-42")).andExpect(status().isOk());
            request("buyer","GET","/api/v1/user/buyer?secret=private-query",null).andExpect(status().isOk());
            String logs=capture.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.joining("\n"));
            assertThat(logs).contains("/api/v1/user/{id}","status=200").doesNotContain("Test-password-42","private-query","Bearer","eyJ","Private buyer");
        } finally { logger.detachAppender(capture); capture.stop(); }
    }
    @Test void concurrentAdministratorDemotionRetainsAnAuthorizedAdministrator() throws Exception {
        userService.createAdmin(new CreateUser("admin_two","Admin-test-password"));
        var other=new Actor("admin_two",true);
        Supplier<Boolean> first=()->tryRole(administrator,"admin_two"),second=()->tryRole(other,"admin");
        assertThat(race(List.of(first,second))).containsExactlyInAnyOrder(true,false);
        assertThat(users.findAll().stream().filter(u->u.getRole()==1).count()).isEqualTo(1);
    }
    boolean tryRole(Actor actor,String id) {
        try {userService.role(actor,id,0);return true;}
        catch(hy.CommunityBuy.exception.BusinessException e){assertThat(e.getStatus()).isEqualTo(403);return false;}
    }
    @Test void anOrdinaryAccountMayBuyAndSellWithoutRolePromotion() {
        var own=itemService.create(buyer,new NewItem("Buyer also sells",null,50,1,0,0L,null));
        var bought=orderService.create(seller,new NewOrder(own.id(),1,"address"),"dual-identity");
        assertThat(bought.user()).isEqualTo("seller");assertThat(bought.owner()).isEqualTo("buyer");
        assertThat(users.findById("buyer").orElseThrow().getRole()).isZero();
        assertThat(users.findById("seller").orElseThrow().getRole()).isZero();
    }
    @Test void unicodePasswordByteLimitAndWrongOldPasswordNeverChangeAccount() throws Exception {
        request(null,"POST","/api/v1/auth/register",new CreateUser("unicode","密".repeat(30))).andExpect(status().isBadRequest());
        String before=users.findById("buyer").orElseThrow().getPassword();String old=token("buyer");
        request("buyer","POST","/api/v1/auth/reset",new Reset("incorrect","New-test-password")).andExpect(status().isUnauthorized());
        assertThat(users.findById("buyer").orElseThrow().getPassword()).isEqualTo(before);
        mvc.perform(get("/api/v1/auth").header("Authorization","Bearer "+old)).andExpect(status().isOk());
    }

    @Test void itemSnapshotsSurviveCatalogChangesAndIdempotentRetryAfterClosure() {
        var i=item(3);var original=order(i.id(),"historical");
        itemService.update(seller,i.id(),new EditItem("Renamed","changed",999,null,-1,null,null,null));
        var stored=orderService.get(buyer,original.id());
        assertThat(stored.itemName()).isEqualTo("Apples");assertThat(stored.itemDescription()).isEqualTo("fresh red fruit");
        assertThat(stored.snapshotSource()).isEqualTo("AT_CREATION");assertThat(stored.price()).isEqualTo(199);
        assertThat(order(i.id(),"historical").id()).isEqualTo(original.id());
        assertThatThrownBy(()->order(i.id(),"new-after-close")).hasMessageContaining("available");
    }

}
