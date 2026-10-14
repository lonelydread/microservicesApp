package ru.microservicesApp.orderService.dto.user;

import java.util.Set;
import ru.microservicesApp.orderService.entity.Role;

public record UserResponse(java.util.UUID id,
                           String username,
                           String email,
                           Set<Role> roles) {
}
