package com.hr.jpa;

import com.hr.Employee;
import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceUnitUtil;
import javax.transaction.UserTransaction;

import static com.hr.jpa.TestUtils.exampleEmployee;
import static org.junit.jupiter.api.Assertions.*;

class ReferencesTest extends JpaTest {
  @Test
  void shouldGetReferenceToNotCachedInstance() throws Exception {
    try {
      // GIVEN
      int id = 1234;
      String firstName = "Jane";
      save(exampleEmployee(id, "Jane", "Doe"));

      PersistenceUnitUtil persistenceUnitUtil = JPA.getPersistenceUnitUtil();
      EntityManager entityManager = JPA.createEntityManager();
      UserTransaction transaction = TM.getUserTransaction();

      transaction.begin();

      // WHEN
      Employee employee = entityManager.getReference(Employee.class, id);

      // THEN
      // Without any operations, the reference is not loaded
      assertFalse(persistenceUnitUtil.isLoaded(employee));

      // And the initialized reference is loaded
      Hibernate.initialize(employee);

      assertTrue(persistenceUnitUtil.isLoaded(employee));

      // And the object is fully initialized
      transaction.commit();
      entityManager.close();

      assertEquals(firstName, employee.getFirstName());
    } finally {
      TM.rollback();
    }
  }

  @Test
  void shouldGetReferenceAndInstantiateItLazily() throws Exception {
    try {
      // GIVEN
      PersistenceUnitUtil persistenceUnitUtil = JPA.getPersistenceUnitUtil();

      int id = 1234;
      String firstName = "Jane";
      save(exampleEmployee(id, "Jane", "Doe"));

      EntityManager entityManager = JPA.createEntityManager();
      UserTransaction transaction = TM.getUserTransaction();

      transaction.begin();

      // WHEN
      Employee lazyEmployee = entityManager.getReference(Employee.class, id);

      // THEN
      // Without any operations, the reference is not loaded
      assertFalse(persistenceUnitUtil.isLoaded(lazyEmployee));

      // With invoking one of the getters, the reference is loaded
      String obtainedFirstName = lazyEmployee.getFirstName();

      assertTrue(persistenceUnitUtil.isLoaded(lazyEmployee));
      assertEquals(firstName, obtainedFirstName);

      transaction.commit();
      entityManager.close();
    } finally {
      TM.rollback();
    }
  }

  @Test
  void shouldThrowExceptionOnLazilyLoadedReferenceAndGetterOutOfTransaction() throws Exception {
    try {
      // GIVEN
      PersistenceUnitUtil persistenceUnitUtil = JPA.getPersistenceUnitUtil();

      int id = 1234;
      String firstName = "Jane";
      save(exampleEmployee(id, "Jane", "Doe"));

      EntityManager entityManager = JPA.createEntityManager();
      UserTransaction transaction = TM.getUserTransaction();

      transaction.begin();

      // WHEN
      Employee lazyEmployee = entityManager.getReference(Employee.class, id);

      // THEN
      // Without any operations, the reference is not loaded
      assertFalse(persistenceUnitUtil.isLoaded(lazyEmployee));

      // And invoking getter on an uninitialized reference without a transaction results in an
      // exception
      transaction.commit();
      entityManager.close();

      assertThrows(LazyInitializationException.class, lazyEmployee::getFirstName);
    } finally {
      TM.rollback();
    }
  }

  @Test
  void shouldGetReferenceToCachedInstance() throws Exception {
    try {
      // GIVEN
      PersistenceUnitUtil persistenceUnitUtil = JPA.getPersistenceUnitUtil();

      int id = 1234;
      String firstName = "Jane";
      save(exampleEmployee(id, "Jane", "Doe"));

      EntityManager entityManager = JPA.createEntityManager();
      UserTransaction transaction = TM.getUserTransaction();

      transaction.begin();

      // WHEN
      // Object is cached
      entityManager.find(Employee.class, id);

      // And reference is got
      Employee lazyEmployee = entityManager.getReference(Employee.class, id);

      // THEN
      // The reference is already loaded
      assertTrue(persistenceUnitUtil.isLoaded(lazyEmployee));

      transaction.commit();
      entityManager.close();

      // And the object is fully initialized
      assertEquals(firstName, lazyEmployee.getFirstName());
    } finally {
      TM.rollback();
    }
  }

  @Test
  void shouldThrowExceptionWhenEntityDoesNotExistInDb() throws Exception {
    try {
      // GIVEN
      PersistenceUnitUtil persistenceUnitUtil = JPA.getPersistenceUnitUtil();

      int id = 1234;
      assertNull(findById(id));

      EntityManager entityManager = JPA.createEntityManager();
      UserTransaction transaction = TM.getUserTransaction();

      transaction.begin();

      // WHEN
      Employee lazyEmployee = entityManager.getReference(Employee.class, id);

      // THEN
      assertFalse(persistenceUnitUtil.isLoaded(lazyEmployee));
      assertThrows(EntityNotFoundException.class, () -> Hibernate.initialize(lazyEmployee));

      transaction.commit();
      entityManager.close();
    } finally {
      TM.rollback();
    }
  }
}
