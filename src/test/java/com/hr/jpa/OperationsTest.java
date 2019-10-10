package com.hr.jpa;

import com.hr.Employee;
import com.hr.JobId;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.transaction.UserTransaction;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;

import static java.time.LocalDate.now;
import static java.util.Collections.emptyList;

// TODO test different operations
class OperationsTest extends JpaTest {

  @Test
  void addNewEmployee(String firstName, String lastName, String email) {
    EntityManager entityManager = JPA.createEntityManager();

    try {
      UserTransaction transaction = TM.getUserTransaction();

      transaction.begin();

      Employee employee = new Employee();
      employee.setId(12345);
      employee.setFirstName(firstName);
      employee.setLastName(lastName);
      employee.setEmail(email);
      employee.setHireDate(Date.valueOf(now()));
      employee.setJobId(JobId.FI_ACCOUNT);
      employee.setPhone("1234");
      employee.setSalary(BigDecimal.valueOf(12345));

      entityManager.persist(employee);

      transaction.commit();
    } catch (Exception e) {
      e.printStackTrace();
      try {
        TM.getUserTransaction().rollback();
      } catch (Exception rollbackException) {
        System.err.println("Rollback of transaction failed, trace follows!");
        rollbackException.printStackTrace(System.err);
      }
    } finally {
      if (entityManager != null && entityManager.isOpen()) {
        entityManager.close();
      }
    }
  }

  @Test
  List<Employee> getEmployees() {
    try {
      UserTransaction transaction = TM.getUserTransaction();

      transaction.begin();

      EntityManager entityManager = JPA.createEntityManager();

      List<Employee> employees =
          entityManager.createQuery("select e from Employee e").getResultList();

      transaction.commit();

      entityManager.close();

      return employees;
    } catch (Exception e) {
      e.printStackTrace();

      return emptyList();
    }
  }

  @Test
  void alterEmployee() {
    try {
      UserTransaction transaction = TM.getUserTransaction();

      transaction.begin();

      EntityManager entityManager = JPA.createEntityManager();

      List<Employee> employees =
          entityManager.createQuery("select e from Employee e").getResultList();

      employees.stream()
          .filter(e -> e.getId() == 1234)
          .forEach(e -> e.setPhone(e.getPhone() + "666"));
      transaction.commit();

      entityManager.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
