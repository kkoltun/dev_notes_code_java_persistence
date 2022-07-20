-- Examples for FOREIGN KEY RESTRICT constraint

DROP TABLE IF EXISTS Apply;
DROP TABLE IF EXISTS Student;
DROP TABLE IF EXISTS College;

CREATE TABLE Student
(
    sID     INT UNSIGNED PRIMARY KEY,
    sname   VARCHAR(255),
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
    CONSTRAINT FOREIGN KEY (cName) REFERENCES College (cName)
);

-- INSERTS
-- Disallowed
INSERT INTO Apply
VALUES (123, 'Cambridge', 'CS', 'Y');

-- Allowed
INSERT INTO Student
VALUES (123, 'Amy', 3.9, 1000),
       (234, 'Bob', 3.6, 1500);

INSERT INTO College
VALUES ('Stanford', 'CA', 15000),
       ('Berkeley', 'CA', 36000);

INSERT INTO Apply
VALUES (123, 'Stanford', 'CS', 'Y');

-- UPDATES
-- Disallowed
UPDATE Apply
SET sID = 345
WHERE sID = 123;

UPDATE College
SET cName = 'Stanford'
WHERE cName = 'Ztanford';

-- Allowed
UPDATE Apply
SET cName = 'Berkeley'
WHERE cName = 'Stanford';

-- DELETES
-- Allowed
DELETE
FROM Student
WHERE sID = 345;

-- Disallowed
DELETE
FROM Student
WHERE sID = 123;

