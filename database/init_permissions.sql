-- ============================================
-- CodeGuardian AI - permission seed script
-- Version: v1.1.0
-- Date: 2025-12-04
-- Notes: seeds roles, permissions and the default admin user
-- ============================================

-- ============================================
-- 1. Seed roles
-- ============================================

INSERT INTO roles (code, name, description) VALUES
('ADMIN', 'Administrator', 'System administrator with all permissions'),
('REVIEWER', 'Reviewer', 'Can create and view review tasks'),
('VIEWER', 'Viewer', 'Can only view review tasks and reports')
ON CONFLICT (code) DO NOTHING;

-- ============================================
-- 2. Seed permissions
-- ============================================

INSERT INTO permissions (code, name, description, resource, action) VALUES
('QUERY', 'Query permission', 'Can view review tasks, reports and history', 'TASK,REPORT', 'READ'),
('REVIEW', 'Review permission', 'Can create and run code review tasks', 'TASK', 'CREATE,READ'),
('CONFIG', 'Config permission', 'Can change system and AI configuration', 'CONFIG', 'READ,UPDATE'),
('ADMIN', 'Administrator permission', 'All permissions, including user and role management', 'ALL', 'ALL')
ON CONFLICT (code) DO NOTHING;

-- ============================================
-- 3. Seed role-permission associations
-- ============================================

-- The ADMIN role holds every permission
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- The REVIEWER role holds QUERY and REVIEW
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'REVIEWER' AND p.code IN ('QUERY', 'REVIEW')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- The VIEWER role holds QUERY only
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'VIEWER' AND p.code = 'QUERY'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================
-- 4. Create the default admin user
-- ============================================
-- Note: the password is admin123 - change it before real use
-- The password hash is BCrypt, e.g. $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- In real use, generate the BCrypt hash in the application layer

INSERT INTO users (username, email, password_hash, real_name, status) VALUES
('admin', 'admin@codeguardian.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'System Administrator', 'ACTIVE')
ON CONFLICT (username) DO NOTHING;

-- Grant the ADMIN role to the admin user
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'admin' AND r.code = 'ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ============================================
-- Script complete
-- ============================================
-- Default admin account:
-- Username: admin
-- Password: admin123 (change this immediately in production)
-- Email: admin@codeguardian.com

