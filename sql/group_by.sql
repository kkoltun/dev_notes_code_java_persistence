-- Get the MIN and MAX salary in each department.
SELECT department_name, MIN(salary), MAX(salary)
FROM employees,
     departments
WHERE employees.department_id = departments.department_id
GROUP BY department_name;

-- Get the departments with more than 20 employees.
SELECT departments.department_id, department_name
FROM employees,
     departments
WHERE employees.department_id = departments.department_id
GROUP BY departments.department_id
HAVING COUNT(*) > 20;

-- Get the departments that have max salary lower than the average.
SELECT departments.department_id, department_name
FROM employees,
     departments
WHERE employees.department_id = departments.department_id
GROUP BY departments.department_id
HAVING MAX(salary) < (SELECT AVG(salary) FROM employees);