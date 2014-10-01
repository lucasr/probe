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

/**
 * Defines contract for a {@link android.view.View} proxy class.
 * <p>
 * Under the hood, {@link Probe} will create dynamic {@link android.view.View}
 * proxies that complies with this interface when inflating views.
 */
public interface ViewProxy {
    /**
     * Sets the {@link Interceptor} on this proxy.
     */
    void setInterceptor(Interceptor interceptor);

    /**
     * Calls {@code super.onMeasure(int, int)}.
     */
    void superOnMeasure(int widthMeasureSpec, int heightMeasureSpec);

    /**
     * Calls {@code super.onLayout(boolean, int, int, int, int)}.
     */
    void superOnLayout(boolean changed, int l, int t, int r, int b);

    /**
     * Calls {@code super.draw(Canvas)}.
     */
    void superDraw(Canvas canvas);

    /**
     * Calls {@code super.onDraw(Canvas)}.
     */
    void superOnDraw(Canvas canvas);

    /**
     * Calls {@code super.requestLayout()}.
     */
    void superRequestLayout();

    /**
     * Calls {@code super.onSetMeasuredDimension(int, int)}.
     */
    void superSetMeasuredDimension(int width, int height);
}
