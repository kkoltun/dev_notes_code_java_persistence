DROP SCHEMA
    IF EXISTS forum;
CREATE SCHEMA forum COLLATE = utf8_general_ci;

USE forum;

CREATE USER 'forum'@'%' IDENTIFIED WITH mysql_native_password BY 'password';
GRANT ALL PRIVILEGES ON forum.* TO 'forum'@'%';

CREATE TABLE post
(
    id    INT (11) PRIMARY KEY,
    title VARCHAR(255)
);

CREATE TABLE post_comment
(
    id      INT (11) PRIMARY KEY,
    review  VARCHAR(255),
    post_id INT (11),
    CONSTRAINT FOREIGN KEY (post_id) REFERENCES post (id)
);