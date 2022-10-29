package com.bank;

import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.BeforeEach;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import java.sql.Connection;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class HibernateTest {

    protected boolean useDatasource = true;
    protected EntityManagerFactory entityManagerFactory;

    abstract DataSourceProvider dataSourceProvider();

    @BeforeEach
    void beforeEach() {
        DataSourceProvider dataSourceProvider = dataSourceProvider();
        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", dataSourceProvider.hibernateDialect());

        if (useDatasource) {
            properties.put("hibernate.connection.datasource", dataSourceProvider.dataSource());
        } else {
            properties.setProperty("hibernate.connection.url", dataSourceProvider().url());
            properties.setProperty("hibernate.connection.username", dataSourceProvider().user());
            properties.setProperty("hibernate.connection.password", dataSourceProvider().password());
        }

        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        properties.setProperty("hibernate.show_sql", "true");

        BootstrapServiceRegistry bootstrapServiceRegistry = new BootstrapServiceRegistryBuilder().build();
        StandardServiceRegistryBuilder standardServiceRegistryBuilder = new StandardServiceRegistryBuilder(bootstrapServiceRegistry)
                .applySettings(properties);
        StandardServiceRegistry standardServiceRegistry = standardServiceRegistryBuilder
                .build();

        MetadataSources metadataSources = new MetadataSources(standardServiceRegistry);
        metadataSources.addAnnotatedClass(Account.class);

        Metadata metadata = metadataSources.buildMetadata();

        entityManagerFactory = metadata.buildSessionFactory();
    }

    protected <T> T doInJpa(Function<EntityManager, T> function) {
        T result;

        EntityManager entitymanager = null;
        EntityTransaction transaction = null;

        try {
            entitymanager = entityManagerFactory.createEntityManager();
            transaction = entitymanager.getTransaction();

            transaction.begin();
            result = function.apply(entitymanager);

            if (!transaction.getRollbackOnly()) {
                transaction.commit();
            } else {
                try {
                    transaction.rollback();
                } catch (Exception e) {
                    System.err.println("Rollback failure: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        } catch (Throwable t) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception e) {
                    System.err.println("Rollback failure: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }

            throw t;
        } finally {
            if (entitymanager != null) {
                entitymanager.close();
            }
        }

        return result;
    }

    protected void doInJpa(Consumer<EntityManager> function) {
        EntityManager entitymanager = null;
        EntityTransaction transaction = null;

        try {
            entitymanager = entityManagerFactory.createEntityManager();
            transaction = entitymanager.getTransaction();

            transaction.begin();

            function.accept(entitymanager);

            if (!transaction.getRollbackOnly()) {
                transaction.commit();
            } else {
                try {
                    transaction.rollback();
                } catch (Exception e) {
                    System.err.println("Rollback failure: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        } catch (Throwable t) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception e) {
                    System.err.println("Rollback failure: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }

            throw t;
        } finally {
            if (entitymanager != null) {
                entitymanager.close();
            }
        }
    }

    protected <T> T doInJDBC(Function<Connection, T> callable) {
        AtomicReference<T> result = new AtomicReference<>();
        Session session = null;
        Transaction transaction = null;
        try {
            session = entityManagerFactory.unwrap(SessionFactory.class).openSession();

            transaction = session.beginTransaction();

            session.doWork(connection -> result.set(callable.apply(connection)));

            if (!transaction.getRollbackOnly()) {
                transaction.commit();
            } else {
                try {
                    transaction.rollback();
                } catch (Exception e) {
                    System.err.println("Rollback failure: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        } catch (Throwable t) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception e) {
                    System.err.println("Rollback failure: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
            throw t;
        } finally {
            if (session != null) {
                session.close();
            }
        }

        return result.get();
    }

    protected void doInJDBC(Consumer<Connection> callable) {
        Session session = null;
        Transaction transaction = null;
        try {
            session = entityManagerFactory.unwrap(SessionFactory.class).openSession();
            session.setDefaultReadOnly(true);
            session.setHibernateFlushMode(FlushMode.MANUAL);

            transaction = session.beginTransaction();

            session.doWork(callable::accept);

            if (!transaction.getRollbackOnly()) {
                transaction.commit();
            } else {
                try {
                    transaction.rollback();
                } catch (Exception e) {
                    System.err.println("Rollback failure: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
        } catch (Throwable t) {
            if (transaction != null && transaction.isActive()) {
                try {
                    transaction.rollback();
                } catch (Exception e) {
                    System.err.println("Rollback failure: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            }
            throw t;
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }
}
