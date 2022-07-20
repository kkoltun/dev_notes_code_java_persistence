DROP TABLE IF EXISTS location_rent;

CREATE TABLE location_rent
(
    rent_id     INT(11) UNSIGNED NOT NULL,
    location_id INT(11) UNSIGNED UNIQUE,
    owner_name  VARCHAR(30)      NOT NULL,
    rent_amount DECIMAL(8, 2)    NOT NULL CHECK (rent_amount > 0 AND rent_amount < 1000000),
    PRIMARY KEY (rent_id),
    CHECK (owner_name != 'Karol' OR rent_amount > 5000)
);

INSERT INTO location_rent (rent_id, location_id, owner_name, rent_amount)
VALUES (1, NULL, 'Tom', 2500),
       (2, NULL, 'Amy', 4000);

-- Disallowed - if renting from Karol, rent_amount must be higher than 5000
INSERT INTO location_rent (rent_id, location_id, owner_name, rent_amount)
VALUES (1, 1, 'Karol', 1000);

-- Disallowed - negative amount
INSERT INTO location_rent (rent_id, location_id, owner_name, rent_amount)
VALUES (2, 2, 'Mark', -10);

-- Disallowed - duplicate location_id
INSERT INTO location_rent (rent_id, location_id, owner_name, rent_amount)
VALUES (3, 1, 'Lawrence', 100);

