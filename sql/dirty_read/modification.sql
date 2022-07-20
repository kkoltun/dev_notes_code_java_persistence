DELIMITER $$

DROP PROCEDURE IF EXISTS ModifySalaries $$

CREATE PROCEDURE ModifySalaries(repeats INT)
BEGIN
    DECLARE counter INT DEFAULT 1;

    WHILE counter <= repeats DO
            UPDATE employees SET salary = 1000 WHERE 1 = 1;
            UPDATE employees SET salary = 2000 WHERE 1 = 1;
            SET counter = counter + 1;
        END WHILE;

END$$

DELIMITER ;

START TRANSACTION;
CALL ModifySalaries(10000);
COMMIT;