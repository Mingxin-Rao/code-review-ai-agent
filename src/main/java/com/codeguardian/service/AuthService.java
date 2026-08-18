package com.codeguardian.service;

import com.codeguardian.dto.LoginRequestDTO;
import com.codeguardian.dto.LoginResponseDTO;
import com.codeguardian.entity.User;
import com.codeguardian.repository.UserRepository;
import com.codeguardian.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Authentication service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    
    /**
     * User login
     *
     * <p>Looks up the user by username or email, verifies the password and status, then establishes a login session via Sa-Token, and returns a response object containing the token.</p>
     *
     * @param request login request (username or email, and password)
     * @param clientIp client IP, used to record audit information
     * @return login response object; on success it contains basic user info and the token
     */
    public LoginResponseDTO login(LoginRequestDTO request, String clientIp) {
        log.info("User login attempt: {}", request.getUsernameOrEmail());
        
        // look up the user by username or email
        User user = userRepository.findByUsernameOrEmail(
            request.getUsernameOrEmail(), 
            request.getUsernameOrEmail()
        ).orElse(null);
        
        if (user == null) {
            log.warn("User not found: {}", request.getUsernameOrEmail());
            return LoginResponseDTO.builder()
                .success(false)
                .message("Invalid username or password")
                .build();
        }
        
        // check the user's status
        if (user.getStatus() != 0) { // 0 = ACTIVE
            log.warn("Abnormal user status: {}, status={}", request.getUsernameOrEmail(), user.getStatus());
            return LoginResponseDTO.builder()
                .success(false)
                .message("User is disabled or locked")
                .build();
        }
        
        // verify the password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Incorrect password: {}", request.getUsernameOrEmail());
            return LoginResponseDTO.builder()
                .success(false)
                .message("Invalid username or password")
                .build();
        }
        
        // update last login info
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(clientIp);
        userRepository.save(user);
        
        // query the user's role names (take the first role)
        List<String> roleNames = userRoleRepository.findRoleNamesByUserId(user.getId());
        String roleName = roleNames != null && !roleNames.isEmpty()
            ? roleNames.get(0)
            : "System Administrator"; // default role name
        
        log.info("User login successful: {}, role: {}", user.getUsername(), roleName);
        
        // establish login session via Sa-Token
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();
        return LoginResponseDTO.builder()
            .success(true)
            .message("Login successful")
            .userId(user.getId())
            .username(user.getUsername())
            .realName(roleName)
            .token(token)
            .build();
    }
}
