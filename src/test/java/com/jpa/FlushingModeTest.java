package com.jpa;

import com.hr.Employee;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import javax.transaction.UserTransaction;

import static com.jpa.TestUtils.exampleEmployee;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FlushingModeTest extends JpaTest {

  @Test
  void shouldNotFlushBeforeQueryWhenFlushingModeCommit() throws Exception {
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
      // Employee name is changed
      entityManager.find(Employee.class, id).setFirstName(newName);

      // And flushing mode is commitSession is read only
      entityManager.setFlushMode(FlushModeType.COMMIT);

      // THEN
      // Query is performed without prior flushing
      assertEquals(
          oldName,
          entityManager
              .createQuery("SELECT e.firstName from Employee  e where e.id = :id")
              .setParameter("id", id)
              .getSingleResult());

      transaction.commit();
      entityManager.close();
    } finally {
      TM.rollback();
    }
  }

  @Test
  void shouldFlushBeforeJpaQueryIsExecuted() throws Exception {
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
      // Employee name is changed
      entityManager.find(Employee.class, id).setFirstName(newName);

      // THEN
      // JPA query is performed with prior flushing
      assertEquals(
          newName,
          entityManager
              .createQuery("SELECT e.firstName from Employee  e where e.id = :id")
              .setParameter("id", id)
              .getSingleResult());

      transaction.commit();
      entityManager.close();
    } finally {
      TM.rollback();
    }
  }

  @Test
  void shouldFlushManually() throws Exception {
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
      // Employee name is changed
      entityManager.find(Employee.class, id).setFirstName(newName);

      // And flushing mode is commitSession is read only
      entityManager.setFlushMode(FlushModeType.COMMIT);

      // And the persistence context is flushed manually
      entityManager.flush();

      // THEN
      // Entity with new name is found
      assertEquals(
          newName,
          entityManager
              .createQuery("SELECT e.firstName from Employee  e where e.id = :id")
              .setParameter("id", id)
              .getSingleResult());

      transaction.commit();
      entityManager.close();
    } finally {
      TM.rollback();
    }
  }
}
