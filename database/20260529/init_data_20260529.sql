-- ============================================
-- CodeGuardian AI - sample data seed script
-- Version: v2.1.0
-- Date: 2026-05-29
-- Notes: matches the current entity enum values (ReviewType/TaskStatus/Severity are integers)
-- ============================================

DO $$
DECLARE
  admin_user_id BIGINT;
BEGIN
  SELECT id INTO admin_user_id FROM users WHERE username = 'admin' LIMIT 1;
  IF admin_user_id IS NULL THEN
    RAISE EXCEPTION 'Admin user not found; run init_permissions_20260529.sql first';
  END IF;

  -- 1) Sample review tasks
  -- SNIPPET=3, FILE=2, DIRECTORY=1; COMPLETED=2, RUNNING=1
  INSERT INTO review_tasks (name, review_type, scope, status, created_at, completed_at)
  VALUES
    ('Example snippet review', 3, 'public class Test {
        public void method(String str) {
            System.out.println(str.length());
        }
    }', 2, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days' + INTERVAL '30 seconds'),
    ('Example file review',      2, '/path/to/example/FileService.java', 1, CURRENT_TIMESTAMP - INTERVAL '1 hour', NULL),
    ('Example directory review',      1, '/path/to/src/main/java', 2, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '1 day' + INTERVAL '2 minutes');

  -- 2) Sample findings
  -- severity: CRITICAL=0, HIGH=1, MEDIUM=2, LOW=3
  INSERT INTO findings (task_id, severity, title, location, start_line, end_line, description, suggestion, category, source)
  VALUES
    (1, 1, 'Potential null pointer exception', 'Line 3', 3, 3,
     'The variable str is used without a null check; passing null causes a NullPointerException',
     'Add a null check before using str: if (str != null) { System.out.println(str.length()); }',
     'BUG', 'AI'),
    (1, 2, 'Code style issue', 'Lines 2-4', 2, 4,
     'The method name "method" is not descriptive; use a more meaningful name',
     'Rename the method to something more descriptive, e.g. printStringLength',
     'CODE_STYLE', 'RuleEngine'),
    (1, 3, 'Missing documentation comment', 'Line 2', 2, 2,
     'The public method has no JavaDoc comment',
     'Add a JavaDoc comment describing the behaviour, parameters and return value',
     'MAINTAINABILITY', 'AI'),
    (3, 0, 'SQL injection risk', 'UserService.java:45', 45, 45,
     'The SQL query is built by string concatenation, creating a SQL injection risk',
     'Use a PreparedStatement or a parameterised query to prevent SQL injection',
     'SECURITY', 'Semgrep'),
    (3, 1, 'Unclosed resource', 'FileService.java:78', 78, 78,
     'The file stream is not closed after use, which can leak resources',
     'Use try-with-resources so the resource is closed automatically',
     'BUG', 'AI'),
    (3, 2, 'Performance issue', 'DataProcessor.java:120', 120, 125,
     'A database query inside a loop creates an N+1 query problem',
     'Use a batch query or a JOIN to reduce the number of database round trips',
     'PERFORMANCE', 'AI');

  -- 3) Sample reports
  INSERT INTO review_reports (task_id, html_content, markdown_content, statistics, created_at)
  VALUES
    (1,
     '<!DOCTYPE html><html><head><title>Code Review Report</title></head><body><h1>Code Review Report</h1><p>Task: Example snippet review</p><p>Total issues: 3</p></body></html>',
     '# Code Review Report

**Task**: Example snippet review

**Total issues**: 3

## Findings

1. **Potential null pointer exception** (HIGH)
2. **Code style issue** (MEDIUM)
3. **Missing documentation comment** (LOW)',
     '{"total":3,"critical":0,"high":1,"medium":1,"low":1,"categories":{"BUG":1,"CODE_STYLE":1,"MAINTAINABILITY":1}}',
     CURRENT_TIMESTAMP - INTERVAL '2 days'),
    (3,
     '<!DOCTYPE html><html><head><title>Code Review Report</title></head><body><h1>Code Review Report</h1><p>Task: Example directory review</p><p>Total issues: 3</p></body></html>',
     '# Code Review Report

**Task**: Example directory review

**Total issues**: 3

## Findings

1. **SQL injection risk** (CRITICAL)
2. **Unclosed resource** (HIGH)
3. **Performance issue** (MEDIUM)',
     '{"total":3,"critical":1,"high":1,"medium":1,"low":0,"categories":{"SECURITY":1,"BUG":1,"PERFORMANCE":1}}',
     CURRENT_TIMESTAMP - INTERVAL '1 day');
END $$;

-- Done
