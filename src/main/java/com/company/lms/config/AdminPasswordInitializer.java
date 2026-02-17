package com.company.lms.config;

import com.company.lms.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminPasswordInitializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        userRepository.findByEmail("admin@local").ifPresent(user -> {
            if (!passwordEncoder.matches("Admin123!", user.getPasswordHash())) {
                user.setPasswordHash(passwordEncoder.encode("Admin123!"));
                userRepository.save(user);
            }
        });
    }
}
