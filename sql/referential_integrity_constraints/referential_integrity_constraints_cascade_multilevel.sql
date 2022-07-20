-- Examples for the multi-level CASCADE constraints

DROP TABLE IF EXISTS ApplyDetail;
DROP TABLE IF EXISTS Apply;
DROP TABLE IF EXISTS College;

CREATE TABLE College
(
    cName      VARCHAR(255) PRIMARY KEY,
    state      VARCHAR(255),
    enrollment INT UNSIGNED
);

CREATE TABLE Apply
(
    aID      INT UNSIGNED PRIMARY KEY,
    sID      INT UNSIGNED,
    cName    VARCHAR(255),
    major    VARCHAR(255),
    decision VARCHAR(255),
    CONSTRAINT FOREIGN KEY (cName) REFERENCES College (cName) ON DELETE CASCADE
);

CREATE TABLE ApplyDetail
(
    aID      INT UNSIGNED PRIMARY KEY,
    reviewerName    VARCHAR(255),
    reviewerComment    VARCHAR(255),
    CONSTRAINT FOREIGN KEY (aID) REFERENCES Apply (aID) ON DELETE CASCADE
);

INSERT INTO College
VALUES ('Stanford', 'CA', 15000),
       ('Oxford', 'X', 3000);

INSERT INTO Apply
VALUES (1, 123, 'Stanford', 'CS', 'Y');

INSERT INTO ApplyDetail
VALUES (1, 'Mark', 'Super!');

-- This will delete all the application data linked with the college
SELECT *
FROM ApplyDetail;

DELETE FROM College
WHERE cName = 'Stanford';

SELECT *
FROM ApplyDetail;
