package ru.microservicesApp.orderService.dto.user;

import java.util.Set;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.microservicesApp.orderService.entity.Role;
import ru.microservicesApp.orderService.validation.PasswordByteLength;

public record UserRequest(
        @NotBlank @Size(max = 255) String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8) @PasswordByteLength String password,
        @Size(min = 1) Set<@NotNull Role> roles) {
    @Override
    public String toString() {
        return "UserRequest[username=" + username + ", email=" + email + ", roles=" + roles + "]";
    }
}
