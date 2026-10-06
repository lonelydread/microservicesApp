package ru.microservicesApp.orderService.service;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.microservicesApp.orderService.entity.User;
import ru.microservicesApp.orderService.exception.UserNotFoundException;
import ru.microservicesApp.orderService.mapper.UserMapper;
import ru.microservicesApp.orderService.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Transactional
    public User createUser(User user) {
        user.setId(null);
        user.setPassword(encodePassword(user.getPassword()));
        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(Long userId, User updatedUser) {
        User user = getUserById(userId);
        String encodedPassword = encodePassword(updatedUser.getPassword());
        userMapper.updateEntity(user, updatedUser, encodedPassword);
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = getUserById(userId);
        userRepository.delete(user);
    }

    private String encodePassword(@NotBlank String password) {
        return passwordEncoder.encode(password);
    }
}
