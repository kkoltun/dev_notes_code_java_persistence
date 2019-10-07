package com.hr;

import bitronix.tm.TransactionManagerServices;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.transaction.UserTransaction;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;

import static java.time.LocalDate.now;
import static java.util.Collections.emptyList;

class EmployeeRepositoryHibernate {

  private static final String PERSISTENCE_UNIT_NAME = "HrAppPU";

  private TransactionManagerSetup transactionManagerSetup;
  private EntityManagerFactory entityManagerFactory;

  EmployeeRepositoryHibernate() throws Exception {
    transactionManagerSetup = new TransactionManagerSetup();
    entityManagerFactory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
  }

  void addNewEmployee(String firstName, String lastName, String email) {
    try {
      UserTransaction transaction = transactionManagerSetup.getUserTransaction();

      transaction.begin();

      EntityManager entityManager = entityManagerFactory.createEntityManager();

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

      entityManager.close();
    } catch (Exception e) {
      e.printStackTrace();
      try {
        transactionManagerSetup.getUserTransaction().rollback();
      } catch (Exception rollbackException) {
        System.err.println("Rollback of transaction failed, trace follows!");
        rollbackException.printStackTrace(System.err);
      }
    }
  }

  List<Employee> getEmployees() {
    try {
      UserTransaction transaction = transactionManagerSetup.getUserTransaction();

      transaction.begin();

      EntityManager entityManager = entityManagerFactory.createEntityManager();

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

  void alterEmployee() {
    try {
      UserTransaction transaction = transactionManagerSetup.getUserTransaction();

      transaction.begin();

      EntityManager entityManager = entityManagerFactory.createEntityManager();

      List<Employee> employees =
          entityManager.createQuery("select e from Employee e").getResultList();

      employees.stream().filter(e -> e.getId() == 1234).forEach(e -> e.setPhone(e.getPhone() + "666"));
      transaction.commit();

      entityManager.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  void close() {
    entityManagerFactory.close();
    TransactionManagerServices.getTransactionManager().shutdown();
  }
}
