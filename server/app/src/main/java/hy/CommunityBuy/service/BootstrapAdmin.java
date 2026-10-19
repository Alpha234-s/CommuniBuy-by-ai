package hy.CommunityBuy.service;

import hy.CommunityBuy.dto.Dtos.CreateUser;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("bootstrap")
@RequiredArgsConstructor
public class BootstrapAdmin implements ApplicationRunner {
  private final UserService users;
  private final Validator validator;

  @Value("${BOOTSTRAP_ADMIN_ID}")
  private String id;

  @Value("${BOOTSTRAP_ADMIN_PASSWORD}")
  private String password;

  public void run(ApplicationArguments args) {
    var request = new CreateUser(id, password);
    if (!validator.validate(request).isEmpty())
      throw new IllegalArgumentException("Invalid bootstrap account");
    users.createAdmin(request);
  }
}
