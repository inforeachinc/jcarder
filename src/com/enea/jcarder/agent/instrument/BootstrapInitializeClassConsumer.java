package com.enea.jcarder.agent.instrument;

import java.util.function.BiConsumer;

public class BootstrapInitializeClassConsumer {
    private static BiConsumer<Class<?>, String> delegate;

    public static void setDelegate(BiConsumer<Class<?>, String> delegate) {
        BootstrapInitializeClassConsumer.delegate = delegate;
    }

    public static void onClassInitialize(Class<?> clazz, String methodWithClass) {
        if (delegate != null)
            delegate.accept(clazz, methodWithClass);
    }
}
