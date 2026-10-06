package ru.microservicesApp.orderService.dto.auth;

public record AuthResponse(String accessToken,
                           String tokenType,
                           long expiresIn) {
}
