package com.trademaster.ims.service;

import com.trademaster.ims.model.User;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.UserRepository;
import com.trademaster.ims.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "User")
    public User createUser(User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }
        // validate role exists
        roleRepository.findById(user.getRoleId())
            .orElseThrow(() -> new RuntimeException("Invalid role ID"));

        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        user.setCreatedAt(null); // will be auto-set by auditing
        user.setUpdatedAt(null);
        return userRepository.save(user);
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "User")
    public User updateUser(Long id, User userDetails) {
        User existing = getUserById(id);
        existing.setFullName(userDetails.getFullName());
        existing.setEmail(userDetails.getEmail());
        existing.setPhone(userDetails.getPhone());
        existing.setRoleId(userDetails.getRoleId());
        existing.setIsActive(userDetails.getIsActive());

        if (userDetails.getPasswordHash() != null && !userDetails.getPasswordHash().isEmpty()) {
            existing.setPasswordHash(passwordEncoder.encode(userDetails.getPasswordHash()));
        }
        return userRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "User")
    public void deleteUser(Long id) {
        User user = getUserById(id);
        user.setIsActive(false);
        userRepository.save(user);
    }

    public boolean validatePassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
