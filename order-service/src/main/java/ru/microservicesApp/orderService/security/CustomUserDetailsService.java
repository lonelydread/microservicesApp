package ru.microservicesApp.orderService.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.microservicesApp.orderService.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        String role = user.getRole() == null ? "ROLE_USER" : user.getRole().name();
        return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                .password(user.getPassword()).authorities(role).build();
    }
}
