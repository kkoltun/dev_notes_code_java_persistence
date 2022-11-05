package dev.karolkoltun.persistence.jpa;

import dev.karolkoltun.persistence.entity.Employee;
import org.hibernate.Session;
import org.hibernate.annotations.QueryHints;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.transaction.UserTransaction;

import static dev.karolkoltun.persistence.jpa.TestUtils.exampleEmployee;
import static org.junit.jupiter.api.Assertions.*;

class ReadOnlyOperationsTest extends JpaTest {
  @Test
  void shouldDetachEntityFromPersistenceContext() throws Exception {
    try {
      // GIVEN
      int idOne = 1;
      int idTwo = 2;
      save(exampleEmployee(idOne, "Jane", "Doe"));
      save(exampleEmployee(idTwo, "Jane", "Doe"));

      EntityManager entityManager = JPA.createEntityManager();
      UserTransaction transaction = TM.getUserTransaction();

      transaction.begin();

      // WHEN
      Employee employeeOne = entityManager.find(Employee.class, idOne);
      Employee employeeTwo = entityManager.find(Employee.class, idTwo);

      // Both entities are in persistent state
      assertTrue(entityManager.contains(employeeOne));
      assertTrue(entityManager.contains(employeeTwo));

      // Entity #1 is detached
      entityManager.detach(employeeOne);

      // THEN
      assertFalse(entityManager.contains(employeeOne));
      assertTrue(entityManager.contains(employeeTwo));

      transaction.commit();
      entityManager.close();
    } finally {
      TM.rollback();
    }
  }

  @Test
  void shouldClearPersistenceContext() throws Exception {
    try {
      // GIVEN
      int idOne = 1;
      int idTwo = 2;
      save(exampleEmployee(idOne, "Jane", "Doe"));
      save(exampleEmployee(idTwo, "Jane", "Doe"));

      EntityManager entityManager = JPA.createEntityManager();
      UserTransaction transaction = TM.getUserTransaction();

      transaction.begin();

      // WHEN
      Employee employeeOne = entityManager.find(Employee.class, idOne);
      Employee employeeTwo = entityManager.find(Employee.class, idTwo);

      // Both entities are in persistent state
      assertTrue(entityManager.contains(employeeOne));
      assertTrue(entityManager.contains(employeeTwo));

      // Persistence context is cleared
      entityManager.clear();

      // THEN
      assertFalse(entityManager.contains(employeeOne));
      assertFalse(entityManager.contains(employeeTwo));

      transaction.commit();
      entityManager.close();
    } finally {
      TM.rollback();
    }
  }

  @Test
  void shouldSetSessionAsReadOnly() throws Exception {
    try {
      // GIVEN
      int id = 1;
      String oldName = "Old Name";
      String newName = "New Name";
      save(exampleEmployee(id, oldName, "Doe"));

      EntityManager entityManager = JPA.createEntityManager();
      UserTransaction transaction = TM.getUserTransaction();

      transaction.begin();

      // WHEN
      // Session is read only
      entityManager.unwrap(Session.class).setDefaultReadOnly(true);

      Employee employeeOne = entityManager.find(Employee.class, id);

      // Entity is in persistent state
      assertTrue(entityManager.contains(employeeOne));

      // Name is changed and transaction committed
      employeeOne.setFirstName(newName);

      transaction.commit();
      entityManager.close();

      // THEN
      Employee foundEmployee = findById(1);
      assertNotEquals(newName, foundEmployee.getFirstName());
      assertEquals(oldName, foundEmployee.getFirstName());
    } finally {
      TM.rollback();
    }
  }

  @Test
  void shouldSetEntityAsReadOnly() throws Exception {
    try {
      // GIVEN
      int idOne = 1;
      int idTwo = 2;
      String oldName = "Old Name";
      String newName = "New Name";

      save(exampleEmployee(idOne, oldName, "Doe"));
      save(exampleEmployee(idTwo, newName, "Doe"));

      EntityManager entityManager = JPA.createEntityManager();
      UserTransaction transaction = TM.getUserTransaction();

      transaction.begin();

      // WHEN
      Employee employeeOne = entityManager.find(Employee.class, idOne);
      Employee employeeTwo = entityManager.find(Employee.class, idTwo);

      // Both entities are in persistent state
      assertTrue(entityManager.contains(employeeOne));
      assertTrue(entityManager.contains(employeeTwo));

      // Entity #1 is set to read-only
      entityManager.unwrap(Session.class).setReadOnly(employeeOne, true);

      // Name is changed in both entities
      employeeOne.setFirstName(newName);
      employeeTwo.setFirstName(newName);

      // Transaction is committed
      transaction.commit();
      entityManager.close();

      // THEN
      assertEquals(oldName, findById(1).getFirstName());
      assertEquals(newName, findById(2).getFirstName());
    } finally {
      TM.rollback();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldSetQueryAsReadOnly() throws Exception {
    try {
      // GIVEN
      int idOne = 1;
      int idTwo = 2;
      String oldName = "Old Name";
      String newName = "New Name";

      save(exampleEmployee(idOne, oldName, "Doe"));
      save(exampleEmployee(idTwo, oldName, "Doe"));

      EntityManager entityManager = JPA.createEntityManager();
      UserTransaction transaction = TM.getUserTransaction();

      transaction.begin();

      // WHEN
      // Query is read-only and all found employees have a new name assigned
      entityManager
          .unwrap(Session.class)
          .createQuery("SELECT e from Employee e")
          .setReadOnly(true)
          .list()
          .forEach(employee -> ((Employee) employee).setFirstName(newName));

      // And transaction is committed
      transaction.commit();
      entityManager.close();

      // THEN
      assertEquals(oldName, findById(1).getFirstName());
      assertEquals(oldName, findById(2).getFirstName());
    } finally {
      TM.rollback();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldSetJpaQueryAsReadOnly() throws Exception {
    try {
      // GIVEN
      int idOne = 1;
      int idTwo = 2;
      String oldName = "Old Name";
      String newName = "New Name";

      save(exampleEmployee(idOne, oldName, "Doe"));
      save(exampleEmployee(idTwo, oldName, "Doe"));

      EntityManager entityManager = JPA.createEntityManager();
      UserTransaction transaction = TM.getUserTransaction();

      transaction.begin();

      // WHEN
      // JPA query has dirty-checking disabled with query hints
      entityManager
          .createQuery("SELECT e from Employee e")
          .setHint(QueryHints.READ_ONLY, true)
          .getResultList()
          .forEach(employee -> ((Employee) employee).setFirstName(newName));

      // And transaction is committed
      transaction.commit();
      entityManager.close();

      // THEN
      assertEquals(oldName, findById(1).getFirstName());
      assertEquals(oldName, findById(2).getFirstName());
    } finally {
      TM.rollback();
    }
  }
}
