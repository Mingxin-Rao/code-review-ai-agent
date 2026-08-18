package com.codeguardian.service.auth;

import cn.dev33.satoken.stp.StpInterface;
import com.codeguardian.repository.RolePermissionRepository;
import com.codeguardian.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sa-Token permission and role resolution implementation
 *
 * <p>Loads the current principal's permissions and roles by login ID, using the database-backed
 * / Based on the database user-role and role-permission relationships, loads the permission and role lists of the current subject by login ID.</p>
 */
@Component
@RequiredArgsConstructor
public class SaPermissionImpl implements StpInterface {

    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    /**
     * Load the permission list
     *
     * @param loginId login ID (user ID)
     * @param loginType login type (unused)
     * @return list of permission codes
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = Long.parseLong(String.valueOf(loginId));
        List<com.codeguardian.entity.UserRole> urs = userRoleRepository.findByUserId(userId);
        Set<String> perms = new HashSet<>();
        for (com.codeguardian.entity.UserRole ur : urs) {
            perms.addAll(rolePermissionRepository.findPermissionCodesByRoleId(ur.getRoleId()));
        }
        return new ArrayList<>(perms);
    }

    /**
     * Load the role list
     *
     * @param loginId login ID (user ID)
     * @param loginType login type (unused)
     * @return list of role codes
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Long.parseLong(String.valueOf(loginId));
        return userRoleRepository.findRoleCodesByUserId(userId);
    }
}
