/*
 * Copyright (C) 2014 Lucas Rocha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lucasr.probe;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.lucasr.probe.ViewClassUtil.findProxyViewClass;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds a lightweight dynamic proxy class that redirects {@link View}
 * layout-related method calls to an {@link Interceptor}.
 *
 * {@link ViewProxyBuilder} is used by {@link ProbeViewFactory} to dynamically
 * wrap the inflated {@link View} instances for a given {@link Probe}.
 *
 * @see Probe
 * @see ProbeViewFactory
 */
final class ViewProxyBuilder<T extends View> {
    private static final Map<Class<?>, Class<?>> sGeneratedProxyClasses =
            Collections.synchronizedMap(new HashMap<Class<?>, Class<?>>());

    static final Class<?>[] CONSTRUCTOR_ARG_TYPES = new Class<?>[] {
        Context.class, AttributeSet.class
    };
    private static final Object[] CONSTRUCTOR_ARG_VALUES = new Object[2];

    private final Context mContext;
    private final Class<T> mBaseClass;
    private final ClassLoader mParentClassLoader;
    private Interceptor mInterceptor;

    private ViewProxyBuilder(Context context, Class<T> clazz) {
        mContext = context;
        mBaseClass = clazz;
        mParentClassLoader = context.getClassLoader();
    }

    private static <T> Constructor<? extends T> getProxyClassConstructor(Class<? extends T> proxyClass) {
        final Constructor<? extends T> constructor;
        try {
            constructor = proxyClass.getConstructor(CONSTRUCTOR_ARG_TYPES);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("No constructor for " + proxyClass.getName()
                    + " with parameter types " + Arrays.toString(CONSTRUCTOR_ARG_TYPES));
        }

        return constructor;
    }

    /**
     * Generates dynamic {@link View} proxy class.
     */
    @SuppressWarnings("unchecked")
    private Class<? extends T> generateProxyClass() throws IOException {
        Class<? extends T> proxyClass = (Class) sGeneratedProxyClasses.get(mBaseClass);
        if (proxyClass != null &&
                (proxyClass.getClassLoader() == mParentClassLoader ||
                 proxyClass.getClassLoader().getParent() == mParentClassLoader)) {
            // Cache hit; return immediately.
            return proxyClass;
        }

        proxyClass = (Class<? extends T>) findProxyViewClass(mContext, mBaseClass.getName());
        if (proxyClass != null) {
            // This app ships with the build-time proxy.
            sGeneratedProxyClasses.put(mBaseClass, proxyClass);
            return proxyClass;
        }

        try {
            Class.forName("com.google.dexmaker.DexMaker");
            return DexProxyBuilder.generateProxyClass(mContext, mBaseClass);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static RuntimeException launderCause(InvocationTargetException e) {
        final Throwable cause = e.getCause();

        // Errors should be thrown as they are.
        if (cause instanceof Error) {
            throw (Error) cause;
        }

        // RuntimeException can be thrown as-is.
        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }

        // Declared exceptions will have to be wrapped.
        throw new UndeclaredThrowableException(cause);
    }

    /**
     * Calls {@code super.onMeasure(int, int)} on the given {@link View}.
     */
    static void superOnMeasure(View view, int widthMeasureSpec, int heightMeasureSpec) {
        final ViewProxy proxy = (ViewProxy) view;
        proxy.superOnMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Calls {@code super.onLayout(boolean, int, int, int, int)} on the given {@link View}.
     */
    static void superOnLayout(View view, boolean changed, int l, int t, int r, int b) {
        final ViewProxy proxy = (ViewProxy) view;
        proxy.superOnLayout(changed, l, t, r, b);
    }

    /**
     * Calls {@code super.draw(Canvas)} on the given {@link View}.
     */
    static void superDraw(View view, Canvas canvas) {
        final ViewProxy proxy = (ViewProxy) view;
        proxy.superDraw(canvas);
    }

    /**
     * Calls {@code super.onDraw(Canvas)} on the given {@link View}.
     */
    static void superOnDraw(View view, Canvas canvas) {
        final ViewProxy proxy = (ViewProxy) view;
        proxy.superOnDraw(canvas);
    }

    /**
     * Calls {@code super.requestLayout()} on the given {@link View}.
     */
    static void superRequestLayout(View view) {
        final ViewProxy proxy = (ViewProxy) view;
        proxy.superRequestLayout();
    }

    /**
     * Calls {@code super.setMeasuredDimension(int, int)} on the given {@link View}.
     */
    static void superSetMeasuredDimension(View view, int width, int height) {
        final ViewProxy proxy = (ViewProxy) view;
        proxy.superSetMeasuredDimension(width, height);
    }

    static <T> ViewProxyBuilder forClass(Context context, Class<T> clazz) {
        return new ViewProxyBuilder(context, clazz);
    }

    ViewProxyBuilder interceptor(Interceptor interceptor) {
        mInterceptor = interceptor;
        return this;
    }

    ViewProxyBuilder constructorArgValues(Context context, AttributeSet attrs) {
        CONSTRUCTOR_ARG_VALUES[0] = context;
        CONSTRUCTOR_ARG_VALUES[1] = attrs;
        return this;
    }

    /**
     * Builds instance of the built {@link View} proxy class..
     */
    View build() throws IOException {
        final Class<? extends T> proxyClass = generateProxyClass();
        if (proxyClass == null) {
            return null;
        }

        final Constructor<? extends T> constructor = getProxyClassConstructor(proxyClass);

        final View result;
        try {
            result = constructor.newInstance(CONSTRUCTOR_ARG_VALUES);
        } catch (InstantiationException e) {
            // Should not be thrown, generated class is not abstract.
            throw new AssertionError(e);
        } catch (IllegalAccessException e) {
            // Should not be thrown, the generated constructor is accessible.
            throw new AssertionError(e);
        } catch (InvocationTargetException e) {
            // Thrown when the base class constructor throws an exception.
            throw launderCause(e);
        }

        ((ViewProxy) result).setInterceptor(mInterceptor);
        return result;
    }
}
