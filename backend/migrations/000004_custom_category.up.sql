-- Cho phép danh mục tự do (người dùng tự thêm), thay vì chỉ 3 giá trị cố định.
ALTER TABLE tasks ALTER COLUMN category TYPE VARCHAR(50);
ALTER TABLE tasks DROP CONSTRAINT IF EXISTS tasks_category_check;
