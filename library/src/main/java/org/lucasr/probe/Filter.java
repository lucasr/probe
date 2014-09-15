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
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

/**
 * Select which {@link View}s in the hierarchy should direct their
 * method calls to the {@link Probe}'s {@link Interceptor}. You can define
 * a {@link Filter} when creating a new {@link Probe} instance.
 *
 * @see Probe
 */
public interface Filter {
    boolean shouldIntercept(Context context, View parent, String name, AttributeSet attrs);

    /**
     * Compose two or more {@link Filter}s.
     */
    public static class Compose implements Filter {
        private final Filter[] mFilters;

        public Compose(Filter filter1, Filter filter2) {
            this(new Filter[] { filter1, filter2 });
        }

        public Compose(Filter[] filters) {
            mFilters = filters;
        }

        @Override
        public boolean shouldIntercept(Context context, View parent, String name,
                                       AttributeSet attrs) {
            for (int i = 0; i < mFilters.length; i++) {
                if (!mFilters[i].shouldIntercept(context, parent, name, attrs)) {
                    return false;
                }
            }

            return true;
        }
    }

    /**
     * Filter by one or more view IDs.
     */
    public static class ViewId implements Filter {
        private static final int[] idAttribute = new int[] {
            android.R.attr.id
        };

        private final int[] mViewIds;

        public ViewId(int viewId) {
            this(new int[] { viewId });
        }

        public ViewId(int[] viewIds) {
            mViewIds = viewIds;
        }

        @Override
        public boolean shouldIntercept(Context context, View parent, String name,
                                       AttributeSet attrs) {
            final TypedArray a = context.obtainStyledAttributes(attrs, idAttribute);
            final int viewId = a.getResourceId(0, View.NO_ID);

            for (int i = 0; i < mViewIds.length; i++) {
                if (mViewIds[i] == viewId) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Filter by one or more parent view IDs.
     */
    public static class ParentId implements Filter {
        private final int[] mParentIds;

        public ParentId(int parentId) {
            this(new int[] { parentId });
        }

        public ParentId(int[] parentIds) {
            mParentIds = parentIds;
        }

        @Override
        public boolean shouldIntercept(Context context, View parent, String name,
                                       AttributeSet attrs) {
            if (parent == null) {
                return false;
            }

            final int parentId = parent.getId();

            for (int i = 0; i < mParentIds.length; i++) {
                if (mParentIds[i] == parentId) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Filter by one or more {@link View} class names.
     */
    public static class ClassName implements Filter {
        private final String[] mClassNames;

        public ClassName(String className) {
            this(new String[] { className });
        }

        public ClassName(String[] classNames) {
            mClassNames = classNames;
        }

        @Override
        public boolean shouldIntercept(Context context, View parent, String name,
                                       AttributeSet attrs) {
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex >= 0) {
                name = name.substring(dotIndex + 1);
            }

            for (int i = 0; i < mClassNames.length; i++) {
                if (name.equals(mClassNames[i])) {
                    return true;
                }
            }

            return false;
        }
    }
}
