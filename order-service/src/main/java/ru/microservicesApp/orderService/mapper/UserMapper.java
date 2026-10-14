package ru.microservicesApp.orderService.mapper;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import ru.microservicesApp.orderService.dto.auth.RegisterRequest;
import ru.microservicesApp.orderService.dto.user.UserRequest;
import ru.microservicesApp.orderService.dto.user.UserResponse;
import ru.microservicesApp.orderService.entity.User;
import ru.microservicesApp.orderService.entity.Role;

@Component
public class UserMapper {
    public User toEntity(UserRequest request) {
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(request.password());
        user.setRoles(new HashSet<>(request.roles() == null ? Set.of(Role.ROLE_USER) : request.roles()));
        return user;
    }

    public User toEntity(RegisterRequest request) {
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(request.password());
        user.setRoles(new HashSet<>(Set.of(Role.ROLE_USER)));
        return user;
    }

    public void updateEntity(User user, User updatedUser, String encodedPassword) {
        user.setUsername(updatedUser.getUsername());
        user.setEmail(updatedUser.getEmail());
        user.setPassword(encodedPassword);
        user.getRoles().clear();
        user.getRoles().addAll(updatedUser.getRoles());
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), Set.copyOf(user.getRoles()));
    }
}
