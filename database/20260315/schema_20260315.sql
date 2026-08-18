-- ============================================
-- CodeGuardian AI - PostgreSQL schema script (latest)
-- Version: v2.1.0
-- Date: 2026-03-15
-- Notes: regenerated from the current JPA entities; columns and constraints match the code
-- ============================================

-- Optional: connect to the target database (run externally)
-- \c codeguardian;

-- ============================================
-- 1. Enable extensions
-- ============================================
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gin;
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================
-- 2. Create tables (aligned with the JPA entities)
-- ============================================

-- 2.1 users
DROP TABLE IF EXISTS users CASCADE;
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(32) NOT NULL UNIQUE,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(60) NOT NULL,
  real_name VARCHAR(64),
  phone VARCHAR(16),
  avatar_url TEXT,
  status INTEGER NOT NULL DEFAULT 0, -- 0=ACTIVE,1=INACTIVE,2=LOCKED
  last_login_at TIMESTAMPTZ,
  last_login_ip VARCHAR(45),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ
);

-- 2.2 roles
DROP TABLE IF EXISTS roles CASCADE;
CREATE TABLE roles (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(64) NOT NULL,
  description TEXT,
  status INTEGER NOT NULL DEFAULT 0, -- 0=ACTIVE,1=INACTIVE
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ
);

