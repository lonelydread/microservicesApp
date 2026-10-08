package ru.microservicesApp.orderService.service;


import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.microservicesApp.orderService.dto.auth.AuthResponse;
import ru.microservicesApp.orderService.dto.auth.LoginRequest;
import ru.microservicesApp.orderService.dto.auth.RegisterRequest;
import ru.microservicesApp.orderService.dto.auth.RefreshRequest;
import ru.microservicesApp.orderService.entity.User;
import ru.microservicesApp.orderService.mapper.UserMapper;
import ru.microservicesApp.orderService.repository.UserRepository;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtDecoder refreshJwtDecoder;
    private final UserRepository userRepository;
    private final UserService userService;
    private final UserMapper userMapper;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService,
                       @Qualifier("refreshJwtDecoder") JwtDecoder refreshJwtDecoder,
                       UserRepository userRepository, UserService userService, UserMapper userMapper) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshJwtDecoder = refreshJwtDecoder;
        this.userRepository = userRepository;
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        return jwtService.generateTokenPair(authentication(user), user.getId());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        User user = userService.createUser(userMapper.toEntity(request));
        return jwtService.generateTokenPair(authentication(user), user.getId());
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshRequest request) {
        Jwt token;
        try {
            token = refreshJwtDecoder.decode(request.refreshToken());
        } catch (JwtException exception) {
            throw new BadCredentialsException("Invalid refresh token", exception);
        }

        UUID userId = UUID.fromString(Objects.requireNonNull(token.getSubject()));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        return jwtService.refreshAccessToken(authentication(user), user.getId(), request.refreshToken());
    }

    private Authentication authentication(User user) {
        return UsernamePasswordAuthenticationToken.authenticated(user.getUsername(), null,
                user.getRoles().stream().map(role -> new SimpleGrantedAuthority(role.name())).toList());
    }
}
