package com.hr.jpa;

import com.hr.Employee;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import javax.persistence.EntityManager;
import javax.transaction.UserTransaction;

/**
 * Class used for starting JPA environment before and after a test class. Class inspired by test
 * configuration in book Java Persistence with Hibernate.
 */
class JpaTest {
  private static final String PERSISTENCE_UNIT_NAME = "HrAppPU";

  static InMemoryTransactionManagerSetup TM;
  static JpaSetup JPA;

  @BeforeAll
  static void beforeAll() throws Exception {
    TM = new InMemoryTransactionManagerSetup();
  }

  @BeforeEach
  void beforeEach() throws Exception {
    JPA = new JpaSetup(PERSISTENCE_UNIT_NAME, TM.getHibernateDialect());

    // Always drop the schema, cleaning up at least some of the artifacts
    // that might be left over from the last run, if it didn't cleanup
    // properly
    JPA.dropSchema();

    JPA.createSchema();
  }

  @AfterAll
  static void afterAll() throws Exception {
    if (TM != null) {
      TM.stop();
    }
  }

  @AfterEach
  void afterMethod() throws Exception {
    if (JPA != null) {
      if (!"true".equals(System.getProperty("keepSchema"))) {
        JPA.dropSchema();
      }
      JPA.getEntityManagerFactory().close();
    }
  }

  Employee save(Employee employee) throws Exception {
    UserTransaction transaction = TM.getUserTransaction();
    EntityManager entityManager = JPA.createEntityManager();

    transaction.begin();

    entityManager.persist(employee);

    transaction.commit();
    entityManager.close();

    return employee;
  }

  Employee findById(Integer id) throws Exception {
    UserTransaction transaction = TM.getUserTransaction();
    EntityManager entityManager;

    transaction.begin();
    entityManager = JPA.createEntityManager();

    Employee employee = entityManager.find(Employee.class, id);

    transaction.commit();
    entityManager.close();

    return employee;
  }
}
