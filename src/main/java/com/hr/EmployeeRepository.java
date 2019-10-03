package com.hr;

import java.sql.*;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

class EmployeeRepository {
  // todo keeping connection and abstracting database in JDBC
  // todo keep database settings in properties?

  // Database details
  private static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
  private static final String DB_URL =
      "jdbc:mysql://localhost/hr?useUnicode=true&useJDBCCompliantTimezoneShift=true&serverTimezone=UTC";

  // Account details
  private static final String USER = "hr";
  private static final String PASSWORD = "hr";

  // Sql
  private static final String getByFirstNameSql =
      "SELECT employee_id, first_name, last_name, email, phone_number FROM employees WHERE first_name = ?";
  private static final String getByLastNameSql =
      "SELECT employee_id, first_name, last_name, email, phone_number FROM employees WHERE last_name = ?";

  private static Connection connection;
  private static PreparedStatement getByFirstName;
  private static PreparedStatement getByLastName;

  EmployeeRepository() {
    connect();
    prepareStatements();
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

  public Optional<Employee> getById(int id) {
    try (Statement statement = connection.createStatement()) {
      ResultSet resultSet =
          statement.executeQuery(
              String.format(
                  "SELECT EMPLOYEE_ID, FIRST_NAME, LAST_NAME, EMAIL, PHONE_NUMBER FROM employees WHERE EMPLOYEE_ID = '%d'",
                  id));

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

  public void changePhoneNumber(int id, String newPhoneNumber) {
    // mozna by bylo normalnym statementem to opedzic
    try (PreparedStatement preparedStatement =
        connection.prepareStatement("UPDATE employees SET PHONE_NUMBER=? WHERE EMPLOYEE_ID=?")) {
      preparedStatement.setString(1, newPhoneNumber);
      preparedStatement.setInt(2, id);

      int affectedRows = preparedStatement.executeUpdate();
      System.out.println("Affected rows: " + affectedRows);
    } catch (SQLException exception) {
      throw new RepositoryException("Could not execute update", exception);
    }
  }

  private static void prepareStatements() {
    try {
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

  private static void connect() {
    if (connection != null) {
      return;
    }

    try {
      // Register driver
      Class.forName(JDBC_DRIVER);

      connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
    } catch (ClassNotFoundException e) {
      throw new RepositoryException("Driver has not been found", e);
    } catch (SQLException e) {
      throw new RepositoryException("Could not connect", e);
    }
  }

  private static Employee employeeFromResultSet(ResultSet resultSet) throws SQLException {
    return new Employee(
        resultSet.getInt("EMPLOYEE_ID"),
        resultSet.getString("FIRST_NAME"),
        resultSet.getString("LAST_NAME"),
        resultSet.getString("EMAIL"),
        resultSet.getString("PHONE_NUMBER"));
  }
}
