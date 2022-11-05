package dev.karolkoltun.persistence.jpa;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.PersistenceUnitUtil;
import java.util.HashMap;
import java.util.Map;

/**
 * Setup used to create an EntityManagerFactory.
 * Class inspired by test configuration in book Java Persistence with Hibernate.
 */
class JpaSetup {
  private final String persistenceUnitName;
  private final Map<String, String> properties = new HashMap<>();
  private final EntityManagerFactory entityManagerFactory;

  JpaSetup(String persistenceUnitName, String hibernateDialect) {
    this.persistenceUnitName = persistenceUnitName;

    // No automatic scanning by Hibernate, all persistence units list explicit classes/packages
    properties.put(
        "hibernate.archive.autodetection",
        "none"
    );

    // Nice SQL logging
    properties.put(
        "hibernate.show_sql",
        "true"
    );
    properties.put(
        "hibernate.format_sql",
        "true"
    );
    properties.put(
        "hibernate.use_sql_comments",
        "true"
    );

    // When EntityManager#remove() is called, reset identifier value of the entity.
    // It then can be considered transient.
    properties.put(
        "hibernate.use_identifier_rollback",
        "true"
    );

    properties.put(
        "hibernate.dialect",
        hibernateDialect
    );

    entityManagerFactory =
        Persistence.createEntityManagerFactory(persistenceUnitName, properties);
  }

  EntityManager createEntityManager() {
    return entityManagerFactory.createEntityManager();
  }

  EntityManagerFactory getEntityManagerFactory() {
    return entityManagerFactory;
  }

  PersistenceUnitUtil getPersistenceUnitUtil() {
    return entityManagerFactory.getPersistenceUnitUtil();
  }

  void createSchema() {
    generateSchema("create");
  }

  void dropSchema() {
    generateSchema("drop");
  }

  void generateSchema(String action) {
    // Take exiting EMF properties, override the schema generation setting on a copy
    Map<String, String> createSchemaProperties = new HashMap<>(properties);
    createSchemaProperties.put(
        "javax.persistence.schema-generation.database.action",
        action
    );
    Persistence.generateSchema(persistenceUnitName, createSchemaProperties);
  }
}
