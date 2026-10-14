--liquibase formatted sql

--changeset order-service:002-create-user-roles
CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role VARCHAR(255) NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_user_roles_role CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN'))
);

--rollback DROP TABLE user_roles;
