package ru.microservicesApp.orderService.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.microservicesApp.orderService.validation.PasswordByteLength;

public record RegisterRequest(
        @NotBlank @Size(max = 255) String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8) @PasswordByteLength String password) {
}
