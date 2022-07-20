-- UNION
SELECT first_name, last_name, hire_date
FROM employees
WHERE YEAR(hire_date) = 1996
UNION
SELECT first_name, last_name, hire_date
FROM employees
WHERE YEAR(hire_date) = 1997;

-- Query nested in WHERE
SELECT *
FROM employees e1
WHERE salary < ALL (SELECT e2.salary FROM employees e2 WHERE e1.employee_id <> e2.employee_id)

-- Query nested in the SELECT clause
SELECT d.department_id,
       d.department_name,
       (SELECT e1.salary
        FROM employees e1
        WHERE e1.department_id = d.department_id
          AND e1.salary > ALL (SELECT e2.salary
                               FROM employees e2
                               WHERE e1.employee_id <> e2.employee_id AND e2.department_id = d.department_id)) AS max_salary
FROM departments d
ORDER BY max_salary DESC;

-- Query nested in the SELECT clause
SELECT d.department_id,
       d.department_name,
       (SELECT MAX(salary) FROM employees e WHERE e.department_id = d.department_id) AS max_salary
FROM departments d
ORDER BY max_salary DESC;

-- ALL usage


-- ANY usage

