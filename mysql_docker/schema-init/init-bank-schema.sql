DROP SCHEMA
    IF EXISTS bank;
CREATE SCHEMA bank COLLATE = utf8_general_ci;

USE bank;

CREATE USER 'bank'@'%' IDENTIFIED WITH mysql_native_password BY 'password';
GRANT ALL PRIVILEGES ON bank.* TO 'bank'@'%';

CREATE TABLE account
(
    iban    CHAR(40)    NOT NULL,
    owner   VARCHAR(40) NOT NULL,
    balance INT(12)     NOT NULL,
    PRIMARY KEY (iban)
);