-- 2.3 permissions
DROP TABLE IF EXISTS permissions CASCADE;
CREATE TABLE permissions (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(64) NOT NULL,
  description TEXT,
  resource INTEGER,
  action INTEGER,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2.4 user_roles
DROP TABLE IF EXISTS user_roles CASCADE;
CREATE TABLE user_roles (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_user_roles_unique UNIQUE (user_id, role_id),
  CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- 2.5 role_permissions
DROP TABLE IF EXISTS role_permissions CASCADE;
CREATE TABLE role_permissions (
  id BIGSERIAL PRIMARY KEY,
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_role_permissions_unique UNIQUE (role_id, permission_id),
  CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
  CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

-- 2.6 review_tasks (aligned with ReviewTask.java: no user_id/metadata)
DROP TABLE IF EXISTS review_tasks CASCADE;
CREATE TABLE review_tasks (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  review_type INTEGER NOT NULL, -- ReviewTypeEnum 0..4
  scope TEXT,
  status INTEGER NOT NULL,      -- TaskStatusEnum 0..3
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMPTZ,
  error_message TEXT
);

-- 2.7 findings (aligned with Finding.java)
DROP TABLE IF EXISTS findings CASCADE;
CREATE TABLE findings (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL,
  severity INTEGER NOT NULL,     -- SeverityEnum 0..3
  title TEXT NOT NULL,
  location TEXT NOT NULL,
  start_line INTEGER,
  end_line INTEGER,
  description TEXT NOT NULL,
  suggestion TEXT,
  diff TEXT,
  category VARCHAR(32),
  source VARCHAR(32),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_findings_task FOREIGN KEY (task_id) REFERENCES review_tasks(id) ON DELETE CASCADE,
  CONSTRAINT chk_findings_line CHECK (
    (start_line IS NULL AND end_line IS NULL) OR
    (start_line IS NOT NULL AND end_line IS NOT NULL AND end_line >= start_line)
  )
);

-- 2.8 review_reports (aligned with ReviewReport.java)
DROP TABLE IF EXISTS review_reports CASCADE;
CREATE TABLE review_reports (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL UNIQUE,
  html_content TEXT,
  markdown_content TEXT,
  statistics TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ,
  CONSTRAINT fk_review_reports_task FOREIGN KEY (task_id) REFERENCES review_tasks(id) ON DELETE CASCADE
);

-- 2.9 system_configs (aligned with SystemConfig.java: id primary key + unique config_key)
DROP TABLE IF EXISTS system_configs CASCADE;
CREATE TABLE system_configs (
  id BIGSERIAL PRIMARY KEY,
  config_key VARCHAR(100) NOT NULL UNIQUE,
  config_value TEXT,
  category VARCHAR(50),
  description VARCHAR(255),
  updated_at TIMESTAMPTZ
);

-- 2.10 categories (aligned with Category.java)
DROP TABLE IF EXISTS categories CASCADE;
CREATE TABLE categories (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(64) NOT NULL,
  description TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ
);

-- 2.11 knowledge_documents (aligned with KnowledgeDocument.java)
DROP TABLE IF EXISTS knowledge_documents CASCADE;
CREATE TABLE knowledge_documents (
  id VARCHAR(64) PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  content TEXT,
  solution TEXT,
  category VARCHAR(32),
  metadata TEXT, -- MapJsonConverter (JSON stored as text)
  minio_bucket_name VARCHAR(128),
  minio_object_name VARCHAR(256),
  content_type VARCHAR(64),
  file_size BIGINT,
  create_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2.12 operation_logs (retained)
DROP TABLE IF EXISTS operation_logs CASCADE;
CREATE TABLE operation_logs (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT,
  username VARCHAR(32),
  operation VARCHAR(128),
  method VARCHAR(128),
  params TEXT,
  time_millis BIGINT,
  ip VARCHAR(64),
  status INTEGER DEFAULT 0,
  error_msg TEXT,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 3. Indexes
-- ============================================
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_username_gin ON users USING gin (username gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_roles_status ON roles(status);

CREATE INDEX IF NOT EXISTS idx_user_roles_user ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role ON user_roles(role_id);

CREATE INDEX IF NOT EXISTS idx_role_permissions_role ON role_permissions(role_id);
CREATE INDEX IF NOT EXISTS idx_role_permissions_permission ON role_permissions(permission_id);

CREATE INDEX IF NOT EXISTS idx_review_tasks_type ON review_tasks(review_type);
CREATE INDEX IF NOT EXISTS idx_review_tasks_status ON review_tasks(status);
CREATE INDEX IF NOT EXISTS idx_review_tasks_created_at ON review_tasks(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_findings_task ON findings(task_id);
CREATE INDEX IF NOT EXISTS idx_findings_severity ON findings(severity);
CREATE INDEX IF NOT EXISTS idx_findings_category ON findings(category);
CREATE INDEX IF NOT EXISTS idx_findings_created_at ON findings(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_review_reports_created_at ON review_reports(created_at DESC);

-- ============================================
-- 4. Triggers (updated-at timestamps)
-- ============================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_roles_updated_at ON roles;
CREATE TRIGGER trg_roles_updated_at BEFORE UPDATE ON roles
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_users_updated_at ON users;
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_review_reports_updated_at ON review_reports;
CREATE TRIGGER trg_review_reports_updated_at BEFORE UPDATE ON review_reports
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- 5. Views (retained, compatible with the new structure)
-- ============================================
CREATE OR REPLACE VIEW v_user_permissions AS
SELECT 
  u.id AS user_id,
  u.username,
  u.email,
  r.id AS role_id,
  r.code AS role_code,
  r.name AS role_name,
  p.id AS permission_id,
  p.code AS permission_code,
  p.name AS permission_name,
  p.resource,
  p.action
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id
JOIN role_permissions rp ON r.id = rp.role_id
JOIN permissions p ON rp.permission_id = p.id
WHERE u.status = 0 AND r.status = 0;

CREATE OR REPLACE VIEW v_task_statistics AS
SELECT 
  t.id AS task_id,
  t.name AS task_name,
  t.review_type,
  t.status,
  t.created_at,
  t.completed_at,
  COUNT(f.id) AS total_findings,
  COUNT(CASE WHEN f.severity = 0 THEN 1 END) AS critical_count,
  COUNT(CASE WHEN f.severity = 1 THEN 1 END) AS high_count,
  COUNT(CASE WHEN f.severity = 2 THEN 1 END) AS medium_count,
  COUNT(CASE WHEN f.severity = 3 THEN 1 END) AS low_count
FROM review_tasks t
LEFT JOIN findings f ON t.id = f.task_id
GROUP BY t.id, t.name, t.review_type, t.status, t.created_at, t.completed_at;

-- ============================================
-- Script complete
-- ============================================

