package dev.karolkoltun.persistence.concurrency.isolation.levels;

import dev.karolkoltun.persistence.DataSourceProvider;
import dev.karolkoltun.persistence.HibernateTest;
import dev.karolkoltun.persistence.PostgresqlHrDataSourceProvider;
import dev.karolkoltun.persistence.entity.Employee;
import dev.karolkoltun.persistence.entity.JobId;
import org.hibernate.FlushMode;
import org.hibernate.Session;

import javax.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class PostgresIsolationLevelTest extends HibernateTest {
    abstract int getTestedIsolationLevel();

    @Override
    public Optional<Integer> getIsolationLevel() {
        return Optional.of(getTestedIsolationLevel());
    }

    @Override
    public DataSourceProvider dataSourceProvider() {
        return new PostgresqlHrDataSourceProvider();
    }

    @Override
    public boolean recreateBeforeEachTest() {
        return false;
    }

    protected void assertCorrectTransactionIsolationLevel(Session session) {
        session.doWork(connection -> {
            assertEquals(getTestedIsolationLevel(), connection.getTransactionIsolation());
        });
    }

    protected static BigDecimal getEmployeeSalary(Session session, int id) {
        return session.createQuery("SELECT e.salary FROM Employee e WHERE e.id = :id", BigDecimal.class)
                .setParameter("id", id)
                .getSingleResult();
    }

    protected static void increaseEmployeeSalary(Session session, int id) {
        session.createQuery("" +
                        "UPDATE Employee " +
                        "SET salary = salary + 100 " +
                        "WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }

    protected static <T> void commit(Session session, T context) {
        session.getTransaction().commit();
    }

    protected void assertSelectedSalary(Session session, int employeeId, BigDecimal expectedSalary) {
        // Make sure the transaction isolation level is correct first.
        assertCorrectTransactionIsolationLevel(session);

        assertThat(getEmployeeSalary(session, employeeId)).isEqualTo(expectedSalary);
    }

    protected int addExampleEmployee() {
        return getUsingHibernate(session -> {
            // Lock table first.
            session.createQuery("SELECT e FROM Employee e")
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getResultList();

            int availableId = session.createQuery("SELECT MAX(e.id) + 1 FROM Employee e", Integer.class)
                    .getSingleResult();

            Employee employee = new Employee();

            employee.setId(availableId);
            employee.setFirstName("Example");
            employee.setLastName("Employee");
            employee.setEmail("eemp_" + availableId + "@example.com");
            employee.setHireDate(LocalDate.now());
            employee.setJobId(JobId.FI_ACCOUNT);
            employee.setPhone("1234" + availableId);
            employee.setSalary(BigDecimal.valueOf(5000));

            session.persist(employee);

            return availableId;
        }, false, FlushMode.AUTO);
    }
}
