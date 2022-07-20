### Scripts here

`modification.sql`

The `ModifySalaries` procedure updates all the employee salaries in a loop.
In each iteration, it sets all salaries to 1000 and then to 2000.
So the salaries of all employees change from 1000 to 2000 and then back to 1000 in a loop, for example 10,000 times.

`reading.sql`

Small query that checks the average salary of all the employees.

### How to see the dirty read

1. Start the execution of the `modification.sql` with a sufficiently large number of repeats.
Just leave it running.
The isolation level does not matter here.
2. Now (the loop from pt. 1 is running) open the `reading.sql`.
In another session, with `READ UNCOMMITTED` isolation mode, start checking the average salary.

Values returned by the average query:
* `1000` and `2000`: This means that the query can actually see the uncommitted modifications done by the modification script.
* Values other than `1000` and `2000`: This means that the query can also get the salaries from the table in an inconsistent state. 
Such average is produced only when only a subset of the employees got the new value and the table is still being updated.