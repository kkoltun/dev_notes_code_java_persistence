package dev.karolkoltun.persistence.jpa;

import dev.karolkoltun.persistence.Employee;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.transaction.UserTransaction;

import static dev.karolkoltun.persistence.jpa.TestUtils.exampleEmployee;
import static org.junit.jupiter.api.Assertions.*;

class MergeTest extends JpaTest {

  @Test
  void shouldMergeDetachedInstanceWithChangedEntity() throws Exception {
    try {
      // GIVEN
      int id = 1234;
      String oldFirstName = "Old First Name";
      String newFirstName = "New First Name";

      String oldLastName = "Old Last Name";
      String newLastName = "New Last Name";

      save(exampleEmployee(id, oldFirstName, oldLastName));

      EntityManager entityManager = JPA.createEntityManager();
      UserTransaction transaction = TM.getUserTransaction();

      transaction.begin();

      // WHEN
      // Employee is found and detached
      Employee employeeBeforeMerge = entityManager.find(Employee.class, id);

      transaction.commit();
      entityManager.close();

      // And this employee has first name is changed in database
      updateFirstName(id, newFirstName);

      // And the detached employee last name is changed
      employeeBeforeMerge.setLastName(newLastName);

      // And detached employee is merged with fresh persistence context
      entityManager = JPA.createEntityManager();
      transaction = TM.getUserTransaction();

      transaction.begin();

      Employee employeeAfterMerge = entityManager.merge(employeeBeforeMerge);

      // THEN
      // Returned employee has changes merged onto the persistent one
      assertEquals(oldFirstName, employeeAfterMerge.getFirstName());
      assertEquals(newLastName, employeeAfterMerge.getLastName());

      transaction.commit();
      entityManager.close();
    } finally {
      TM.rollback();
    }
  }

  @Test
  void shouldReturnPersistentInstanceAfterMergingTransient() throws Exception {
    try {
      // GIVEN
      Employee transientEmployee = exampleEmployee();

      EntityManager entityManager = JPA.createEntityManager();
      UserTransaction transaction = TM.getUserTransaction();

      transaction.begin();

      // WHEN
      Employee mergedEmployee = entityManager.merge(transientEmployee);

      // THEN
      assertFalse(entityManager.contains(transientEmployee));
      assertTrue(entityManager.contains(mergedEmployee));

      transaction.commit();
      entityManager.close();
    } finally {
      TM.rollback();
    }
  }

  private void updateFirstName(int id, String newFirstName) throws Exception {
    try {
      EntityManager entityManager = JPA.createEntityManager();
      UserTransaction transaction = TM.getUserTransaction();

      transaction.begin();

      Employee foundEmployee = entityManager.find(Employee.class, id);
      foundEmployee.setFirstName(newFirstName);

      transaction.commit();
      entityManager.close();
    } finally {
      TM.rollback();
    }
  }
}
