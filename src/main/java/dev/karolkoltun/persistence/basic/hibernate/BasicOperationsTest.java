package dev.karolkoltun.persistence.basic.hibernate;

import dev.karolkoltun.persistence.DataSourceProvider;
import dev.karolkoltun.persistence.entity.Employee;
import dev.karolkoltun.persistence.HibernateTest;
import dev.karolkoltun.persistence.PostgresqlHrDataSourceProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicOperationsTest extends HibernateTest {

    @Override
    public DataSourceProvider dataSourceProvider() {
        return new PostgresqlHrDataSourceProvider();
    }

    @Override
    public boolean recreateBeforeEachTest() {
        return false;
    }

    @Test
    void shouldGetEmployee() {
        // WHEN
        List<Employee> employees = getUsingHibernateReadOnly(session -> session.createQuery("" +
                        "SELECT emp " +
                        "FROM Employee emp " +
                        "WHERE emp.firstName = :firstName" +
                        "  AND emp.lastName = :lastName ", Employee.class)
                .setParameter("firstName", "Lex")
                .setParameter("lastName", "De Haan")
                .list());

        // THEN
        assertThat(employees).hasSize(1);
    }

    @Test
    void namedQueryShouldGetEmployee() {
        // WHEN
        List<Employee> employees = getUsingHibernateReadOnly(session -> session.createNamedQuery("get_employee_by_first_name_and_last_name", Employee.class)
                .setParameter("firstName", "Lex")
                .setParameter("lastName", "De Haan")
                .list());

        // THEN
        assertThat(employees).hasSize(1);
    }
}
