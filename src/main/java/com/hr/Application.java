package com.hr;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Application {
  public static void main(String[] args) throws Exception {
    checkHibernate();
  }

  private static void checkHibernate() throws Exception {
    EmployeeRepositoryHibernate employeeRepository = new EmployeeRepositoryHibernate();

//    employeeRepository.addNewEmployee("Ala", "Mala", "amala");
    List<Employee> employees = employeeRepository.getEmployees();

    employees.forEach(System.out::println);

    employeeRepository.alterEmployee();

    employeeRepository.close();
  }

  private static void checkJdbc() {
    EmployeeRepositoryJdbc employeeRepository = new EmployeeRepositoryJdbc();

    Set<Employee> employees = employeeRepository.findEmployeesByFirstName("David");
    employees.forEach(System.out::println);

    employees = employeeRepository.findEmployeesByFirstName("Alexander");
    employees.forEach(System.out::println);

    Optional<Employee> employee = employeeRepository.findById(105);
    System.out.println(employee);

    employee = employeeRepository.findById(1);
    System.out.println(employee);

    employeeRepository.changePhoneNumber(105, "777");
    employee = employeeRepository.findById(105);
    System.out.println(employee);
  }
}
