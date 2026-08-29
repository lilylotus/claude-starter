ALTER TABLE tab_dict_item
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1 COMMENT '面向外部同步消费者的实体版本' AFTER status;

UPDATE tab_dict_item
SET version = 1
WHERE version IS NULL OR version < 1;
