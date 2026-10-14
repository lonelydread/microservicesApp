package ru.microservicesApp.orderService.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.microservicesApp.orderService.dto.user.UserRequest;
import ru.microservicesApp.orderService.dto.user.UserResponse;
import ru.microservicesApp.orderService.mapper.UserMapper;
import ru.microservicesApp.orderService.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers().stream().map(userMapper::toResponse).toList();
    }

    @GetMapping("/{user_id}")
    public UserResponse getUserById(@PathVariable("user_id") UUID userId) {
        return userMapper.toResponse(userService.getUserById(userId));
    }

    @PostMapping
    public void createUser(@Valid @RequestBody UserRequest request) {
        UserResponse response = userMapper.toResponse(userService.createUser(userMapper.toEntity(request)));
    }

    @PutMapping("/{user_id}")
    public void updateUser(@PathVariable("user_id") UUID userId, @Valid @RequestBody UserRequest request) {
        userService.updateUser(userId, userMapper.toEntity(request));
    }

    @DeleteMapping("/{user_id}")
    public void deleteUser(@PathVariable("user_id") UUID userId) {
        userService.deleteUser(userId);
    }
}
