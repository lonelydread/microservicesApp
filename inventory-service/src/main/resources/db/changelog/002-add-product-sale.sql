--liquibase formatted sql

--changeset inventory-service:002-add-product-sale dbms:postgresql
ALTER TABLE products
    ADD COLUMN sale NUMERIC(5, 2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_products_sale CHECK (sale >= 0 AND sale <= 100);

--rollback ALTER TABLE products DROP CONSTRAINT chk_products_sale;
--rollback ALTER TABLE products DROP COLUMN sale;
