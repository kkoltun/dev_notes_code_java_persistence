-- Postgres only; this shows that OUTER JOIN is not comutative.

DROP SCHEMA
    IF EXISTS oj_ac CASCADE;
CREATE SCHEMA oj_ac;

DROP TABLE IF EXISTS oj_ac.T1;
DROP TABLE IF EXISTS oj_ac.T2;
DROP TABLE IF EXISTS oj_ac.T3;

CREATE TABLE oj_ac.T1
(
    A INT,
    B INT
);
CREATE TABLE oj_ac.T2
(
    B INT,
    C INT
);
CREATE TABLE oj_ac.T3
(
    A INT,
    C INT
);

INSERT INTO oj_ac.T1
VALUES (1, 2);
INSERT INTO oj_ac.T2
VALUES (2, 3);
INSERT INTO oj_ac.T3
VALUES (4, 5);

-- This way there are other results...
SELECT a, b, c
FROM (oj_ac.T1 NATURAL FULL OUTER JOIN oj_ac.T2)
         NATURAL FULL OUTER JOIN oj_ac.T3;

-- ... than this way.
SELECT a, b, c
FROM oj_ac.T1 NATURAL FULL OUTER JOIN
    (oj_ac.T2 NATURAL FULL OUTER JOIN oj_ac.T3);

-- This is because in case 2, we started with unmatched couples that then did not match T1.