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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.InvocationHandler;

/**
 * Dissect layout traversals on-the-fly.
 * <p>
 * It uses a {@link Filter} to select the {@link View}s in the hierarchy that
 * can be intercepted by the provided {@link Interceptor}.
 *
 * In order to intercept method calls on the filtered {@link View}s, you should
 * inflate your layout using a {@link Probe} instance. For example:
 * <pre>
 * public final class MainActivity extends Activity {
 *     @Override
 *     protected void onCreate(Bundle savedInstanceState) {
 *         super.onCreate(savedInstanceState);
 *         Probe probe = new Probe(this, new MyInterceptor(this));
 *         View root = probe.inflate(R.layout.main_activity, null);
 *         setContentView(root);
 *     }
 * }
 * </pre>
 * If no {@link Filter} is provided, the {@link Probe} will affect all inflated
 * {@link View}s.
 *
 * @see #inflate(int, android.view.ViewGroup)
 * @see Interceptor
 * @see Filter
 */
public class Probe {
    private final LayoutInflater mInflater;
    private final Interceptor mInterceptor;
    private final Filter mFilter;

    public Probe(Context context, Interceptor interceptor) {
        this(context, interceptor, null);
    }

    public Probe(Context context, Interceptor interceptor, Filter filter) {
        if (context == null) {
            throw new IllegalArgumentException("Context should not be null.");
        }

        if (interceptor == null) {
            throw new IllegalArgumentException("Interceptor should not be null.");
        }

        mInflater = LayoutInflater.from(context).cloneInContext(context);
        mInflater.setFactory2(new ProbeViewFactory(context, this));

        mInterceptor = interceptor;
        mFilter = filter;
    }

    Interceptor getInterceptor() {
        return mInterceptor;
    }

    Filter getFilter() {
        return mFilter;
    }

    /**
     * Inflate a new view hierarchy from the specified xml resource.
     */
    public View inflate(int resource, ViewGroup root) {
        return mInflater.inflate(resource, root);
    }

    /**
     * Inflate a new view hierarchy from the specified xml resource.
     */
    public View inflate(int resource, ViewGroup root, boolean attachToRoot) {
        return mInflater.inflate(resource, root, attachToRoot);
    }
}
