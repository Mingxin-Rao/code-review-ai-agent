package com.codeguardian.service;

import com.codeguardian.dto.*;
import com.codeguardian.entity.Role;
import com.codeguardian.entity.User;
import com.codeguardian.entity.UserRole;
import com.codeguardian.repository.RoleRepository;
import com.codeguardian.repository.UserRepository;
import com.codeguardian.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    
    /**
     * Query users with pagination
     */
    @Transactional(readOnly = true)
    public PageResult<UserDTO> queryUsers(UserQueryDTO queryDTO) {
        // build query conditions
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // keyword search (username or email)
            if (StringUtils.hasText(queryDTO.getKeyword())) {
                String keyword = "%" + queryDTO.getKeyword() + "%";
                Predicate usernamePredicate = cb.like(root.get("username"), keyword);
                Predicate emailPredicate = cb.like(root.get("email"), keyword);
                predicates.add(cb.or(usernamePredicate, emailPredicate));
            }
            
            // filter by status
            if (queryDTO.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), queryDTO.getStatus()));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        
        // pagination and sorting
        Pageable pageable = PageRequest.of(
            queryDTO.getPage(),
            queryDTO.getSize(),
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
        
        Page<User> userPage = userRepository.findAll(spec, pageable);
        
        // if a role filter is present, additional filtering is required
        List<UserDTO> userDTOs = userPage.getContent().stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
        
        // filter by role
        if (StringUtils.hasText(queryDTO.getRoleCode())) {
            userDTOs = userDTOs.stream()
                .filter(user -> user.getRoles().contains(queryDTO.getRoleCode()))
                .collect(Collectors.toList());
        }
        
        // build the paginated result
        return PageResult.<UserDTO>builder()
            .content(userDTOs)
            .totalElements(userPage.getTotalElements())
            .totalPages(userPage.getTotalPages())
            .page(queryDTO.getPage())
            .size(queryDTO.getSize())
            .hasPrevious(userPage.hasPrevious())
            .hasNext(userPage.hasNext())
            .build();
    }
    
    /**
     * Query a user by ID
     */
    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return convertToDTO(user);
    }
    
    /**
     * Create a user
     */
    @Transactional
    public UserDTO createUser(UserCreateDTO createDTO) {
        // check whether the username already exists
        if (userRepository.existsByUsername(createDTO.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        
        // check whether the email already exists
        if (userRepository.existsByEmail(createDTO.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        
        // create the user
        User user = User.builder()
            .username(createDTO.getUsername())
            .email(createDTO.getEmail())
            .passwordHash(passwordEncoder.encode(createDTO.getPassword()))
            .realName(createDTO.getRealName())
            .phone(createDTO.getPhone())
            .status(createDTO.getStatus() != null ? createDTO.getStatus() : 0)
            .createdAt(LocalDateTime.now())
            .build();
        
        user = userRepository.save(user);
        
        // assign roles
        if (createDTO.getRoleCodes() != null && !createDTO.getRoleCodes().isEmpty()) {
            assignRoles(user.getId(), createDTO.getRoleCodes());
        }
        
        log.info("User created successfully: username={}, id={}", user.getUsername(), user.getId());
        return convertToDTO(user);
    }
    
    /**
     * Update a user
     */
    @Transactional
    public UserDTO updateUser(Long id, UserUpdateDTO updateDTO) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // update basic information
        if (StringUtils.hasText(updateDTO.getRealName())) {
            user.setRealName(updateDTO.getRealName());
        }
        if (StringUtils.hasText(updateDTO.getEmail())) {
            if (userRepository.existsByEmail(updateDTO.getEmail()) && 
                !user.getEmail().equals(updateDTO.getEmail())) {
                throw new RuntimeException("Email is already in use");
            }
            user.setEmail(updateDTO.getEmail());
        }
        if (StringUtils.hasText(updateDTO.getPhone())) {
            user.setPhone(updateDTO.getPhone());
        }
        if (updateDTO.getStatus() != null) {
            user.setStatus(updateDTO.getStatus());
        }
        
        user = userRepository.save(user);
        
        // update roles
        if (updateDTO.getRoleCodes() != null) {
            assignRoles(id, updateDTO.getRoleCodes());
        }
        
        log.info("User updated successfully: id={}", id);
        return convertToDTO(user);
    }
    
    /**
     * Delete a user
     */
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found");
        }
        
        // delete user-role associations
        userRoleRepository.deleteByUserId(id);
        
        // delete the user
        userRepository.deleteById(id);
        
        log.info("User deleted successfully: id={}", id);
    }
    
    /**
     * Assign roles
     */
    @Transactional
    public void assignRoles(Long userId, List<String> roleCodes) {
        // delete existing roles
        userRoleRepository.deleteByUserId(userId);
        
        // add new roles
        for (String roleCode : roleCodes) {
            Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleCode));
            
            UserRole userRole = UserRole.builder()
                .userId(userId)
                .roleId(role.getId())
                .createdAt(LocalDateTime.now())
                .build();
            
            userRoleRepository.save(userRole);
        }
    }
    
    /**
     * Convert to DTO
     */
    private UserDTO convertToDTO(User user) {
        // query the user's roles
        List<String> roleCodes = userRoleRepository.findRoleCodesByUserId(user.getId());
        
        return UserDTO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .realName(user.getRealName())
            .phone(user.getPhone())
            .avatarUrl(user.getAvatarUrl())
            .status(user.getStatus())
            .roles(roleCodes)
            .createdAt(user.getCreatedAt())
            .lastLoginAt(user.getLastLoginAt())
            .lastLoginIp(user.getLastLoginIp())
            .build();
    }
}

