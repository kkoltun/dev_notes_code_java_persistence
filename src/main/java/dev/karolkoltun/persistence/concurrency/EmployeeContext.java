package dev.karolkoltun.persistence.concurrency;

import dev.karolkoltun.persistence.entity.Employee;

public class EmployeeContext {
    private Employee employee;

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }
}
