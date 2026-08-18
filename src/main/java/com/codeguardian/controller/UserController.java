package com.codeguardian.controller;

import com.codeguardian.dto.*;
import com.codeguardian.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;

/**
 * User Management controller
 */
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    
    private final UserService userService;
    
    /**
     * User Management page
     *
     * <p>Requires login (@SaCheckLogin); renders the admin user list page.</p>
     *
     * @param model view model
     * @param session current session, used to read user info for display
     * @return template name
     */
    @GetMapping
    @SaCheckLogin
    public String userManagementPage(Model model, jakarta.servlet.http.HttpSession session) {
        com.codeguardian.util.ViewModelUtils.populateUserInfo(model, session);
        return "admin/users";
    }
    
    /**
     * Query users with pagination (API)
     *
     * <p>Requires `ADMIN` permission.</p>
     *
     * @param keyword keyword
     * @param status status filter
     * @param roleCode role filter
     * @param page page number
     * @param size page size
     * @return paged result
     */
    @GetMapping("/api")
    @ResponseBody
    @SaCheckPermission("ADMIN")
    public ResponseEntity<PageResult<UserDTO>> queryUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "roleCode", required = false) String roleCode,
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        
        UserQueryDTO queryDTO = UserQueryDTO.builder()
            .keyword(keyword)
            .status(status)
            .roleCode(roleCode)
            .page(page)
            .size(size)
            .build();
        
        PageResult<UserDTO> result = userService.queryUsers(queryDTO);
        return ResponseEntity.ok(result);
    }
    
    /**
     * Query a user by ID (API)
     *
     * <p>Requires `ADMIN` permission.</p>
     */
    @GetMapping("/api/{id}")
    @ResponseBody
    @SaCheckPermission("ADMIN")
    public ResponseEntity<UserDTO> getUserById(@PathVariable("id") Long id) {
        UserDTO user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }
    
    /**
     * Create a user (API)
     *
     * <p>Requires `ADMIN` permission.</p>
     */
    @PostMapping("/api")
    @ResponseBody
    @SaCheckPermission("ADMIN")
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody UserCreateDTO createDTO) {
        UserDTO user = userService.createUser(createDTO);
        return ResponseEntity.ok(user);
    }
    
    /**
     * Update a user (API)
     *
     * <p>Requires `ADMIN` permission.</p>
     */
    @PutMapping("/api/{id}")
    @ResponseBody
    @SaCheckPermission("ADMIN")
    public ResponseEntity<UserDTO> updateUser(
            @PathVariable("id") Long id,
            @Valid @RequestBody UserUpdateDTO updateDTO) {
        UserDTO user = userService.updateUser(id, updateDTO);
        return ResponseEntity.ok(user);
    }
    
    /**
     * Delete a user (API)
     *
     * <p>Requires `ADMIN` permission.</p>
     */
    @DeleteMapping("/api/{id}")
    @ResponseBody
    @SaCheckPermission("ADMIN")
    public ResponseEntity<Void> deleteUser(@PathVariable("id") Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }
}
