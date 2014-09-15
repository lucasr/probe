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

import android.graphics.Canvas;
import android.util.Log;
import android.view.View;

/**
 * Observe and override method calls on views inflated by a {@link Probe}. You
 * must provide an {@link Interceptor} instance when creating a new {@link Probe}.
 *
 * <p>By default, all methods in an {@link Interceptor} simply call their respective
 * view method implementations i.e. {@link #onDraw(View, Canvas)} will simply call
 * {@code view.onDraw(Canvas)} by default.</p>
 *
 * <p></>You should override one or more methods in your {@link Interceptor} subclass to
 * either observer the method calls or completely override the method for the given
 * view on-the-fly. For example:</p>
 *
 * <pre>
 * public void draw(View view, Canvas canvas) {
 *     // Not calling super.draw(view, canvas) here will completely
 *     // override the given view's draw call.
 *     canvas.drawRect(0, 0, view.getWidth(). view.Height(), paint);
 * }
 * </pre>
 *
 * An {@link Interceptor} can also be used to track and benchmark the behaviour of
 * specific views in your Android UI. When benchmarking, keep in mind that Probe
 * uses reflection to make calls to the wrapped {@link View} classes which affect
 * the performance of the wrapped views.
 */
public class Interceptor {
    public void onMeasure(View view, int widthMeasureSpec, int heightMeasureSpec) {
        ViewProxyBuilder.superOnMeasure(view, widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Intercepts an {@link View#onLayout(boolean, int, int, int, int)} call on the
     * given {@link View}.
     */
    public void onLayout(View view, boolean changed, int l, int t, int r, int b) {
        ViewProxyBuilder.superOnLayout(view, changed, l, t, r, b);
    }

    /**
     * Intercepts a {@link View#draw(Canvas)} call on the given {@link View}.
     */
    public void draw(View view, Canvas canvas) {
        ViewProxyBuilder.superDraw(view, canvas);
    }

    /**
     * Intercepts an {@link View#onDraw(Canvas)} call on the given {@link View}.
     */
    public void onDraw(View view, Canvas canvas) {
        ViewProxyBuilder.superOnDraw(view, canvas);
    }

    /**
     * Intercepts a {@link View#requestLayout()} call on the given {@link View}.
     */
    public void requestLayout(View view) {
        ViewProxyBuilder.superRequestLayout(view);
    }

    /**
     * Calls {@link View#setMeasuredDimension(int, int)} on the given {@link View}.
     * This can be used when overriding {@link View#onMeasure(int, int)} calls on-the-fly.
     */
    protected final void setMeasuredDimension(View view, int width, int height) {
        ViewProxyBuilder.superSetMeasuredDimension(view, width, height);
    }
}
