package ru.microservicesApp.orderService.dto.user;

import ru.microservicesApp.orderService.entity.Role;

public record UserResponse(java.util.UUID id,
                           String username,
                           String email,
                           Role role) {
}
