package com.forum;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import java.util.Properties;

public class HibernateConfiguration {
    private final SessionFactory sessionFactory;

    public HibernateConfiguration(String dialect, String driver, String jdbcUrl, String userName, String password) {
        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", dialect);

        properties.setProperty("hibernate.connection.driver_class", driver);
        properties.setProperty("hibernate.connection.url", jdbcUrl);
        properties.setProperty("hibernate.connection.username", userName);
        properties.setProperty("hibernate.connection.password", password);

        // Nice SQL logging
        properties.setProperty("hibernate.show_sql", "true");
        properties.setProperty("hibernate.format_sql", "true");
        properties.setProperty("hibernate.use_sql_comments", "true");

        properties.setProperty("hibernate.transaction.coordinator_class", "jdbc");

        BootstrapServiceRegistry bootstrapServiceRegistry = new BootstrapServiceRegistryBuilder().build();
        StandardServiceRegistryBuilder standardServiceRegistryBuilder = new StandardServiceRegistryBuilder(bootstrapServiceRegistry)
                .applySettings(properties);
        StandardServiceRegistry standardServiceRegistry = standardServiceRegistryBuilder
                .build();

        MetadataSources metadataSources = new MetadataSources(standardServiceRegistry);
        metadataSources.addAnnotatedClass(Post.class);
        metadataSources.addAnnotatedClass(PostComment.class);

        Metadata metadata = metadataSources.buildMetadata();

        sessionFactory = metadata.buildSessionFactory();
    }

    public Session getSession() {
        return sessionFactory.getCurrentSession();
    }
}
