package dev.karolkoltun.persistence.jdbc;

import dev.karolkoltun.persistence.entity.Employee;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static java.lang.String.format;
import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BasicOperationsTest extends JdbcTest {
    // Sql
    private static final String getByIdSql = "SELECT * FROM employees WHERE employee_id = '%d'";
    private static final String getByFirstNameSql = "SELECT * FROM employees WHERE first_name = ?";

    @Test
    void shouldFindEmployeeByIdUsingPlainStatement() throws Exception {
        // GIVEN
        int id = 100;
        String expectedFirstName = "Steven";
        String expectedLastName = "King";

        // WHEN
        Optional<Employee> employee;
        try (Connection connection = getConnection();
                Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery(format(getByIdSql, id));

            employee = resultSet.next() ? Optional.of(QueryUtils.employeeFromResultSet(resultSet)) : empty();
        }

        // THEN
        assertThat(employee).isPresent();
        assertThat(employee.get().getFirstName()).isEqualTo(expectedFirstName);
        assertThat(employee.get().getLastName()).isEqualTo(expectedLastName);
    }

    @Test
    void findEmployeesByFirstNameUsingPreparedStatement() throws Exception {
        // GIVEN
        String firstNameToLookFor = "David";
        int expectedResultSetLength = 3;

        // WHEN
        Set<Employee> employees = new HashSet<>();
        try (PreparedStatement getByFirstName = getConnection().prepareStatement(getByFirstNameSql)) {
            getByFirstName.setString(1, firstNameToLookFor);
            ResultSet resultSet = getByFirstName.executeQuery();

            while (resultSet.next()) {
                employees.add(QueryUtils.employeeFromResultSet(resultSet));
            }

            resultSet.close();
        }

        // THEN
        assertEquals(expectedResultSetLength, employees.size());
    }
}
