package com.hr;

import java.util.Set;

public class EmployeeService {
  private EmployeeRepository employeeRepository;

  public EmployeeService() {
    this.employeeRepository = new EmployeeRepository();
  }

  Set<Employee> getAll() {
    return employeeRepository.findAll();
  }

  void setManager(int employeeId, int newManagerId) {
    Employee employee = employeeRepository.getById(employeeId);
    Employee manager = employeeRepository.getById(newManagerId);

    if (employee.getSalary().compareTo(manager.getSalary()) > 0) {
      throw new BusinessException("Could not set new manager to employer with higher salary.");
    }


  }
}
