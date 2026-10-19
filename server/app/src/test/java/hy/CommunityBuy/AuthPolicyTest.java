package hy.CommunityBuy;

import hy.CommunityBuy.dto.Dtos.*;
import hy.CommunityBuy.service.*;
import hy.CommunityBuy.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=","app.orders.scheduling-enabled=false"})
class AuthPolicyTest {
    static final TestDatabase.Connection DB=TestDatabase.connect(false);
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",DB::url);r.add("spring.datasource.username",DB::user);r.add("spring.datasource.password",DB::password);
    }
    @Autowired UserService users;
    @Autowired UserRepository repository;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Test void legacyPlaintextIsDisabledByDefaultAndNeverRehashedOnRejectedLogin() {
        String id="legacy_"+java.util.UUID.randomUUID().toString().replace("-","").substring(0,16);
        users.create(new CreateUser(id,"Legacy-test-password"));
        jdbc.update("update users set password=?,password_scheme='LEGACY_PLAIN' where id=?","Legacy-test-password",id);
        try {
            assertThatThrownBy(()->auth.login(new Login(id,"Legacy-test-password"))).hasMessage("Invalid credentials");
            assertThat(repository.findById(id).orElseThrow().getPassword()).isEqualTo("Legacy-test-password");
            assertThat(repository.findById(id).orElseThrow().getPasswordScheme()).isEqualTo("LEGACY_PLAIN");
        } finally { repository.deleteById(id); }
    }
}
