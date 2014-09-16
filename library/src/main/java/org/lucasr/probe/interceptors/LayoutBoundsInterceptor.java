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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;

import org.lucasr.probe.Interceptor;

/**
 * Equivalent to Android's "Show layout bounds" developer option. The main difference
 * being that you can show bounds only for specific views in your UI by using a
 * {@link org.lucasr.probe.Filter} in your {@link org.lucasr.probe.Probe} instance.
 *
 * @see org.lucasr.probe.Filter
 */
public class LayoutBoundsInterceptor extends Interceptor {
    private static final int VIEW_COLOR = 0xFF3366FF;
    private static final int CONTAINER_COLOR = 0xFF2AFF80;

    private static final float BORDER_WIDTH_DP = 2.5f;
    private static final float BORDER_SIZE_RATIO = 0.2f;
    private static final float MAX_BORDER_SIZE_DP = 15;

    private final Paint mBorderPaint;
    private final float mBorderWidth;
    private final float mMaxBorderSize;

    public LayoutBoundsInterceptor(Context context) {
        final float density = context.getResources().getDisplayMetrics().density;
        mBorderWidth = BORDER_WIDTH_DP * density;
        mMaxBorderSize = MAX_BORDER_SIZE_DP * density;

        mBorderPaint = new Paint();
        mBorderPaint.setStrokeWidth(mBorderWidth);
    }

    @Override
    public void draw(View view, Canvas canvas) {
        super.draw(view, canvas);

        final int color;
        if (view instanceof ViewGroup) {
            color = CONTAINER_COLOR;
        } else {
            color = VIEW_COLOR;
        }

        mBorderPaint.setColor(color);

        final int width = view.getWidth();
        final int height = view.getHeight();

        final float lineWidth = Math.min(mMaxBorderSize, width * BORDER_SIZE_RATIO);
        final float lineHeight = Math.min(mMaxBorderSize, height * BORDER_SIZE_RATIO);

        canvas.drawLine(0, 0, lineHeight, 0, mBorderPaint);
        canvas.drawLine(0, 0, 0, lineHeight, mBorderPaint);

        canvas.drawLine(0, height, 0, height - lineHeight, mBorderPaint);
        canvas.drawLine(0, height, lineWidth, height, mBorderPaint);

        canvas.drawLine(width, 0, width, lineHeight, mBorderPaint);
        canvas.drawLine(width, 0, width - lineWidth, 0, mBorderPaint);

        canvas.drawLine(width, height, width, height - lineHeight, mBorderPaint);
        canvas.drawLine(width, height, width - lineWidth, height, mBorderPaint);
    }
}
