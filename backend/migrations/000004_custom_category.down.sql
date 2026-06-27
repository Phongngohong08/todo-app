-- Khôi phục ràng buộc 3 danh mục cố định.
UPDATE tasks SET category = 'OTHER' WHERE category NOT IN ('PERSONAL', 'WORK', 'OTHER');
ALTER TABLE tasks ADD CONSTRAINT tasks_category_check CHECK (category IN ('PERSONAL', 'WORK', 'OTHER'));
ALTER TABLE tasks ALTER COLUMN category TYPE VARCHAR(10);
