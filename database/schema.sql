-- ============================================
-- CodeGuardian AI - PostgreSQL schema script
-- Version: v2.0.0
-- Date: 2025-12-04
-- Updated: 2025-12-04 (tightened column types, using TIMESTAMPTZ where appropriate)
-- ============================================

-- Connect to the database (run externally)
-- \c codeguardian;

-- ============================================
-- 1. Enable extensions
-- ============================================
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gin;
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================
-- 2. Create tables
-- ============================================

-- 2.1 users
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(32) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash CHAR(60) NOT NULL,
    real_name VARCHAR(64),
    phone VARCHAR(16),
    avatar_url TEXT,
    status SMALLINT NOT NULL DEFAULT 0,
    last_login_at TIMESTAMPTZ,
    last_login_ip INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB,
    
    CONSTRAINT chk_users_status 
        CHECK (status IN (0, 1, 2)),
    CONSTRAINT chk_users_email 
        CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
);

-- 2.2 roles
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    description TEXT,
    status SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_roles_status 
        CHECK (status IN (0, 1))
);

-- 2.3 permissions
CREATE TABLE IF NOT EXISTS permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    description TEXT,
    resource SMALLINT,
    action SMALLINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2.4 user_roles
CREATE TABLE IF NOT EXISTS user_roles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_user_roles_user_id 
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role_id 
        FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_roles_unique UNIQUE (user_id, role_id)
);

-- 2.5 role_permissions
CREATE TABLE IF NOT EXISTS role_permissions (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_role_permissions_role_id 
        FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission_id 
        FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
    CONSTRAINT uk_role_permissions_unique UNIQUE (role_id, permission_id)
);

-- 2.6 review_tasks
CREATE TABLE IF NOT EXISTS review_tasks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    review_type SMALLINT NOT NULL,
    scope TEXT,
    status SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    error_message TEXT,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB,
    
    CONSTRAINT fk_review_tasks_user_id 
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_review_tasks_status 
        CHECK (status IN (0, 1, 2, 3)),
    CONSTRAINT chk_review_tasks_type 
        CHECK (review_type IN (0, 1, 2, 3, 4)),
    CONSTRAINT chk_review_tasks_time 
        CHECK (completed_at IS NULL OR completed_at >= created_at)
);

