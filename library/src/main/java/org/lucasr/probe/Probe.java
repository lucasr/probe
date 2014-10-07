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
 * In order to intercept view method calls, you should deploy an
 * {@link Interceptor} in the target {@link Context} with an (optional)
 * {@link Filter}. For example:
 * <pre>
 * public final class MainActivity extends Activity {
 *     @Override
 *     protected void onCreate(Bundle savedInstanceState) {
 *         Probe.deploy(this, new MyInterceptor())
 *         super.onCreate(savedInstanceState);
 *         setContentView(R.id.main_activity);
 *     }
 * }
 * </pre>
 *
 * <h2>View proxies</h2>
 * <p>If no {@link Filter} is provided, the {@link Interceptor} will act on
 * every inflated {@link View} in the target {@link Context}.</p>
 *
 * <p>Probe intercepts {@link View} method calls by inflating proxies
 * instead of the original views. These proxies can be generated either
 * at build or run time.</p>
 *
 * <p>If you use Gradle, you can use Probe's plugin which will take care
 * of generating the proxy classes for you. Build-time proxies add virtually
 * no runtime overhead as they're just subclasses of the views referenced
 * in your layout resources.</p>
 *
 * <p>If you app doesn't include build-time proxies, Probe will dynamically
 * generate {@link View} proxy classes at runtime using DexMaker, if your
 * app was compiled with DexMaker as a dependency. Runtime proxies are
 * relatively slow to generate and will greatly affect the time to
 * inflate your layouts. They should only be used for debugging
 * purposes.</p>
 *
 * @see #deploy(Context,Interceptor)
 * @see #deploy(Context,Interceptor,Filter)
 * @see Interceptor
 * @see Filter
 */
public class Probe {
    private final Interceptor mInterceptor;
    private final Filter mFilter;

    private Probe(Context context, Interceptor interceptor, Filter filter) {
        if (context == null) {
            throw new IllegalArgumentException("Context should not be null.");
        }

        if (interceptor == null) {
            throw new IllegalArgumentException("Interceptor should not be null.");
        }

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
     * Deploy an {@link Interceptor} in the given {@link Context}.
     */
    public static void deploy(Context context, Interceptor interceptor) {
        deploy(context, interceptor, null);
    }

    /**
     * Deploy an {@link Interceptor} in the given {@link Context} with a {@link Filter}.
     */
    public static void deploy(Context context, Interceptor interceptor, Filter filter) {
        final Probe probe = new Probe(context, interceptor, filter);
        LayoutInflater.from(context).setFactory2(new ProbeViewFactory(context, probe));
    }
}
