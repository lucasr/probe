/*
 * Copyright (C) 2014 Lucas Rocha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lucasr.probe;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import java.io.IOException;
import java.lang.reflect.Modifier;

import static org.lucasr.probe.ViewClassUtil.findViewClass;

/**
 * {@link LayoutInflater.Factory2} used by a {@link Probe} instance to
 * inflate layout resources. It will wrap target {@link View}s with dynamic
 * proxy classes that redirect their method calls to an {@link Interceptor}.
 *
 * @see Interceptor
 * @see Filter
 * @see ViewProxyBuilder
 */
class ProbeViewFactory implements LayoutInflater.Factory2 {
    private static final String DEX_CACHE_DIRECTORY = "probe";

    private static final String TAG_FRAGMENT = "fragment";
    private static final String TAG_INTERNAL_CLASS = "com.android.internal";
    private static final String TAG_VIEW_STUB = "ViewStub";

    private final Context mContext;
    private final Probe mProbe;

    ProbeViewFactory(Context context, Probe probe) {
        mContext = context;
        mProbe = probe;
    }

    private View createProxyView(String name, AttributeSet attrs) throws ClassNotFoundException {
        try {
            final Class<?> viewClass = findViewClass(mContext, name);

            // Probe can't wrap final View classes, just bail.
            if (Modifier.isFinal(viewClass.getModifiers())) {
                return null;
            }

            return ViewProxyBuilder.forClass(viewClass)
                    .dexCache(mContext.getDir(DEX_CACHE_DIRECTORY, Context.MODE_PRIVATE))
                    .constructorArgValues(mContext, attrs)
                    .interceptor(mProbe.getInterceptor())
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create View proxy", e);
        }
    }

    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        if (name.equals(TAG_FRAGMENT) ||
            name.startsWith(TAG_INTERNAL_CLASS) ||
            name.contains(TAG_VIEW_STUB)) {
            return null;
        }

        final org.lucasr.probe.Filter filter = mProbe.getFilter();

        // Proxy the whole view tree if filter is undefined.
        if (filter == null || filter.shouldIntercept(mContext, parent, name, attrs)) {
            try {
                return createProxyView(name, attrs);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        return onCreateView(null, name, context, attrs);
    }
}
