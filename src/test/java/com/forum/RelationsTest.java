package com.forum;

import org.hibernate.Session;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RelationsTest {
    private static DatabaseConfiguration databaseConfiguration;

    @BeforeAll
    static void beforeAll() {
        databaseConfiguration = new DatabaseConfiguration("org.hibernate.dialect.MySQLDialect",
                "com.mysql.cj.jdbc.Driver",
                "jdbc:mysql://localhost/forum?serverTimezone=UTC",
                "forum",
                "password");
    }

    @AfterAll
    static void afterAll() {
        databaseConfiguration.stop();
    }

    @Test
    public void plainTest() {
        Session session = databaseConfiguration.getSession();

        Post post = new Post("First post");

        post.getComments().add(
                new PostComment("My first review")
        );
        post.getComments().add(
                new PostComment("My second review")
        );
        post.getComments().add(
                new PostComment("My third review")
        );

        session.persist(post);
    }
}
