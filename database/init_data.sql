-- ============================================
-- CodeGuardian AI - data seed script
-- Version: v1.0.0
-- Date: 2025-12-04
-- Notes: sample data for testing and demos
-- ============================================

-- ============================================
-- 1. Insert sample review tasks
-- ============================================
-- Note: run init_permissions.sql first to create the default admin user

-- Look up the default admin user ID
DO $$
DECLARE
    admin_user_id BIGINT;
BEGIN
    SELECT id INTO admin_user_id FROM users WHERE username = 'admin' LIMIT 1;
    
    IF admin_user_id IS NULL THEN
        RAISE EXCEPTION 'Admin user not found; run init_permissions.sql first';
    END IF;

    -- Sample 1: snippet review (completed)
    INSERT INTO review_tasks (user_id, name, review_type, scope, status, created_at, completed_at) VALUES
    (admin_user_id, 'Example snippet review', 'SNIPPET', 'public class Test {
        public void method(String str) {
            System.out.println(str.length());
        }
    }', 'COMPLETED', 
     CURRENT_TIMESTAMP - INTERVAL '2 days',
     CURRENT_TIMESTAMP - INTERVAL '2 days' + INTERVAL '30 seconds');

    -- Sample 2: file review (running)
    INSERT INTO review_tasks (user_id, name, review_type, scope, status, created_at) VALUES
    (admin_user_id, 'Example file review', 'FILE', '/path/to/example/FileService.java', 'RUNNING',
     CURRENT_TIMESTAMP - INTERVAL '1 hour');

    -- Sample 3: directory review (completed)
    INSERT INTO review_tasks (user_id, name, review_type, scope, status, created_at, completed_at) VALUES
    (admin_user_id, 'Example directory review', 'DIRECTORY', '/path/to/src/main/java', 'COMPLETED',
     CURRENT_TIMESTAMP - INTERVAL '1 day',
     CURRENT_TIMESTAMP - INTERVAL '1 day' + INTERVAL '2 minutes');
END $$;

-- ============================================
-- 2. Insert sample findings
-- ============================================

-- Findings for task 1
INSERT INTO findings (task_id, severity, title, location, start_line, end_line, description, suggestion, category) VALUES
(1, 'HIGH', 'Potential null pointer exception', 'Line 3', 3, 3, 
 'The variable str is used without a null check; passing null causes a NullPointerException',
 'Add a null check before using str: if (str != null) { System.out.println(str.length()); }',
 'BUG'),

(1, 'MEDIUM', 'Code style issue', 'Lines 2-4', 2, 4,
 'The method name "method" is not descriptive; use a more meaningful name',
 'Rename the method to something more descriptive, e.g. printStringLength',
 'CODE_STYLE'),

(1, 'LOW', 'Missing documentation comment', 'Line 2', 2, 2,
 'The public method has no JavaDoc comment',
 'Add a JavaDoc comment describing the behaviour, parameters and return value',
 'MAINTAINABILITY');

-- Findings for task 3
INSERT INTO findings (task_id, severity, title, location, start_line, end_line, description, suggestion, category) VALUES
(3, 'CRITICAL', 'SQL injection risk', 'UserService.java:45', 45, 45,
 'The SQL query is built by string concatenation, creating a SQL injection risk',
 'Use a PreparedStatement or a parameterised query to prevent SQL injection',
 'SECURITY'),

(3, 'HIGH', 'Unclosed resource', 'FileService.java:78', 78, 78,
 'The file stream is not closed after use, which can leak resources',
 'Use try-with-resources so the resource is closed automatically',
 'BUG'),

(3, 'MEDIUM', 'Performance issue', 'DataProcessor.java:120', 120, 125,
 'A database query inside a loop creates an N+1 query problem',
 'Use a batch query or a JOIN to reduce the number of database round trips',
 'PERFORMANCE');

-- ============================================
-- 3. Insert sample reports
-- ============================================

-- Report for task 1
INSERT INTO review_reports (task_id, html_content, markdown_content, statistics, created_at) VALUES
(1, 
 '<!DOCTYPE html><html><head><title>Code Review Report</title></head><body><h1>Code Review Report</h1><p>Task: Example snippet review</p><p>Total issues: 3</p></body></html>',
 '# Code Review Report\n\n**Task**: Example snippet review\n\n**Total issues**: 3\n\n## Findings\n\n1. **Potential null pointer exception** (HIGH)\n2. **Code style issue** (MEDIUM)\n3. **Missing documentation comment** (LOW)',
 '{"total":3,"critical":0,"high":1,"medium":1,"low":1,"categories":{"BUG":1,"CODE_STYLE":1,"MAINTAINABILITY":1}}'::jsonb,
 CURRENT_TIMESTAMP - INTERVAL '2 days');

-- Report for task 3
INSERT INTO review_reports (task_id, html_content, markdown_content, statistics, created_at) VALUES
(3,
 '<!DOCTYPE html><html><head><title>Code Review Report</title></head><body><h1>Code Review Report</h1><p>Task: Example directory review</p><p>Total issues: 3</p></body></html>',
 '# Code Review Report\n\n**Task**: Example directory review\n\n**Total issues**: 3\n\n## Findings\n\n1. **SQL injection risk** (CRITICAL)\n2. **Unclosed resource** (HIGH)\n3. **Performance issue** (MEDIUM)',
 '{"total":3,"critical":1,"high":1,"medium":1,"low":0,"categories":{"SECURITY":1,"BUG":1,"PERFORMANCE":1}}'::jsonb,
 CURRENT_TIMESTAMP - INTERVAL '1 day');

-- ============================================
-- Script complete
-- ============================================

