package ru.microservicesApp.orderService.controller;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.microservicesApp.orderService.config.SecurityConfig;
import ru.microservicesApp.orderService.entity.User;
import ru.microservicesApp.orderService.exception.GlobalExceptionHandler;
import ru.microservicesApp.orderService.mapper.UserMapper;
import ru.microservicesApp.orderService.repository.UserRepository;
import ru.microservicesApp.orderService.service.UserService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserCrudTests {
    private UserRepository repository;
    private PasswordEncoder passwordEncoder;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        passwordEncoder = new SecurityConfig().passwordEncoder();
        mvc = MockMvcBuilders.standaloneSetup(
                        new UserController(new UserService(repository, passwordEncoder, new UserMapper()), new UserMapper()))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void returnsEmptyUserList() throws Exception {
        when(repository.findAll()).thenReturn(List.of());
        mvc.perform(get("/api/users")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void readsListAndSingleUserWithoutExposingPassword() throws Exception {
        User user = user(7L, "alice", "alice@example.com", "private-password-hash");
        when(repository.findAll()).thenReturn(List.of(user));
        when(repository.findById(new java.util.UUID(0, 7))).thenReturn(Optional.of(user));
        String expected = "{\"id\":\"00000000-0000-0000-0000-000000000007\",\"username\":\"alice\",\"email\":\"alice@example.com\"}";
        mvc.perform(get("/api/users")).andExpect(status().isOk())
                .andExpect(content().json("[" + expected + "]"));
        var response = mvc.perform(get("/api/users/00000000-0000-0000-0000-000000000007")).andExpect(status().isOk())
                .andExpect(content().json(expected)).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("password"));
        assertFalse(response.contains("private-password-hash"));
    }

    @Test
    void createsUserWithEncodedPassword() throws Exception {
        when(repository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertNull(user.getId());
            user.setId(new java.util.UUID(0, 7));
            return user;
        });
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(request("alice", "alice@example.com", "secret-password")))
                .andExpect(status().isOk());
        ArgumentCaptor<User> captured = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captured.capture());
        assertNotEquals("secret-password", captured.getValue().getPassword());
        assertTrue(passwordEncoder.matches("secret-password", captured.getValue().getPassword()));
    }

    @Test
    void updatesExistingUserAndHashesNewPassword() throws Exception {
        User existing = user(7L, "alice", "alice@example.com", "old-password-hash");
        when(repository.findById(new java.util.UUID(0, 7))).thenReturn(Optional.of(existing));
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(put("/api/users/00000000-0000-0000-0000-000000000007").contentType(MediaType.APPLICATION_JSON)
                        .content(request("bob", "bob@example.com", "new-password")))
                .andExpect(status().isOk());
        verify(repository).save(existing);
        assertEquals(new java.util.UUID(0, 7), existing.getId());
        assertTrue(passwordEncoder.matches("new-password", existing.getPassword()));
    }

    @Test
    void deletesExistingUserWithEmptyBody() throws Exception {
        User existing = user(7L, "alice", "alice@example.com", "hash");
        when(repository.findById(new java.util.UUID(0, 7))).thenReturn(Optional.of(existing));
        mvc.perform(delete("/api/users/00000000-0000-0000-0000-000000000007")).andExpect(status().isOk())
                .andExpect(content().string(""));
        verify(repository).delete(existing);
    }

    @Test
    void returnsNotFoundForReadUpdateAndDeleteWithoutCreatingUser() throws Exception {
        when(repository.findById(new java.util.UUID(0, 7))).thenReturn(Optional.empty());
        mvc.perform(get("/api/users/00000000-0000-0000-0000-000000000007")).andExpect(status().isNotFound());
        mvc.perform(put("/api/users/00000000-0000-0000-0000-000000000007").contentType(MediaType.APPLICATION_JSON)
                .content(request("alice", "alice@example.com", "password")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/users/00000000-0000-0000-0000-000000000007")).andExpect(status().isNotFound());
        verify(repository, never()).save(any(User.class));
        verify(repository, never()).delete(any(User.class));
    }

    @Test
    void returnsConflictWithoutExposingDatabaseDetails() throws Exception {
        when(repository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("private database constraint details"));
        var response = mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(request("alice", "alice@example.com", "password")))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("private database constraint details"));
    }

    @Test
    void returnsConflictWhenUserIsReferencedByOrders() throws Exception {
        User existing = user(7L, "alice", "alice@example.com", "hash");
        when(repository.findById(new java.util.UUID(0, 7))).thenReturn(Optional.of(existing));
        doThrow(new DataIntegrityViolationException("foreign key constraint"))
                .when(repository).delete(existing);
        mvc.perform(delete("/api/users/00000000-0000-0000-0000-000000000007")).andExpect(status().isConflict());
    }

    @Test
    void rejectsMalformedOrMissingBodyBeforeRepositoryAccess() throws Exception {
        for (String body : List.of("", "null", "{")) {
            mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(repository);
    }

    @Test
    void doesNotSaveWhenPasswordIsMissing() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"email\":\"alice@example.com\"}"))
                .andExpect(status().isBadRequest());
        verify(repository, never()).save(any(User.class));
    }

    private User user(Long id, String username, String email, String password) {
        User user = new User();
        user.setId(new java.util.UUID(0, id));
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(password);
        return user;
    }

    private String request(String username, String email, String password) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + email
                + "\",\"password\":\"" + password + "\"}";
    }
}
