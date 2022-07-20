DROP TABLE IF EXISTS T;

CREATE TABLE T
(
    A INT,
    B INT,
    C INT,
    PRIMARY KEY (A, B),
    FOREIGN KEY (B, C) REFERENCES T (A, B) ON DELETE CASCADE
);

INSERT INTO T (A, B, C)
VALUES (1, 1, 1),
       (2, 1, 1),
       (3, 2, 1),
       (4, 3, 2),
       (5, 4, 3),
       (6, 5, 4),
       (7, 6, 5),
       (8, 7, 6),
       (9, 8, 7);

-- This deletes only one row, because (9, 8) pair is not referenced in any other row (B, C) pair.
SELECT * FROM T;

DELETE FROM T WHERE A = 9;

SELECT * FROM T;

-- This deletes all remaining rows, because (1, 1) pair is referenced by the second row (B, C) pair, where (2, 1) pair is referenced by third row...
SELECT * FROM T;

DELETE FROM T WHERE A = 1;

SELECT * FROM T;