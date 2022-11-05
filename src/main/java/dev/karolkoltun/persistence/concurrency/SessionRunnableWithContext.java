package dev.karolkoltun.persistence.concurrency;

import org.hibernate.Session;

import java.util.function.BiConsumer;

@FunctionalInterface
public interface SessionRunnableWithContext<T> extends BiConsumer<Session, T> {

}
