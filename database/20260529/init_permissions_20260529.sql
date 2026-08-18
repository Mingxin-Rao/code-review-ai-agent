-- ============================================
-- CodeGuardian AI - roles, permissions and admin bootstrap
-- Version: v2.1.0
-- Date: 2026-05-29
-- Notes: matches the current entity structure to avoid type mismatches
-- ============================================

-- 1) Roles
INSERT INTO roles (code, name, description, status)
VALUES
  ('ADMIN', 'Administrator', 'System administrator with all permissions', 0),
  ('REVIEWER', 'Reviewer', 'Can create and view review tasks', 0),
  ('VIEWER', 'Viewer', 'Can only view review tasks and reports', 0)
ON CONFLICT (code) DO NOTHING;

-- 2) Permissions (resource/action are integer-coded: READ=1, CREATE=2, UPDATE=3, ALL=99; TASK=1, REPORT=2, CONFIG=3, ALL=99)
INSERT INTO permissions (code, name, description, resource, action, created_at)
VALUES
  ('QUERY',  'Query permission',     'Can view review tasks, reports and history', 1, 1, CURRENT_TIMESTAMP),
  ('REVIEW', 'Review permission',     'Can create and run code review tasks',         1, 2, CURRENT_TIMESTAMP),
  ('CONFIG', 'Config permission',     'Can change system and AI configuration',         3, 3, CURRENT_TIMESTAMP),
  ('ADMIN',  'Administrator permission',   'All permissions, including user and role management',    99, 99, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

-- 3) Role-permission bindings
-- Administrator: all
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r, permissions p
WHERE r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Reviewer: QUERY + REVIEW
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
JOIN permissions p ON p.code IN ('QUERY','REVIEW')
WHERE r.code = 'REVIEWER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Viewer: QUERY
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
JOIN permissions p ON p.code = 'QUERY'
WHERE r.code = 'VIEWER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 4) Default admin account (replace the password hash in production)
INSERT INTO users (username, email, password_hash, real_name, status, created_at)
VALUES ('admin', 'admin@codeguardian.com', '$2a$10$bqoXICof7SwJ86evsdP82OYrfzyTMO9iAEzaG7exhSqFMDStIbTlG', 'System Administrator', 0, CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;

-- Bind the ADMIN role
INSERT INTO user_roles (user_id, role_id, created_at)
SELECT u.id, r.id, CURRENT_TIMESTAMP
FROM users u, roles r
WHERE u.username = 'admin' AND r.code = 'ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Done
