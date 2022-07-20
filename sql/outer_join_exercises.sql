-- Postgres only; exercises with some simple data to do on paper and then check with the DB.

DROP SCHEMA
    IF EXISTS oj_ex;
CREATE SCHEMA oj_ex;

DROP TABLE IF EXISTS oj_ex.emp;
DROP TABLE IF EXISTS oj_ex.dept;
DROP TABLE IF EXISTS oj_ex.loc;

CREATE TABLE oj_ex.emp
(
    emp_id   INT          NOT NULL,
    emp_name VARCHAR(255) NOT NULL,
    dept_id  INT,
    PRIMARY KEY (emp_id)
);

CREATE TABLE oj_ex.dept
(
    dept_id     INT          NOT NULL,
    loc_id      INT,
    dept_name   VARCHAR(255) NOT NULL,
    branch_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (dept_id)
);

CREATE TABLE oj_ex.loc
(
    loc_id  INT          NOT NULL,
    address VARCHAR(255) NOT NULL,
    PRIMARY KEY (loc_id)
);

INSERT INTO oj_ex.emp
VALUES (1, 'Ann', 1),
       (2, 'Marie', 2),
       (3, 'Kate', NULL);

INSERT INTO oj_ex.dept
VALUES (1, 1, 'Orion', 'Marketing'),
       (2, 1, 'Theta', 'Accounting'),
       (3, 2, 'Alpha', 'Security'),
       (4, NULL, 'Ikar', 'IT');

INSERT INTO oj_ex.loc
VALUES (1, 'Warsaw'),
       (2, 'Lublin'),
       (3, 'Olsztyn');

SELECT emp_id, emp_name, dept_id, loc_id, dept_name, branch_name
FROM oj_ex.emp
         NATURAL FULL OUTER JOIN oj_ex.dept;

SELECT emp_id, emp_name, dept_id, loc_id, dept_name, branch_name, address
FROM (oj_ex.emp NATURAL FULL OUTER JOIN oj_ex.dept)
         NATURAL FULL OUTER JOIN oj_ex.loc;

SELECT emp_id, emp_name, dept_id, loc_id, dept_name, branch_name, address
FROM oj_ex.emp NATURAL FULL OUTER JOIN 
    (oj_ex.dept NATURAL FULL OUTER JOIN oj_ex.loc);

