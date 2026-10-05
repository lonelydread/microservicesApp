package ru.microservicesApp.orderService.dto.user;

import ru.microservicesApp.orderService.entity.Role;

public record UserRequest(String username,
                          String email,
                          String password,
                          Role role) {
}