-- 2.7 findings
CREATE TABLE IF NOT EXISTS findings (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    severity SMALLINT NOT NULL,
    title TEXT NOT NULL,
    location TEXT NOT NULL,
    start_line INTEGER,
    end_line INTEGER,
    description TEXT NOT NULL,
    suggestion TEXT,
    diff TEXT,
    category VARCHAR(32),
    rule_id BIGINT,
    confidence DECIMAL(3,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key constraints
    CONSTRAINT fk_findings_task_id 
        FOREIGN KEY (task_id) REFERENCES review_tasks(id) ON DELETE CASCADE,
    
    -- Check constraints
    CONSTRAINT chk_findings_severity 
        CHECK (severity IN (0, 1, 2, 3)),
    CONSTRAINT chk_findings_category 
        CHECK (category IS NULL OR category IN ('SECURITY','PERFORMANCE','BUG','CODE_STYLE','MAINTAINABILITY')),
    CONSTRAINT chk_findings_line 
        CHECK ((start_line IS NULL AND end_line IS NULL) OR 
               (start_line IS NOT NULL AND end_line IS NOT NULL AND end_line >= start_line)),
    CONSTRAINT chk_findings_confidence 
        CHECK (confidence IS NULL OR (confidence >= 0.00 AND confidence <= 1.00))
);

-- 2.8 review_reports
CREATE TABLE IF NOT EXISTS review_reports (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL UNIQUE,
    html_content TEXT,
    markdown_content TEXT,
    statistics JSONB,
    pdf_path TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key constraints
    CONSTRAINT fk_review_reports_task_id 
        FOREIGN KEY (task_id) REFERENCES review_tasks(id) ON DELETE CASCADE
);

-- 2.9 system_configs
CREATE TABLE IF NOT EXISTS system_configs (
    config_key VARCHAR(100) PRIMARY KEY,
    config_value TEXT,
    category VARCHAR(50),
    description VARCHAR(255),
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 3. Create indexes
-- ============================================

-- 3.1 users indexes
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_username_gin ON users USING gin(username gin_trgm_ops);

-- 3.2 roles indexes
CREATE INDEX IF NOT EXISTS idx_roles_status ON roles(status);

-- 3.3 permissions indexes
CREATE INDEX IF NOT EXISTS idx_permissions_resource ON permissions(resource);

-- 3.4 user_roles indexes
CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles(role_id);

-- 3.5 role_permissions indexes
CREATE INDEX IF NOT EXISTS idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX IF NOT EXISTS idx_role_permissions_permission_id ON role_permissions(permission_id);

-- 3.6 review_tasks indexes
CREATE INDEX IF NOT EXISTS idx_review_tasks_user_id ON review_tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_review_tasks_type ON review_tasks(review_type);
CREATE INDEX IF NOT EXISTS idx_review_tasks_status ON review_tasks(status);
CREATE INDEX IF NOT EXISTS idx_review_tasks_created_at ON review_tasks(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_review_tasks_status_created_at ON review_tasks(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_review_tasks_name_gin ON review_tasks USING gin(name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_review_tasks_metadata_gin ON review_tasks USING gin(metadata);

-- 3.2 findings indexes
CREATE INDEX IF NOT EXISTS idx_findings_task_id ON findings(task_id);
CREATE INDEX IF NOT EXISTS idx_findings_severity ON findings(severity);
CREATE INDEX IF NOT EXISTS idx_findings_category ON findings(category);
CREATE INDEX IF NOT EXISTS idx_findings_task_severity ON findings(task_id, severity);
CREATE INDEX IF NOT EXISTS idx_findings_task_category ON findings(task_id, category);
CREATE INDEX IF NOT EXISTS idx_findings_title_gin ON findings USING gin(title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_findings_created_at ON findings(created_at DESC);

-- 3.3 review_reports indexes
CREATE INDEX IF NOT EXISTS idx_review_reports_created_at ON review_reports(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_review_reports_statistics_gin ON review_reports USING gin(statistics);

-- ============================================
-- 4. Create trigger functions
-- ============================================

-- Function that maintains the updated-at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- 5. Create triggers
-- ============================================

-- review_tasks trigger
DROP TRIGGER IF EXISTS trigger_review_tasks_updated_at ON review_tasks;
CREATE TRIGGER trigger_review_tasks_updated_at
    BEFORE UPDATE ON review_tasks
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- review_reports trigger
DROP TRIGGER IF EXISTS trigger_review_reports_updated_at ON review_reports;
CREATE TRIGGER trigger_review_reports_updated_at
    BEFORE UPDATE ON review_reports
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- 6. Table and column comments
-- ============================================

-- users comments
COMMENT ON TABLE users IS 'Users';
COMMENT ON COLUMN users.id IS 'User ID, auto-increment primary key';
COMMENT ON COLUMN users.username IS 'Username, unique identifier';
COMMENT ON COLUMN users.email IS 'Email address, used for login and notifications';
COMMENT ON COLUMN users.password_hash IS 'Password hash (BCrypt)';
COMMENT ON COLUMN users.status IS 'User status: ACTIVE / INACTIVE / LOCKED';

-- roles comments
COMMENT ON TABLE roles IS 'Roles';
COMMENT ON COLUMN roles.id IS 'Role ID, auto-increment primary key';
COMMENT ON COLUMN roles.code IS 'Role code, unique identifier, e.g. ADMIN, REVIEWER, VIEWER';
COMMENT ON COLUMN roles.name IS 'Role display name, e.g. Administrator, Reviewer, Viewer';

-- permissions comments
COMMENT ON TABLE permissions IS 'Permissions';
COMMENT ON COLUMN permissions.id IS 'Permission ID, auto-increment primary key';
COMMENT ON COLUMN permissions.code IS 'Permission code, unique identifier, e.g. QUERY, REVIEW, CONFIG, ADMIN';
COMMENT ON COLUMN permissions.name IS 'Permission display name, e.g. Query, Review, Config';

-- user_roles comments
COMMENT ON TABLE user_roles IS 'User-role associations';

-- role_permissions comments
COMMENT ON TABLE role_permissions IS 'Role-permission associations';

-- review_tasks comments
COMMENT ON COLUMN review_tasks.user_id IS 'Creating user ID, references users';
COMMENT ON TABLE review_tasks IS 'Code review tasks';
COMMENT ON COLUMN review_tasks.id IS 'Task ID, auto-increment primary key';
COMMENT ON COLUMN review_tasks.name IS 'Task name';
COMMENT ON COLUMN review_tasks.review_type IS 'Review type: PROJECT / DIRECTORY / FILE / SNIPPET / GIT';
COMMENT ON COLUMN review_tasks.scope IS 'Review scope: a file path, a directory path, or a code snippet';
COMMENT ON COLUMN review_tasks.status IS 'Task status: PENDING / RUNNING / COMPLETED / FAILED';
COMMENT ON COLUMN review_tasks.created_at IS 'Task creation time';
COMMENT ON COLUMN review_tasks.completed_at IS 'Task completion time';
COMMENT ON COLUMN review_tasks.error_message IS 'Error message, recorded when the task fails';
COMMENT ON COLUMN review_tasks.metadata IS 'Metadata (JSON) for extension fields';

-- findings comments
COMMENT ON TABLE findings IS 'Issues found during code review';
COMMENT ON COLUMN findings.id IS 'Finding ID, auto-increment primary key';
COMMENT ON COLUMN findings.task_id IS 'Associated review task ID';
COMMENT ON COLUMN findings.severity IS 'Severity: CRITICAL / HIGH / MEDIUM / LOW';
COMMENT ON COLUMN findings.title IS 'Issue title';
COMMENT ON COLUMN findings.location IS 'Issue location: a file path or a description of the position in the code';
COMMENT ON COLUMN findings.start_line IS 'Start line number';
COMMENT ON COLUMN findings.end_line IS 'End line number';
COMMENT ON COLUMN findings.description IS 'Detailed issue description';
COMMENT ON COLUMN findings.suggestion IS 'Suggested fix';
COMMENT ON COLUMN findings.diff IS 'Suggested fix as a diff';
COMMENT ON COLUMN findings.category IS 'Issue category: SECURITY / PERFORMANCE / BUG / CODE_STYLE / MAINTAINABILITY';

-- review_reports comments
COMMENT ON TABLE review_reports IS 'Code review reports';
COMMENT ON COLUMN review_reports.id IS 'Report ID, auto-increment primary key';
COMMENT ON COLUMN review_reports.task_id IS 'Associated review task ID (one-to-one)';
COMMENT ON COLUMN review_reports.html_content IS 'Report body in HTML';
COMMENT ON COLUMN review_reports.markdown_content IS 'Report body in Markdown';
COMMENT ON COLUMN review_reports.statistics IS 'Summary statistics (JSON): issue counts, severity distribution, etc.';

-- system_configs comments
COMMENT ON TABLE system_configs IS 'System configuration';

-- 2.10 operation_logs
CREATE TABLE IF NOT EXISTS operation_logs (
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

COMMENT ON TABLE operation_logs IS 'System operation log';
COMMENT ON COLUMN operation_logs.user_id IS 'User ID';
COMMENT ON COLUMN operation_logs.username IS 'Username';
COMMENT ON COLUMN operation_logs.operation IS 'User operation';
COMMENT ON COLUMN operation_logs.method IS 'Request method';
COMMENT ON COLUMN operation_logs.params IS 'Request parameters';
COMMENT ON COLUMN operation_logs.time_millis IS 'Duration in milliseconds';
COMMENT ON COLUMN operation_logs.ip IS 'IP address';
COMMENT ON COLUMN operation_logs.status IS 'Status (0 = success, 1 = failure)';
COMMENT ON COLUMN operation_logs.error_msg IS 'Error message';

CREATE INDEX IF NOT EXISTS idx_operation_logs_username ON operation_logs(username);
CREATE INDEX IF NOT EXISTS idx_operation_logs_created_at ON operation_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_operation_logs_status ON operation_logs(status);
 
 COMMENT ON COLUMN system_configs.config_key IS 'Config key, unique identifier';
COMMENT ON COLUMN system_configs.config_value IS 'Config value';
COMMENT ON COLUMN system_configs.category IS 'Config category';
COMMENT ON COLUMN system_configs.description IS 'Config description';


-- ============================================
-- 7. Create views
-- ============================================

-- User permission view
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

COMMENT ON VIEW v_user_permissions IS 'User permission view: all roles and permissions for each user';

-- Task statistics view
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
    COUNT(CASE WHEN f.severity = 3 THEN 1 END) AS low_count,
    CASE 
        WHEN t.completed_at IS NOT NULL AND t.created_at IS NOT NULL 
        THEN EXTRACT(EPOCH FROM (t.completed_at - t.created_at))
        ELSE NULL 
    END AS duration_seconds
FROM review_tasks t
LEFT JOIN findings f ON t.id = f.task_id
GROUP BY t.id, t.name, t.review_type, t.status, t.created_at, t.completed_at;

COMMENT ON VIEW v_task_statistics IS 'Task statistics view: issue counts per task';

-- Finding category statistics view
CREATE OR REPLACE VIEW v_finding_category_statistics AS
SELECT 
    category,
    severity,
    COUNT(*) AS count,
    ROUND(AVG(confidence), 2) AS avg_confidence
FROM findings
WHERE category IS NOT NULL
GROUP BY category, severity
ORDER BY category, severity;

COMMENT ON VIEW v_finding_category_statistics IS 'Finding category statistics view: grouped by category and severity';

-- ============================================
-- 8. Create stored procedures
-- ============================================

-- Stored procedure: check a user permission
CREATE OR REPLACE FUNCTION check_user_permission(
    p_user_id BIGINT,
    p_permission_code VARCHAR(32)
)
RETURNS BOOLEAN AS $$
DECLARE
    has_permission BOOLEAN;
BEGIN
    SELECT COUNT(*) > 0 INTO has_permission
    FROM users u
    JOIN user_roles ur ON u.id = ur.user_id
    JOIN roles r ON ur.role_id = r.id
    JOIN role_permissions rp ON r.id = rp.role_id
    JOIN permissions p ON rp.permission_id = p.id
    WHERE u.id = p_user_id 
      AND u.status = 0 
      AND r.status = 0
      AND (p.code = p_permission_code OR p.code = 'ADMIN');
    
    RETURN has_permission;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION check_user_permission IS 'Check whether a user holds a specific permission';

-- Stored procedure: clean up stale data
CREATE OR REPLACE FUNCTION cleanup_old_tasks(days_to_keep INTEGER DEFAULT 90)
RETURNS TABLE(deleted_tasks BIGINT, deleted_findings BIGINT, deleted_reports BIGINT) AS $$
DECLARE
    task_count BIGINT;
    finding_count BIGINT;
    report_count BIGINT;
BEGIN
    -- Delete the associated findings
    WITH deleted_findings_cte AS (
        DELETE FROM findings 
        WHERE task_id IN (
            SELECT id FROM review_tasks 
            WHERE status = 2 
            AND completed_at < CURRENT_TIMESTAMP - INTERVAL '1 day' * days_to_keep
        )
        RETURNING id
    )
    SELECT COUNT(*) INTO finding_count FROM deleted_findings_cte;
    
    -- Delete the associated reports
    WITH deleted_reports_cte AS (
        DELETE FROM review_reports 
        WHERE task_id IN (
            SELECT id FROM review_tasks 
            WHERE status = 2 
            AND completed_at < CURRENT_TIMESTAMP - INTERVAL '1 day' * days_to_keep
        )
        RETURNING id
    )
    SELECT COUNT(*) INTO report_count FROM deleted_reports_cte;
    
    -- Delete the tasks
    WITH deleted_tasks_cte AS (
        DELETE FROM review_tasks 
        WHERE status = 'COMPLETED' 
        AND completed_at < CURRENT_TIMESTAMP - INTERVAL '1 day' * days_to_keep
        RETURNING id
    )
    SELECT COUNT(*) INTO task_count FROM deleted_tasks_cte;
    
    RETURN QUERY SELECT task_count, finding_count, report_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_old_tasks IS 'Delete completed tasks older than the given number of days, along with their related data';

-- ============================================
-- Script complete
-- ============================================
