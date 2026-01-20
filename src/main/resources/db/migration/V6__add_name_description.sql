-- 为 react_agent 表添加 name 和 description 字段，并设置默认值以避免迁移失败

-- 首先添加字段，允许为空
ALTER TABLE react_agent
    ADD COLUMN IF NOT EXISTS description VARCHAR(255);

ALTER TABLE react_agent
    ADD COLUMN IF NOT EXISTS name VARCHAR(100);

-- 为已存在的记录设置默认值
UPDATE react_agent
SET description = COALESCE(description, 'Default Agent Description'),
    name = COALESCE(name, 'Unnamed Agent')
WHERE description IS NULL OR name IS NULL;

-- 设置字段为 NOT NULL
ALTER TABLE react_agent
    ALTER COLUMN description SET NOT NULL;

ALTER TABLE react_agent
    ALTER COLUMN name SET NOT NULL;