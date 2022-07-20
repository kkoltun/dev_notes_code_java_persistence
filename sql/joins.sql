-- INNER JOIN
SELECT *
FROM employees
         INNER JOIN departments ON employees.department_id = departments.department_id;

-- NATURAL JOIN
SELECT *
FROM employees
         NATURAL JOIN departments;

-- INNER JOIN USING (attributes)
SELECT *
FROM employees
         INNER JOIN departments USING (department_id);

-- Multiple joins
-- This does not work on POSTGRES (it requires all JOIN operators to be binary)
SELECT *
FROM employees e
         JOIN departments d
         JOIN locations l ON e.department_id = d.department_id AND d.location_id = l.location_id;

-- This works on POSTGRES
SELECT *
FROM (employees e JOIN departments d on e.department_id = d.department_id)
         JOIN locations l on d.location_id = l.location_id;

-- OUTER JOIN
SELECT department_name, first_name, last_name
FROM departments
         LEFT OUTER JOIN employees ON departments.manager_id = employees.employee_id;

-- Rewrite LEFT OUTER JOIN without it
SELECT department_name, first_name, last_name
FROM departments,
     employees
WHERE departments.manager_id = employees.employee_id
UNION
SELECT department_name, NULL, NULL
FROM departments
WHERE manager_id NOT IN (SELECT employee_id FROM employees)
   OR manager_id IS NULL
