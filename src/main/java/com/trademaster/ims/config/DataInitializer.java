package com.trademaster.ims.config;

import com.trademaster.ims.model.Role;
import com.trademaster.ims.model.User;
import com.trademaster.ims.repository.RoleRepository;
import com.trademaster.ims.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${app.default-admin.enabled:false}")
    private boolean defaultAdminEnabled;

    @Value("${app.default-admin.username:admin}")
    private String defaultAdminUsername;

    @Value("${app.default-admin.password:}")
    private String defaultAdminPassword;

    @Value("${app.default-admin.email:admin@trademaster.com}")
    private String defaultAdminEmail;

    @Value("${app.default-admin.phone:01700000000}")
    private String defaultAdminPhone;

    @Override
    public void run(String... args) throws Exception {
        if (!defaultAdminEnabled) {
            return;
        }

        if (userRepository.count() == 0) {
            if (defaultAdminPassword == null || defaultAdminPassword.trim().isEmpty()) {
                throw new IllegalStateException("Default admin is enabled, but app.default-admin.password is not configured");
            }

            Role superAdminRole = roleRepository.findByRoleName("SUPER_ADMIN")
                    .orElseGet(() -> {
                        Role r = new Role();
                        r.setRoleName("SUPER_ADMIN");
                        r.setDescription("Full system access");
                        r.setStatus(true);
                        return roleRepository.save(r);
                    });

            User admin = new User();
            admin.setUsername(defaultAdminUsername);
            admin.setPasswordHash(passwordEncoder.encode(defaultAdminPassword));
            admin.setFullName("System Administrator");
            admin.setEmail(defaultAdminEmail);
            admin.setPhone(defaultAdminPhone);
            admin.setRoleId(superAdminRole.getRoleId());
            admin.setIsActive(true);
            userRepository.save(admin);
            System.out.println("Default admin created: username=" + defaultAdminUsername);
        }
    }
}
