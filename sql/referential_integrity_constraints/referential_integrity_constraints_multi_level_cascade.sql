-- Examples for FOREIGN KEY CASCADE and SET NULL constraint

DROP TABLE IF EXISTS Apply;
DROP TABLE IF EXISTS Student;
DROP TABLE IF EXISTS College;

CREATE TABLE Student
(
    sID     INT UNSIGNED PRIMARY KEY,
    sName   VARCHAR(255),
    GPA     DECIMAL(8, 2),
    size_HS INT UNSIGNED
);

CREATE TABLE College
(
    cName      VARCHAR(255) PRIMARY KEY,
    state      VARCHAR(255),
    enrollment INT UNSIGNED
);

CREATE TABLE Apply
(
    sID      INT UNSIGNED,
    cName    VARCHAR(255),
    major    VARCHAR(255),
    decision VARCHAR(255),
    CONSTRAINT FOREIGN KEY (sID) REFERENCES Student (sID) ON UPDATE SET NULL,
    CONSTRAINT FOREIGN KEY (cName) REFERENCES College (cName) ON DELETE CASCADE
);

INSERT INTO Student
VALUES (123, 'Amy', 3.9, 1000),
       (234, 'Bob', 3.6, 1500),
       (345, 'Alice', 4.3, 1500),
       (456, 'Technoman', 1.6, 1000);

INSERT INTO College
VALUES ('Stanford', 'CA', 15000),
       ('Oxford', 'X', 3000),
       ('Technoschool', 'Y', 5000),
       ('Berkeley', 'CA', 36000);

INSERT INTO Apply
VALUES (123, 'Stanford', 'CS', 'Y'),
       (456, 'Technoschool', 'CS', 'N');

-- This UPDATE will set the Technoman application to NULL
SELECT *
FROM Apply;

UPDATE Student
SET sID = 666
WHERE sName = 'Technoman';

SELECT *
FROM Apply;

-- This DELETE will also delete the Amy application
SELECT *
FROM Apply;

DELETE
FROM College
WHERE cName = 'Stanford';

SELECT *
FROM Apply;