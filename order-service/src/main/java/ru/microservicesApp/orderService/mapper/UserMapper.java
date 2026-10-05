package ru.microservicesApp.orderService.mapper;

import org.springframework.stereotype.Component;
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
        user.setRole(request.role() == null ? Role.ROLE_USER : request.role());
        return user;
    }

    public void updateEntity(User user, User updatedUser, String encodedPassword) {
        user.setUsername(updatedUser.getUsername());
        user.setEmail(updatedUser.getEmail());
        user.setPassword(encodedPassword);
        user.setRole(updatedUser.getRole());
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }
}
