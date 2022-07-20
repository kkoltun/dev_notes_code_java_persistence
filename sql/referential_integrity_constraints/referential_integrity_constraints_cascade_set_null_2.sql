-- Examples for FOREIGN KEY CASCADE and SET NULL constraint; especially UPDATE CASCADE

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
    CONSTRAINT FOREIGN KEY (sID) REFERENCES Student (sID),
    CONSTRAINT FOREIGN KEY (cName) REFERENCES College (cName) ON UPDATE CASCADE ON DELETE SET NULL
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

-- This UPDATE will set the Technoman application college name too
SELECT *
FROM Apply
WHERE sID = 456;

UPDATE College
SET cName = 'Super Technical School v2'
WHERE cName = 'Technoschool';

SELECT *
FROM Apply
WHERE sID = 456;

-- This DELETE will set Amy application college name to NULL
SELECT *
FROM Apply
WHERE sID = 123;

DELETE
FROM College
WHERE cName = 'Stanford';

SELECT *
FROM Apply
WHERE sID = 123;

-- This constraint would be conflicting with the ON DELETE SET NULL, not allowed by the database

ALTER TABLE Apply MODIFY cName VARCHAR(255) NOT NULL;