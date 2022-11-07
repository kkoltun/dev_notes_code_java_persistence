package dev.karolkoltun.persistence.concurrency.isolation.issues;

import dev.karolkoltun.persistence.concurrency.EmptyContext;
import dev.karolkoltun.persistence.concurrency.SessionRunnableWithContext;
import dev.karolkoltun.persistence.concurrency.TwoThreadsWithTransactions;
import dev.karolkoltun.persistence.entity.Employee;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static java.sql.Connection.TRANSACTION_READ_UNCOMMITTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class DirtyReadTest extends PostgresReadUncommittedHrTest {

    private static void updateEmployeeSalary(Session session, int id) {
        session.createQuery("" +
                        "UPDATE Employee " +
                        "SET salary = salary + 100 " +
                        "WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
// TODO DOES NOT WORK
    @Test
    void dirtyReadIsNotPossibleInAnyIsolationLevel() {
        BigDecimal salaryBefore = getUsingHibernate(session -> session.find(Employee.class, 100).getSalary());
        BigDecimal salaryAfter = salaryBefore.add(BigDecimal.valueOf(100));

        SessionRunnableWithContext<EmptyContext> updateEmployeeSalary = (session, context) -> updateEmployeeSalary(session, 100);

        SessionRunnableWithContext<EmptyContext> assertUnchangedSalaryIsSelected = (session, context) -> {
            assertTransactionIsolationLevel(session, TRANSACTION_READ_UNCOMMITTED);

            assertThat(getEmployeeSalaries(session).get(100)).isEqualTo(salaryBefore);
        };

        SessionRunnableWithContext<EmptyContext> assertChangedEmailIsSelected = (session, context) -> {
            assertTransactionIsolationLevel(session, TRANSACTION_READ_UNCOMMITTED);

            // This works.
            assertEquals(salaryAfter, session.doReturningWork(DirtyReadTest::getEmployeeSalaries).get(100));

            // This returns pre-update email. Is this because:
            // * The transaction is pre-update, has READ_UNCOMMITTED but POSTGRES will still prevent unrepeatable read.
            // * The transaction has another transaction isolation level?
            assertEquals(salaryAfter, getEmployeeSalaries(session).get(100));

        };

        TwoThreadsWithTransactions<EmptyContext> twoThreadsWithTransactions = TwoThreadsWithTransactions.configure(entityManagerFactory, EmptyContext::new)
                .threadOneStartsWith(updateEmployeeSalary)
                .thenThreadTwo(assertUnchangedSalaryIsSelected)
                .thenThreadOne((session, context) -> session.getTransaction().commit())
                .thenThreadTwo(assertChangedEmailIsSelected)
                .thenFinish();

        twoThreadsWithTransactions.run();
    }

    private static void setTransactionIsolationLevel(Session session, int isolationLevel) {
        session.doWork(connection -> connection.setTransactionIsolation(isolationLevel));
    }

    private static void assertTransactionIsolationLevel(Session session, int isolationLevel) {
        session.doWork(connection -> {
            assertEquals(isolationLevel, connection.getTransactionIsolation());
        });
    }

    private static Map<Integer, BigDecimal> getEmployeeSalaries(Session session) {
        return session.createQuery("SELECT e FROM Employee e", Employee.class)
                .getResultList()
                .stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getSalary));
    }

    private static Map<Integer, BigDecimal> getEmployeeSalaries(Connection connection) {
        return getEmployeeSalaries(connection, null);
    }

    private static Map<Integer, BigDecimal> getEmployeeSalaries(Connection connection, Integer isolationLevel) {
        try {
            if (isolationLevel != null) {
                connection.setTransactionIsolation(isolationLevel);
            }
            try (PreparedStatement preparedStatement = connection.prepareStatement("" +
                    "SELECT employee_id, salary " +
                    "FROM employees ")) {
                ResultSet resultSet = preparedStatement.executeQuery();
                Map<Integer, BigDecimal> salaries = new HashMap<>();
                while (resultSet.next()) {
                    salaries.put(resultSet.getInt("employee_id"), resultSet.getBigDecimal("salary"));
                }
                return salaries;
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
