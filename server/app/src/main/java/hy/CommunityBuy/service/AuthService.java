package hy.CommunityBuy.service;
import hy.CommunityBuy.dto.Dtos.*;
import hy.CommunityBuy.entity.*;
import hy.CommunityBuy.exception.BusinessException;
import hy.CommunityBuy.repository.*;
import hy.CommunityBuy.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final BlacklistRepository blacklist;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final Clock clock;
    @Value("${app.jwt.issuer}") private String issuer;
    @Value("${app.jwt.audience}") private String audience;
    @Value("${app.jwt.ttl-seconds}") private long ttl;
    @Value("${app.auth.legacy-password-login}") private boolean legacy;
    // A constant-work BCrypt comparison for absent users limits account enumeration by timing.
    private String dummyHash;
    @jakarta.annotation.PostConstruct void init() {
        if (ttl < 60 || ttl > 86400) throw new IllegalArgumentException("JWT TTL must be 60..86400 seconds");
        dummyHash = passwords.encode(UUID.randomUUID().toString());
    }
    public static void checkPassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw BusinessException.bad("Password exceeds 72 UTF-8 bytes");
    }
    @Transactional
    public Token login(Login request) {
        checkPassword(request.password());
        User u = users.lock(request.user()).orElse(null);
        boolean matches;
        if (u != null && "LEGACY_PLAIN".equals(u.getPasswordScheme()) && legacy) {
            matches = MessageDigest.isEqual(u.getPassword().getBytes(StandardCharsets.UTF_8),request.password().getBytes(StandardCharsets.UTF_8));
            passwords.matches(request.password(),dummyHash);
        } else {
            matches = passwords.matches(request.password(), u != null && "BCRYPT".equals(u.getPasswordScheme()) ? u.getPassword() : dummyHash)
                && u != null && "BCRYPT".equals(u.getPasswordScheme());
        }
        if (!matches || u == null || u.getRole() < 0) throw new BusinessException(401,"Invalid credentials");
        if ("LEGACY_PLAIN".equals(u.getPasswordScheme())) {
            u.setPassword(passwords.encode(request.password())); u.setPasswordScheme("BCRYPT");
            u.setAuthVersion(u.getAuthVersion()+1);
        }
        Instant now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(u.getId()).audience(List.of(audience))
            .issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(ttl)).id(UUID.randomUUID().toString())
            .claim("ver",u.getAuthVersion()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Token(token,"Bearer",now.plusSeconds(ttl).getEpochSecond());
    }
    @Transactional
    public void logout(Jwt jwt) {
        // Serializes concurrent logouts of this user's sessions; unique jti is the DB backstop.
        users.lock(jwt.getSubject()).orElseThrow(BusinessException::missing);
        if (!blacklist.existsById(jwt.getId())) {
            var b = new Blacklist(); b.setTokenHash(jwt.getId()); b.setExpireAt(jwt.getExpiresAt().getEpochSecond());
            blacklist.saveAndFlush(b);
        }
    }
    @Transactional
    public void reset(Actor actor, Reset request) {
        checkPassword(request.newPassword()); checkPassword(request.oldPassword());
        var u = users.lock(actor.id()).orElseThrow(BusinessException::missing);
        if (!"BCRYPT".equals(u.getPasswordScheme()) || !passwords.matches(request.oldPassword(),u.getPassword()))
            throw new BusinessException(401,"Invalid credentials");
        u.setPassword(passwords.encode(request.newPassword()));
        u.setAuthVersion(u.getAuthVersion()+1);
    }
}
