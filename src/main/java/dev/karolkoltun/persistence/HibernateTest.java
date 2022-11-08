package dev.karolkoltun.persistence;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import java.sql.Connection;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class HibernateTest {

    protected boolean useDatasource = false;
    protected EntityManagerFactory entityManagerFactory;

    public abstract DataSourceProvider dataSourceProvider();

    public abstract boolean recreateBeforeEachTest();

    public Optional<Integer> getIsolationLevel() {
        // This is not the best way, but it works with tests.
        return Optional.empty();
    }


    // todo what about closing the factory?
    @BeforeEach
    void beforeEach() {
        if (getIsolationLevel().isPresent() && useDatasource) {
            // If you use the datasource, hibernate will ignore the transaction isolation level set in the properties.
            // You would have to set the isolation level directly in the datasource configuration, but I could not find the exact way for MySQL or PostgreSQL datasources...
            // See: https://vladmihalcea.com/a-beginners-guide-to-transaction-isolation-levels-in-enterprise-java/ for details.
            throw new IllegalStateException("Do not use the datasource along with non-default isolation level.");
        }

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

        if (recreateBeforeEachTest()) {
            properties.put("hibernate.hbm2ddl.auto", "create-drop");
        }

        if (getIsolationLevel().isPresent()) {
            properties.setProperty("hibernate.connection.isolation", String.valueOf(getIsolationLevel().get()));
        }

        properties.setProperty("hibernate.show_sql", "true");

        BootstrapServiceRegistry bootstrapServiceRegistry = new BootstrapServiceRegistryBuilder().build();
        StandardServiceRegistryBuilder standardServiceRegistryBuilder = new StandardServiceRegistryBuilder(bootstrapServiceRegistry)
                .applySettings(properties);
        StandardServiceRegistry standardServiceRegistry = standardServiceRegistryBuilder
                .build();

        MetadataSources metadataSources = new MetadataSources(standardServiceRegistry);
        for (Class<?> annotatedClass : dataSourceProvider().annotatedClasses()) {
            metadataSources.addAnnotatedClass(annotatedClass);
        }

        Metadata metadata = metadataSources.buildMetadata();

        entityManagerFactory = metadata.buildSessionFactory();
    }

    @AfterEach
    void afterEach() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }

    protected <T> T getUsingJpa(Function<EntityManager, T> function) {
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
    protected void doUsingJpa(Consumer<EntityManager> function) {
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

    protected <T> T getUsingJDBC(Function<Connection, T> callable) {
        AtomicReference<T> result = new AtomicReference<>();
        Transaction transaction = null;
        try (Session session = entityManagerFactory.unwrap(SessionFactory.class).openSession()) {

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
        }

        return result.get();
    }

    protected void doUsingJDBC(Consumer<Connection> callable) {
        Transaction transaction = null;
        try (Session session = entityManagerFactory.unwrap(SessionFactory.class).openSession()) {
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
        }
    }

    protected <T> T getUsingHibernateReadOnly(Function<Session, T> callable) {
        return getUsingHibernate(callable, true, FlushMode.MANUAL);
    }

    protected <T> T getUsingHibernate(Function<Session, T> callable, boolean readOnly, FlushMode flushMode) {
        AtomicReference<T> result = new AtomicReference<>();
        Transaction transaction = null;
        try (Session session = entityManagerFactory.unwrap(SessionFactory.class).openSession()) {
            session.setDefaultReadOnly(readOnly);
            session.setHibernateFlushMode(flushMode);

            transaction = session.beginTransaction();

            session.doWork(connection -> result.set(callable.apply(session)));

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
        }

        return result.get();
    }

    protected void doInHibernateReadOnly(Consumer<Session> callable) {
        doInHibernate(callable, true, FlushMode.AUTO);
    }

    protected  void doInHibernate(Consumer<Session> callable) {
        doInHibernate(callable, false, FlushMode.AUTO);
    }

    protected void doInHibernate(Consumer<Session> callable, boolean readOnly, FlushMode flushMode) {
        Transaction transaction = null;
        try (Session session = entityManagerFactory.unwrap(SessionFactory.class).openSession()) {
            session.setDefaultReadOnly(readOnly);
            session.setHibernateFlushMode(flushMode);

            transaction = session.beginTransaction();

            callable.accept(session);

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
        }
    }

    public static void awaitOnLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
