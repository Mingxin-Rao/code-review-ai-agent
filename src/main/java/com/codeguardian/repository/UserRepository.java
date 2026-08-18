package com.codeguardian.repository;

import com.codeguardian.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for users.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    
    /**
     * Find a user by username.
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Find a user by email.
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Find a user by username or email.
     */
    Optional<User> findByUsernameOrEmail(String username, String email);
    
    /**
     * Check whether a username exists.
     */
    boolean existsByUsername(String username);
    
    /**
     * Check whether an email exists.
     */
    boolean existsByEmail(String email);
}

