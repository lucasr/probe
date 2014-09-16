/*
 * Copyright (C) 2014 Lucas Rocha
 *
 * This code is based on bits and pieces of DexMaker's ProxyBuilder.
 *
 * Copyright (C) 2011 The Android Open Source Project
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

import com.google.dexmaker.Code;
import com.google.dexmaker.Comparison;
import com.google.dexmaker.DexMaker;
import com.google.dexmaker.FieldId;
import com.google.dexmaker.Label;
import com.google.dexmaker.Local;
import com.google.dexmaker.MethodId;
import com.google.dexmaker.TypeId;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PUBLIC;

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
    private enum ViewMethod {
        ON_MEASURE("onMeasure"),
        ON_LAYOUT("onLayout"),
        DRAW("draw"),
        ON_DRAW("onDraw"),
        REQUEST_LAYOUT("requestLayout"),
        SET_MEASURED_DIMENSION("setMeasuredDimension");

        private final String mMethodName;

        private ViewMethod(String methodName) {
            mMethodName = methodName;
        }

        String getName() {
            return mMethodName;
        }
    }

    private static final String FIELD_NAME_INTERCEPTOR = "$__interceptor";

    private static final Map<Class<?>, Class<?>> sGeneratedProxyClasses =
            Collections.synchronizedMap(new HashMap<Class<?>, Class<?>>());

    private static final ClassLoader PARENT_CLASS_LOADER = ViewProxyBuilder.class.getClassLoader();

    private static final TypeId<Canvas> CANVAS_TYPE = TypeId.get(Canvas.class);
    private static final TypeId<Interceptor> INTERCEPTOR_TYPE = TypeId.get(Interceptor.class);
    private static final TypeId<View> VIEW_TYPE = TypeId.get(View.class);
    private static final TypeId<Void> VOID_TYPE = TypeId.get(void.class);

    private static final Class<?>[] CONSTRUCTOR_ARG_TYPES =
            new Class<?>[] { Context.class, AttributeSet.class };
    private static final Object[] CONSTRUCTOR_ARG_VALUES = new Object[2];

    private final Class<T> mBaseClass;
    private File mDexCache;
    private Interceptor mInterceptor;

    private ViewProxyBuilder(Class<T> clazz) {
        mBaseClass = clazz;
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
     * Generates class field that holds a reference to the associated
     * {@link Interceptor} instance and the {@link View} constructor.
     */
    private static <T, G extends T> void generateConstructorAndFields(DexMaker dexMaker,
                                                                      TypeId<G> generatedType,
                                                                      TypeId<T> baseType) {
        final FieldId<G, Interceptor> interceptorField =
                generatedType.getField(INTERCEPTOR_TYPE, FIELD_NAME_INTERCEPTOR);
        dexMaker.declare(interceptorField, PRIVATE, null);

        final TypeId<?>[] types = classArrayToTypeArray(CONSTRUCTOR_ARG_TYPES);
        final MethodId<?, ?> constructor = generatedType.getConstructor(types);
        final Code constructorCode = dexMaker.declare(constructor, PUBLIC);

        final Local<?>[] params = new Local[types.length];
        for (int i = 0; i < params.length; ++i) {
            params[i] = constructorCode.getParameter(i, types[i]);
        }

        final MethodId<T, ?> superConstructor = baseType.getConstructor(types);
        final Local<G> thisRef = constructorCode.getThis(generatedType);
        constructorCode.invokeDirect(superConstructor, null, thisRef, params);
        constructorCode.returnVoid();
    }

    /**
     * Generates the {@link View#onMeasure(int, int)} method for the proxy class.
     */
    private static <T, G extends T> void generateOnMeasureMethod(DexMaker dexMaker,
                                                                 TypeId<G> generatedType,
                                                                 TypeId<T> baseType) {
        final FieldId<G, Interceptor> interceptorField =
                generatedType.getField(INTERCEPTOR_TYPE, FIELD_NAME_INTERCEPTOR);

        final String methodName = ViewMethod.ON_MEASURE.getName();

        final MethodId<T, Void> superMethod = baseType.getMethod(VOID_TYPE, methodName, TypeId.INT,
                TypeId.INT);
        final MethodId<Interceptor, Void> onMeasureMethod =
                INTERCEPTOR_TYPE.getMethod(VOID_TYPE, methodName, VIEW_TYPE, TypeId.INT, TypeId.INT);

        final MethodId<G, Void> methodId = generatedType.getMethod(VOID_TYPE, methodName,
                TypeId.INT, TypeId.INT);
        final Code code = dexMaker.declare(methodId, PUBLIC);

        final Local<G> localThis = code.getThis(generatedType);
        final Local<Interceptor> nullInterceptor = code.newLocal(INTERCEPTOR_TYPE);
        final Local<Interceptor> localInterceptor = code.newLocal(INTERCEPTOR_TYPE);
        final Local<Integer> localWidth = code.getParameter(0, TypeId.INT);
        final Local<Integer> localHeight = code.getParameter(1, TypeId.INT);

        code.iget(interceptorField, localInterceptor, localThis);
        code.loadConstant(nullInterceptor, null);

        // Interceptor is not null, call it.
        final Label interceptorNullCase = new Label();
        code.compare(Comparison.EQ, interceptorNullCase, nullInterceptor, localInterceptor);
        code.invokeVirtual(onMeasureMethod, null, localInterceptor, localThis,
                localWidth, localHeight);
        code.returnVoid();

        // Interceptor is null, call super method.
        code.mark(interceptorNullCase);
        code.invokeSuper(superMethod, null, localThis, localWidth, localHeight);
        code.returnVoid();

        final MethodId<G, Void> callsSuperMethod = generatedType.getMethod(VOID_TYPE,
                superMethodName(methodName), TypeId.INT, TypeId.INT);

        final Code superCode = dexMaker.declare(callsSuperMethod, PUBLIC);

        final Local<G> superThis = superCode.getThis(generatedType);
        final Local<Integer> superLocalWidth = superCode.getParameter(0, TypeId.INT);
        final Local<Integer> superLocalHeight = superCode.getParameter(1, TypeId.INT);
        superCode.invokeSuper(superMethod, null, superThis, superLocalWidth, superLocalHeight);
        superCode.returnVoid();
    }

    /**
     * Generates the {@link View#onLayout(boolean, int, int, int, int)} method
     * for the proxy class.
     */
    private static <T, G extends T> void generateOnLayoutMethod(DexMaker dexMaker,
                                                                TypeId<G> generatedType,
                                                                TypeId<T> baseType) {
        final FieldId<G, Interceptor> interceptorField =
                generatedType.getField(INTERCEPTOR_TYPE, FIELD_NAME_INTERCEPTOR);

        final String methodName = ViewMethod.ON_LAYOUT.getName();

        final MethodId<T, Void> superMethod = baseType.getMethod(VOID_TYPE, methodName,
                TypeId.BOOLEAN, TypeId.INT, TypeId.INT, TypeId.INT, TypeId.INT);
        final MethodId<Interceptor, Void> onLayoutMethod =
                INTERCEPTOR_TYPE.getMethod(VOID_TYPE, methodName, VIEW_TYPE, TypeId.BOOLEAN,
                        TypeId.INT, TypeId.INT, TypeId.INT, TypeId.INT);

        final MethodId<G, Void> methodId = generatedType.getMethod(VOID_TYPE, methodName,
                TypeId.BOOLEAN, TypeId.INT, TypeId.INT, TypeId.INT, TypeId.INT);
        final Code code = dexMaker.declare(methodId, PUBLIC);

        final Local<G> localThis = code.getThis(generatedType);
        final Local<Interceptor> nullInterceptor = code.newLocal(INTERCEPTOR_TYPE);
        final Local<Interceptor> localInterceptor = code.newLocal(INTERCEPTOR_TYPE);
        final Local<Boolean> localChanged = code.getParameter(0, TypeId.BOOLEAN);
        final Local<Integer> localLeft = code.getParameter(1, TypeId.INT);
        final Local<Integer> localTop = code.getParameter(2, TypeId.INT);
        final Local<Integer> localRight = code.getParameter(3, TypeId.INT);
        final Local<Integer> localBottom = code.getParameter(4, TypeId.INT);

        code.iget(interceptorField, localInterceptor, localThis);
        code.loadConstant(nullInterceptor, null);

        // Interceptor is not null, call it.
        final Label interceptorNullCase = new Label();
        code.compare(Comparison.EQ, interceptorNullCase, nullInterceptor, localInterceptor);
        code.invokeVirtual(onLayoutMethod, null, localInterceptor, localThis, localChanged,
                localLeft, localTop, localRight, localBottom);
        code.returnVoid();

        // Interceptor is null, call super method.
        code.mark(interceptorNullCase);
        code.invokeSuper(superMethod, null, localThis, localChanged, localLeft, localTop,
                localRight, localBottom);
        code.returnVoid();

        final MethodId<G, Void> callsSuperMethod = generatedType.getMethod(VOID_TYPE,
                superMethodName(methodName), TypeId.BOOLEAN, TypeId.INT, TypeId.INT, TypeId.INT, TypeId.INT);

        final Code superCode = dexMaker.declare(callsSuperMethod, PUBLIC);

        final Local<G> superThis = superCode.getThis(generatedType);
        final Local<Boolean> superLocalChanged = superCode.getParameter(0, TypeId.BOOLEAN);
        final Local<Integer> superLocalLeft = superCode.getParameter(1, TypeId.INT);
        final Local<Integer> superLocalTop = superCode.getParameter(2, TypeId.INT);
        final Local<Integer> superLocalRight = superCode.getParameter(3, TypeId.INT);
        final Local<Integer> superLocalBottom = superCode.getParameter(4, TypeId.INT);
        superCode.invokeSuper(superMethod, null, superThis, superLocalChanged, superLocalLeft,
                superLocalTop, superLocalRight, superLocalBottom);
        superCode.returnVoid();
    }

    /**
     * Generates the {@link View#draw(Canvas)} method for the proxy class.
     */
    private static <T, G extends T> void generateDrawMethod(DexMaker dexMaker,
                                                            TypeId<G> generatedType,
                                                            TypeId<T> baseType,
                                                            ViewMethod viewMethod) {
        final FieldId<G, Interceptor> interceptorField =
                generatedType.getField(INTERCEPTOR_TYPE, FIELD_NAME_INTERCEPTOR);

        final String methodName = viewMethod.getName();

        final MethodId<T, Void> superMethod = baseType.getMethod(VOID_TYPE, methodName, CANVAS_TYPE);
        final MethodId<Interceptor, Void> drawMethod =
                INTERCEPTOR_TYPE.getMethod(VOID_TYPE, methodName, VIEW_TYPE, CANVAS_TYPE);

        final MethodId<G, Void> methodId = generatedType.getMethod(VOID_TYPE, methodName, CANVAS_TYPE);
        final Code code = dexMaker.declare(methodId, PUBLIC);

        final Local<G> localThis = code.getThis(generatedType);
        final Local<Interceptor> nullInterceptor = code.newLocal(INTERCEPTOR_TYPE);
        final Local<Interceptor> localInterceptor = code.newLocal(INTERCEPTOR_TYPE);
        final Local<Canvas> localCanvas = code.getParameter(0, CANVAS_TYPE);

        code.iget(interceptorField, localInterceptor, localThis);
        code.loadConstant(nullInterceptor, null);

        // Interceptor is not null, call it.
        final Label interceptorNullCase = new Label();
        code.compare(Comparison.EQ, interceptorNullCase, nullInterceptor, localInterceptor);
        code.invokeVirtual(drawMethod, null, localInterceptor, localThis, localCanvas);
        code.returnVoid();

        // Interceptor is null, call super method.
        code.mark(interceptorNullCase);
        code.invokeSuper(superMethod, null, localThis, localCanvas);
        code.returnVoid();

        final MethodId<G, Void> callsSuperMethod =
                generatedType.getMethod(VOID_TYPE, superMethodName(methodName), CANVAS_TYPE);

        final Code superCode = dexMaker.declare(callsSuperMethod, PUBLIC);

        final Local<G> superThis = superCode.getThis(generatedType);
        final Local<Canvas> superLocalCanvas = superCode.getParameter(0, CANVAS_TYPE);
        superCode.invokeSuper(superMethod, null, superThis, superLocalCanvas);
        superCode.returnVoid();
    }

    /**
     * Generates the {@link View#onDraw(Canvas)} method for the proxy class.
     */
    private static <T, G extends T> void generateDrawMethods(DexMaker dexMaker,
                                                             TypeId<G> generatedType,
                                                             TypeId<T> baseType) {
        generateDrawMethod(dexMaker, generatedType, baseType, ViewMethod.DRAW);
        generateDrawMethod(dexMaker, generatedType, baseType, ViewMethod.ON_DRAW);
    }

    /**
     * Generates the {@link View#requestLayout()} method for the proxy class.
     */
    private static <T, G extends T> void generateRequestLayoutMethod(DexMaker dexMaker,
                                                                     TypeId<G> generatedType,
                                                                     TypeId<T> baseType) {
        final FieldId<G, Interceptor> interceptorField =
                generatedType.getField(INTERCEPTOR_TYPE, FIELD_NAME_INTERCEPTOR);

        final String methodName = ViewMethod.REQUEST_LAYOUT.getName();

        final MethodId<T, Void> superMethod = baseType.getMethod(VOID_TYPE, methodName);
        final MethodId<Interceptor, Void> requestLayoutMethod =
                INTERCEPTOR_TYPE.getMethod(VOID_TYPE, methodName, VIEW_TYPE);

        final MethodId<?, ?> methodId = generatedType.getMethod(VOID_TYPE, methodName);
        final Code code = dexMaker.declare(methodId, PUBLIC);

        final Local<G> localThis = code.getThis(generatedType);
        final Local<Interceptor> nullInterceptor = code.newLocal(INTERCEPTOR_TYPE);
        final Local<Interceptor> localInterceptor = code.newLocal(INTERCEPTOR_TYPE);

        code.iget(interceptorField, localInterceptor, localThis);
        code.loadConstant(nullInterceptor, null);

        // Interceptor is not null, call it.
        final Label interceptorNullCase = new Label();
        code.compare(Comparison.EQ, interceptorNullCase, nullInterceptor, localInterceptor);
        code.invokeVirtual(requestLayoutMethod, null, localInterceptor, localThis);
        code.returnVoid();

        // Interceptor is null, call super method.
        code.mark(interceptorNullCase);
        code.invokeSuper(superMethod, null, localThis);
        code.returnVoid();

        final MethodId<G, Void> callsSuperMethod =
                generatedType.getMethod(VOID_TYPE, superMethodName(methodName));

        final Code superCode = dexMaker.declare(callsSuperMethod, PUBLIC);

        final Local<G> superThis = superCode.getThis(generatedType);
        superCode.invokeSuper(superMethod, null, superThis);
        superCode.returnVoid();
    }

    /**
     * Generates the {@link View#setMeasuredDimension(int, int)} method for
     * the proxy class.
     */
    private static <T, G extends T> void generateSetMeasuredDimension(DexMaker dexMaker,
                                                                      TypeId<G> generatedType,
                                                                      TypeId<T> baseType) {
        final String methodName = ViewMethod.SET_MEASURED_DIMENSION.getName();

        final MethodId<T, Void> superMethod = baseType.getMethod(VOID_TYPE, methodName, TypeId.INT,
                TypeId.INT);

        final MethodId<G, Void> callsSuperMethod = generatedType.getMethod(VOID_TYPE,
                superMethodName(methodName), TypeId.INT, TypeId.INT);

        final Code code = dexMaker.declare(callsSuperMethod, PUBLIC);

        final Local<G> localThis = code.getThis(generatedType);
        final Local<Integer> localWidth = code.getParameter(0, TypeId.INT);
        final Local<Integer> localHeight = code.getParameter(1, TypeId.INT);
        code.invokeSuper(superMethod, null, localThis, localWidth, localHeight);
        code.returnVoid();
    }

    /**
     * Generates dynamic {@link View} proxy class.
     */
    @SuppressWarnings("unchecked")
    private Class<? extends T> generateProxyClass() throws IOException {
        Class<? extends T> proxyClass = (Class) sGeneratedProxyClasses.get(mBaseClass);
        if (proxyClass != null && proxyClass.getClassLoader().getParent() == PARENT_CLASS_LOADER) {
            // Cache hit; return immediately.
            return proxyClass;
        }

        // Cache missed; generate the proxy class.
        final DexMaker dexMaker = new DexMaker();

        final String proxyClassName = getClassNameForProxyOf(mBaseClass);
        final TypeId<? extends T> generatedType = TypeId.get("L" + proxyClassName + ";");
        final TypeId<T> baseType = TypeId.get(mBaseClass);

        generateConstructorAndFields(dexMaker, generatedType, baseType);
        generateOnMeasureMethod(dexMaker, generatedType, baseType);
        generateOnLayoutMethod(dexMaker, generatedType, baseType);
        generateDrawMethods(dexMaker, generatedType, baseType);
        generateRequestLayoutMethod(dexMaker, generatedType, baseType);
        generateSetMeasuredDimension(dexMaker, generatedType, baseType);

        dexMaker.declare(generatedType, proxyClassName + ".generated", PUBLIC, baseType);

        final ClassLoader classLoader = dexMaker.generateAndLoad(PARENT_CLASS_LOADER, mDexCache);
        try {
            proxyClass = (Class<? extends T>) classLoader.loadClass(proxyClassName);
        } catch (IllegalAccessError e) {
            // Thrown when the base class is not accessible.
            throw new UnsupportedOperationException(
                    "cannot proxy inaccessible class " + mBaseClass, e);
        } catch (ClassNotFoundException e) {
            // Should not be thrown, we're sure to have generated this class.
            throw new AssertionError(e);
        }

        sGeneratedProxyClasses.put(mBaseClass, proxyClass);
        return proxyClass;
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

    private static void setInterceptorInstanceField(Object instance, Interceptor interceptor) {
        try {
            final Field interceptorField =
                    instance.getClass().getDeclaredField(FIELD_NAME_INTERCEPTOR);
            interceptorField.setAccessible(true);
            interceptorField.set(instance, interceptor);
        } catch (NoSuchFieldException e) {
            // Should not be thrown, generated proxy class has been generated with this field.
            throw new AssertionError(e);
        } catch (IllegalAccessException e) {
            // Should not be thrown, we just set the field to accessible.
            throw new AssertionError(e);
        }
    }

    private static String superMethodName(String methodName) {
        return "$__super$" + methodName;
    }

    private static <T> String getClassNameForProxyOf(Class<? extends T> clazz) {
        return clazz.getSimpleName() + "_Proxy";
    }

    private static TypeId<?>[] classArrayToTypeArray(Class<?>[] input) {
        final TypeId<?>[] result = new TypeId[input.length];
        for (int i = 0; i < input.length; ++i) {
            result[i] = TypeId.get(input[i]);
        }

        return result;
    }

    static <T> ViewProxyBuilder forClass(Class<T> clazz) {
        return new ViewProxyBuilder(clazz);
    }

    /**
     * Calls {@code super.onMeasure(int, int)} on the given {@link View}.
     */
    static void superOnMeasure(View view, int widthMeasureSpec, int heightMeasureSpec) {
        try {
            view.getClass()
                .getMethod(superMethodName(ViewMethod.ON_MEASURE.getName()), int.class, int.class)
                .invoke(view, widthMeasureSpec, heightMeasureSpec);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to call super.onMeasure(int, int) on " + view);
        }
    }

    /**
     * Calls {@code super.onLayout(boolean, int, int, int, int)} on the given {@link View}.
     */
    static void superOnLayout(View view, boolean changed, int l, int t, int r, int b) {
        try {
            view.getClass()
                .getMethod(superMethodName(ViewMethod.ON_LAYOUT.getName()), boolean.class,
                        int.class, int.class, int.class, int.class)
                .invoke(view, changed, l, t, r, b);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to call super.onLayout(boolean int, int, int, int) in " + view);
        }
    }

    /**
     * Calls {@code super.draw(Canvas)} on the given {@link View}.
     */
    static void superDraw(View view, Canvas canvas) {
        try {
            view.getClass()
                .getMethod(superMethodName(ViewMethod.DRAW.getName()), Canvas.class)
                .invoke(view, canvas);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to call super.draw(Canvas) in " + view);
        }
    }

    /**
     * Calls {@code super.onDraw(Canvas)} on the given {@link View}.
     */
    static void superOnDraw(View view, Canvas canvas) {
        try {
            view.getClass()
                .getMethod(superMethodName(ViewMethod.ON_DRAW.getName()), Canvas.class)
                .invoke(view, canvas);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to call super.onDraw(Canvas) in " + view);
        }
    }

    /**
     * Calls {@code super.requestLayout()} on the given {@link View}.
     */
    static void superRequestLayout(View view) {
        try {
            view.getClass()
                .getMethod(superMethodName(ViewMethod.REQUEST_LAYOUT.getName()))
                .invoke(view);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to call super.requestLayout() in " + view);
        }
    }

    /**
     * Calls {@code super.setMeasuredDimension(int, int)} on the given {@link View}.
     */
    static void superSetMeasuredDimension(View view, int width, int height) {
        try {
            view.getClass()
                    .getMethod(superMethodName(ViewMethod.SET_MEASURED_DIMENSION.getName()),
                            int.class, int.class)
                    .invoke(view, width, height);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to call super.onMeasure(int, int) on " + view);
        }
    }

    ViewProxyBuilder interceptor(Interceptor interceptor) {
        mInterceptor = interceptor;
        return this;
    }

    ViewProxyBuilder dexCache(File dexCache) {
        mDexCache = dexCache;
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

        setInterceptorInstanceField(result, mInterceptor);
        return result;
    }
}
