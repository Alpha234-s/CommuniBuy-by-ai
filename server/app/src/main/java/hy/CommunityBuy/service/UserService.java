package hy.CommunityBuy.service;
import hy.CommunityBuy.dto.Dtos.*;
import hy.CommunityBuy.entity.User;
import hy.CommunityBuy.exception.BusinessException;
import hy.CommunityBuy.repository.UserRepository;
import hy.CommunityBuy.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    @Transactional
    public UserView create(CreateUser request) { return createAccount(request,0); }
    @Transactional
    public UserView createAdmin(CreateUser request) { return createAccount(request,1); }
    private UserView createAccount(CreateUser request, int role) {
        AuthService.checkPassword(request.password());
        if (users.existsById(request.user())) throw BusinessException.conflict("User already exists");
        var u = new User(); u.setId(request.user()); u.setPassword(passwords.encode(request.password())); u.setRole(role);
        // Explicit INSERT never merges an assigned id. Repository translation also maps a racing duplicate to 409.
        users.insert(u.getId(),u.getPassword(),role); return UserView.of(u);
    }
    @Transactional(readOnly=true)
    public UserView get(Actor actor, String id) {
        actor.requireSelf(id); return UserView.of(users.findById(id).orElseThrow(BusinessException::missing));
    }
    @Transactional(readOnly=true)
    public PageView<String> list(Actor actor, int page, int size) {
        actor.requireAdmin(); return PageView.of(users.findAll(PageRequest.of(page,size,Sort.by("id"))).map(User::getId));
    }
    @Transactional
    public UserView update(Actor actor, String id, Profile request) {
        actor.requireSelf(id);
        if (request.nickname()==null && request.description()==null && request.address()==null) throw BusinessException.bad("No profile changes");
        var u = users.lock(id).orElseThrow(BusinessException::missing);
        if (request.nickname()!=null) { if (request.nickname().isBlank()) throw BusinessException.bad("Blank nickname"); u.setNickname(request.nickname()); }
        if (request.description()!=null) u.setDescription(request.description());
        if (request.address()!=null) { if (request.address().isBlank()) throw BusinessException.bad("Blank address"); u.setAddress(request.address()); }
        return UserView.of(u);
    }
    @Transactional
    public UserView role(Actor actor, String id, int role) {
        actor.requireAdmin();
        // Re-check the actual actor under ordered administrator locks: concurrent mutual demotion cannot remove every admin.
        if (users.lockAdministrators().stream().noneMatch(u -> u.getId().equals(actor.id()))) throw BusinessException.forbidden();
        if (actor.id().equals(id)) throw BusinessException.forbidden();
        var u = users.lock(id).orElseThrow(BusinessException::missing);
        u.setRole(role); u.setAuthVersion(u.getAuthVersion()+1); return UserView.of(u);
    }
}
