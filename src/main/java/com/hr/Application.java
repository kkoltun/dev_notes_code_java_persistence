package com.hr;

import java.util.Optional;
import java.util.Set;

public class Application {
  public static void main(String[] args) {
    EmployeeRepository employeeRepository = new EmployeeRepository();

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
