package com.hr;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static java.lang.String.format;

class EmployeeRepositoryJdbc {
  // Sql
  private static final String getAllSql =
      "SELECT employee_id, first_name, last_name, email, phone_number FROM employees";
  private static final String getByIdSql =
      "SELECT EMPLOYEE_ID, FIRST_NAME, LAST_NAME, EMAIL, PHONE_NUMBER FROM employees WHERE EMPLOYEE_ID = '%d'";
  private static final String getByFirstNameSql =
      "SELECT employee_id, first_name, last_name, email, phone_number FROM employees WHERE first_name = ?";
  private static final String getByLastNameSql =
      "SELECT employee_id, first_name, last_name, email, phone_number FROM employees WHERE last_name = ?";
  private static final String insertEmployeeSql =
      "INSERT INTO employees (first_name, last_name, email, phone_number, hire_date, job_id, salary) values (?,?,?,?,?,?,?)";

  private HikariDataSource hikariDataSource;
  private static PreparedStatement getByFirstName;
  private static PreparedStatement getByLastName;

  EmployeeRepositoryJdbc() {
    HikariConfig hikariConfig = new HikariConfig("hikari.properties");
    hikariDataSource = new HikariDataSource(hikariConfig);

    prepareStatements();
  }

  Set<Employee> findAll() {
    try (Connection connection = hikariDataSource.getConnection();
        Statement statement = connection.createStatement()) {
      ResultSet resultSet = statement.executeQuery(getAllSql);

      Set<Employee> employees = new HashSet<>();
      while (resultSet.next()) {
        employees.add(employeeFromResultSet(resultSet));
      }

      resultSet.close();

      return employees;
    } catch (SQLException e) {
      throw new RepositoryException("Error getting employees.", e);
    }
  }

  Set<Employee> findEmployeesByFirstName(String firstName) {
    try {
      getByFirstName.setString(1, firstName);
      ResultSet resultSet = getByFirstName.executeQuery();

      Set<Employee> employees = new HashSet<>();
      while (resultSet.next()) {
        employees.add(employeeFromResultSet(resultSet));
      }

      resultSet.close();

      return employees;
    } catch (SQLException e) {
      throw new RepositoryException("Error getting employees.", e);
    }
  }

  Optional<Employee> findById(int id) {
    try (Connection connection = hikariDataSource.getConnection();
        Statement statement = connection.createStatement()) {
      ResultSet resultSet = statement.executeQuery(format(getByIdSql, id));

      Optional<Employee> employee;
      if (resultSet.next()) {
        employee = Optional.of(employeeFromResultSet(resultSet));
      } else {
        employee = Optional.empty();
      }

      return employee;
    } catch (SQLException exception) {
      throw new RepositoryException("Could not get employee by id", exception);
    }
  }

  Employee getById(int id) {
    return findById(id).orElseThrow(() -> new RepositoryException("Could not find employee by id"));
  }

  void persist(Employee employee) {
    try (Connection connection = hikariDataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(insertEmployeeSql)) {
      statement.setString(1, employee.getFirstName());
      statement.setString(2, employee.getLastName());
      statement.setString(3, employee.getEmail());
      statement.setString(4, employee.getPhone());
      statement.setDate(5, employee.getHireDate());
      statement.setString(6, employee.getJobId().toString());
      statement.setBigDecimal(7, employee.getSalary());

      statement.executeUpdate();

    } catch (SQLException exception) {
      throw new RepositoryException("Could not execute persist", exception);
    }
  }

  void changePhoneNumber(int id, String newPhoneNumber) {
    // mozna by bylo normalnym statementem to opedzic
    try (Connection connection = hikariDataSource.getConnection();
        PreparedStatement preparedStatement =
            connection.prepareStatement(
                "UPDATE employees SET PHONE_NUMBER=? WHERE EMPLOYEE_ID=?")) {
      preparedStatement.setString(1, newPhoneNumber);
      preparedStatement.setInt(2, id);

      int affectedRows = preparedStatement.executeUpdate();
      System.out.println("Affected rows: " + affectedRows);
    } catch (SQLException exception) {
      throw new RepositoryException("Could not execute update", exception);
    }
  }

  private void prepareStatements() {
    try {
      Connection connection = hikariDataSource.getConnection();
      getByFirstName = connection.prepareStatement(getByFirstNameSql);
      getByLastName = connection.prepareStatement(getByLastNameSql);
    } catch (SQLException exception) {
      try {
        if (getByFirstName != null) {
          getByFirstName.close();
        }
        if (getByLastName != null) {
          getByLastName.close();
        }

        throw new RepositoryException("Error preparing statement", exception);
      } catch (SQLException inner_exception) {
        throw new RepositoryException(
            "Error preparing statement; could not close it", inner_exception);
      }
    }
  }

  private static Employee employeeFromResultSet(ResultSet resultSet) throws SQLException {
    return new Employee(
        resultSet.getInt("EMPLOYEE_ID"),
        resultSet.getString("FIRST_NAME"),
        resultSet.getString("LAST_NAME"),
        resultSet.getString("EMAIL"),
        resultSet.getString("PHONE_NUMBER"),
        resultSet.getDate("HIRE_DATE"),
        JobId.valueOf(resultSet.getString("JOB_ID")),
        resultSet.getBigDecimal("SALARY"));
  }
}
