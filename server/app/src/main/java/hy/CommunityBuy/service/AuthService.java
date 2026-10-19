package hy.CommunityBuy.service;

import hy.CommunityBuy.entity.User;
import hy.CommunityBuy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    // 模拟登录：实际应用中应校验加密后的密码
    public String login(String userId, String password) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent() && userOpt.get().getPassword().equals(password)) {
            // 实际应用中这里应生成 JWT
            return "mock-jwt-token-for-" + userId;
        }
        return null;
    }

    public String getCurrentUserId(String token) {
        if (token != null && token.startsWith("mock-jwt-token-for-")) {
            return token.replace("mock-jwt-token-for-", "");
        }
        return null;
    }
}