package com.trademaster.ims.service;

import com.trademaster.ims.model.Role;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleService {

    @Autowired
    private RoleRepository roleRepository;

    // Get all roles (unpaginated) – for dropdowns
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    // Get all active roles
    public List<Role> getActiveRoles() {
        return roleRepository.findByStatusTrue();
    }

    // Get paginated roles (for list page)
    public Page<Role> getRolesPaginated(Pageable pageable) {
        return roleRepository.findAll(pageable);
    }

    public Role getRoleById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found with id: " + id));
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "Role")
    public Role createRole(Role role) {
        if (roleRepository.findByRoleName(role.getRoleName()).isPresent()) {
            throw new RuntimeException("Role name already exists");
        }
        role.setRoleId(null);
        role.setStatus(true);
        return roleRepository.save(role);
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "Role")
    public Role updateRole(Long id, Role roleDetails) {
        Role existing = getRoleById(id);
        existing.setRoleName(roleDetails.getRoleName());
        existing.setDescription(roleDetails.getDescription());
        existing.setStatus(roleDetails.getStatus());
        return roleRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "Role")
    public void deleteRole(Long id) {
        Role role = getRoleById(id);
        role.setStatus(false);
        roleRepository.save(role);
    }
}
