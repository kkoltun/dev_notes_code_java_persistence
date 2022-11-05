package dev.karolkoltun.persistence;

import java.math.BigDecimal;

import static java.math.BigDecimal.*;

public class PayrollService {
  /**
   * Checks the following rules:
   * 1. No salary reductions are allowed.
   * 2. No salary increases by more than 10 percent are allowed.
   *
   * @param employee employee to update salary
   * @param updateValue value of the salary update
   *
   * @return is the update allowed
   */
  public boolean isSalaryUpdateAllowed(Employee employee, BigDecimal updateValue) {
    if (updateValue.compareTo(ZERO) < 0) {
      return false;
    }

    long currentSalary = employee.getSalary().longValue();
    long update = updateValue.longValue();

    return update * 100/currentSalary < 10;
  }
}
