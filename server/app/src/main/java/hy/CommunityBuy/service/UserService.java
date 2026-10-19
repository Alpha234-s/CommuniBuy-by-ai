package hy.CommunityBuy.service;

import hy.CommunityBuy.entity.User;
import hy.CommunityBuy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public User getUser(String userId) {
        return userRepository.findById(userId).orElse(null);
    }

    public User createUser(String userId, String password) {
        User user = User.builder()
                .id(userId)
                .password(password)
                .role(0) // 默认普通用户
                .build();
        return userRepository.save(user);
    }

    public User updateUser(String userId, User updateInfo) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        if (updateInfo.getNickname() != null) user.setNickname(updateInfo.getNickname());
        if (updateInfo.getDescription() != null) user.setDescription(updateInfo.getDescription());
        if (updateInfo.getAddress() != null) user.setAddress(updateInfo.getAddress());
        if (updateInfo.getRole() != null) user.setRole(updateInfo.getRole());
        return userRepository.save(user);
    }

    public void deleteUser(String userId) {
        userRepository.deleteById(userId);
    }

    public List<String> getAllUserIds() {
        return userRepository.findAll().stream().map(User::getId).toList();
    }
}