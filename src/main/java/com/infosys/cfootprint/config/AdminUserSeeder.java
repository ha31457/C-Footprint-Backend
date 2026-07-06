package com.infosys.cfootprint.config;

import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import org.springframework.context.annotation.Lazy;

@Component
public class AdminUserSeeder implements CommandLineRunner {

    @Autowired
    @Lazy
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.findByEmail("admin@cfootprint.com").isEmpty()) {
            User admin = User.builder()
                    .username("admin")
                    .email("admin@cfootprint.com")
                    .password(passwordEncoder.encode("adminpassword"))
                    .role("ROLE_ADMIN")
                    .isEnabled(true)
                    .build();
            userRepository.save(admin);
            System.out.println("Default Admin user successfully seeded!");
        }
    }
}
