package dev.karolkoltun.persistence.jpa;

import dev.karolkoltun.persistence.Employee;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.transaction.UserTransaction;

import java.util.HashSet;
import java.util.Set;

import static dev.karolkoltun.persistence.jpa.TestUtils.exampleEmployee;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to demonstrate problems with determining object identity of entities in detached state.
 */
class IdentityProblemTest extends JpaTest {
  @Test
  void shouldRetrieveDifferentObjectsForTheSameRow() throws Exception {
    try {
      // GIVEN
      int id = 1234;
      save(exampleEmployee(id, "Jane", "Doe"));

      // WHEN
      // First transaction is performed
      EntityManager entityManager = JPA.createEntityManager();
      UserTransaction transaction = TM.getUserTransaction();
      transaction.begin();

      Employee employeeA_firstTransaction = entityManager.find(Employee.class, id);
      Employee employeeB_firstTransaction =
          entityManager.find(Employee.class, id); // cached employee should be retrieved

      transaction.commit();
      entityManager.close();

      // And second transaction is performed
      entityManager = JPA.createEntityManager();
      transaction.begin();

      Employee employeeC_secondTransaction = entityManager.find(Employee.class, id);

      transaction.commit();
      entityManager.close();

      // THEN
      // The same row is represented by two object instances
      assertSame(employeeA_firstTransaction, employeeB_firstTransaction);
      assertEquals(employeeA_firstTransaction, employeeB_firstTransaction);

      assertNotEquals(employeeA_firstTransaction, employeeC_secondTransaction);
      assertNotEquals(employeeB_firstTransaction, employeeC_secondTransaction);

      // And the produced HashSet has an "unexpected" size (this is not crucial in this test, just a note)
      Set<Employee> allEmployees = new HashSet<>();

      allEmployees.add(employeeA_firstTransaction);
      allEmployees.add(employeeB_firstTransaction);
      allEmployees.add(employeeC_secondTransaction);

      assertEquals(2, allEmployees.size());
    } finally {
      TM.rollback();
    }
  }
}
