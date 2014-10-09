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

package org.lucasr.probe.interceptors;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;

import org.lucasr.probe.Interceptor;

import java.util.WeakHashMap;

/**
 * Tints leaf views according to the number of times they got measured in a single
 * layout traversal.
 * <p>
 * <p>Overmeasure is a factor of the number of {@link View#onMeasure(int, int)}
 * calls a {@link View} performs within a single layout traversal. It's analogous
 * to the notion of overdraw but for measurement.</p>
 *
 * <p>{@link OvermeasureInterceptor} tints views as follows:</p>
 * <ul>
 *     <li><b>No color</b> means there is no overmeasure.</li>
 *     <li><b>Blue</b> indicates an overmeasure of 1x. The {@link View} has been
 *     measured twice in a single traversal.</li>
 *     <li><b>Green</b> indicates an overmeasure of 2x. The {@link View} has been
 *     measured three times in a single traversal.</li>
 *     <li><b>Light red</b> indicates an overmeasure of 3x. The {@link View} has been
 *     measured three times in a single traversal. Having one or two simple views
 *     in light red is acceptable. Consider re-organizing and simplifying your view
 *     hierarchy and/or writing custom layouts.</li>
 *     <li><b>Dark red</b> indicates an overmeasure of 4x or more. The {@link View} has been
 *     measured five or more times in a single traversal. This is wrong.</li>
 * </ul>
 */
public class OvermeasureInterceptor extends Interceptor {
    private static final int NO_OVERMEASURE = 0xFF999999;
    private static final int OVERMEASURE_1x = 0xFFAAAAFF;
    private static final int OVERMEASURE_2x = 0xFF2AFF80;
    private static final int OVERMEASURE_3x = 0xFFFFAAAA;
    private static final int OVERMEASURE_4x = 0xFFFF0000;

    private final int mRootId;
    private final WeakHashMap<View, Integer> mMeasureByView;

    private final Paint mTintPaint;

    public OvermeasureInterceptor(int rootId) {
        mRootId = rootId;
        mMeasureByView = new WeakHashMap<View, Integer>();
        mTintPaint = new Paint();
    }

    private void forceLayoutRecursive(View view) {
        invokeForceLayout(view);

        if (view instanceof ViewGroup) {
            final ViewGroup viewGroup = (ViewGroup) view;

            final int count = viewGroup.getChildCount();
            for (int i = 0; i < count; i++) {
                forceLayoutRecursive(viewGroup.getChildAt(i));
            }
        }
    }

    private void invalidate(View view) {
        view.invalidate();

        if (view instanceof ViewGroup) {
            final ViewGroup viewGroup = (ViewGroup) view;

            final int count = viewGroup.getChildCount();
            for (int i = 0; i < count; i++) {
                invalidate(viewGroup.getChildAt(i));
            }
        }
    }

    @Override
    public void onMeasure(View view, int widthMeasureSpec, int heightMeasureSpec) {
        if (view.getId() == mRootId) {
            mMeasureByView.clear();
        }

        super.onMeasure(view, widthMeasureSpec, heightMeasureSpec);

        if (!(view instanceof ViewGroup)) {
            final Integer measureCount = mMeasureByView.get(view);
            mMeasureByView.put(view, (measureCount != null ? measureCount : 0) + 1);
        }
    }

    @Override
    public void onDraw(View view, Canvas canvas) {
        super.onDraw(view, canvas);

        if (view instanceof ViewGroup) {
            return;
        }

        if (view.getId() != mRootId) {
            Integer measureCount = mMeasureByView.get(view);
            if (measureCount == null) {
                measureCount = 0;
            }

            final int color;
            switch (measureCount) {
                case 0:
                case 1:
                    color = NO_OVERMEASURE;
                    break;

                case 2:
                    color = OVERMEASURE_1x;
                    break;

                case 3:
                    color = OVERMEASURE_2x;
                    break;

                case 4:
                    color = OVERMEASURE_3x;
                    break;

                default:
                    color = OVERMEASURE_4x;
                    break;
            }

            if (color != NO_OVERMEASURE) {
                final int tintColor = Color.argb(150, Color.red(color), Color.green(color),
                        Color.blue(color));
                mTintPaint.setColor(tintColor);
                canvas.drawPaint(mTintPaint);
            }
        }
    }

    @Override
    public void requestLayout(View view) {
        super.requestLayout(view);

        if (view.getId() == mRootId) {
            // Clear all measure spec caches and make sure all the
            // views will be redrawn.
            forceLayoutRecursive(view);
            invalidate(view);
        }
    }
}
