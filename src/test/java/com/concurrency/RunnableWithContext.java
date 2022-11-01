package com.concurrency;

import org.hibernate.Session;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@FunctionalInterface
public interface RunnableWithContext<T> extends BiConsumer<Session, T> {
}
