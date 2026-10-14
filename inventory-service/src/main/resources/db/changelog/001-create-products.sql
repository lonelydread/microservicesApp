--liquibase formatted sql

--changeset inventory-service:001-create-products dbms:postgresql
CREATE TABLE products (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    name VARCHAR(255),
    description VARCHAR(255),
    price NUMERIC(19, 2),
    quantity INTEGER
);

--rollback DROP TABLE products;
