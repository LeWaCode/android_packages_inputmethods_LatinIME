/*
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

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.PopupWindow;

import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardActionListener;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.keyboard.MoreKeysDetector;
import com.android.inputmethod.keyboard.MoreKeysPanel;
import com.android.inputmethod.keyboard.PointerTracker.DrawingProxy;
import com.android.inputmethod.keyboard.PointerTracker.TimerProxy;


/**
 * A view that renders a virtual {@link MoreSuggestions}. It handles rendering of keys and detecting
 * key presses and touch movements.
 */
public class MoreSuggestionsView extends KeyboardView implements MoreKeysPanel {
    private final int[] mCoordinates = new int[2];

    private final KeyDetector mKeyDetector;

    private Controller mController;
    private KeyboardActionListener mListener;
    private int mOriginX;
    private int mOriginY;

    private static final TimerProxy EMPTY_TIMER_PROXY = new TimerProxy.Adapter();

    private final KeyboardActionListener mSuggestionsPaneListener =
            new KeyboardActionListener.Adapter() {
        @Override
        public void onPress(int primaryCode, boolean withSliding) {
            mListener.onPress(primaryCode, withSliding);
        }

        @Override
        public void onRelease(int primaryCode, boolean withSliding) {
            mListener.onRelease(primaryCode, withSliding);
        }

        @Override
        public void onCodeInput(int primaryCode, int[] keyCodes, int x, int y) {
            mListener.onCustomRequest(primaryCode - MoreSuggestions.SUGGESTION_CODE_BASE);
        }

        @Override
        public void onCancelInput() {
            mListener.onCancelInput();
        }
    };

    public MoreSuggestionsView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.suggestionsPaneViewStyle);
    }

    public MoreSuggestionsView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final Resources res = context.getResources();
        // Override default ProximityKeyDetector.
        mKeyDetector = new MoreKeysDetector(res.getDimension(
                R.dimen.more_suggestions_slide_allowance));
        // Remove gesture detector on suggestions pane
        setKeyPreviewPopupEnabled(false, 0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard != null) {
            final int width = keyboard.mOccupiedWidth + getPaddingLeft() + getPaddingRight();
            final int height = keyboard.mOccupiedHeight + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(width, height);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    public void setKeyboard(Keyboard keyboard) {
        super.setKeyboard(keyboard);
        mKeyDetector.setKeyboard(keyboard, -getPaddingLeft(),
                -getPaddingTop() + mVerticalCorrection);
    }

    @Override
    public KeyDetector getKeyDetector() {
        return mKeyDetector;
    }

    @Override
    public KeyboardActionListener getKeyboardActionListener() {
        return mSuggestionsPaneListener;
    }

    @Override
    public DrawingProxy getDrawingProxy() {
        return  this;
    }

    @Override
    public TimerProxy getTimerProxy() {
        return EMPTY_TIMER_PROXY;
    }

    @Override
    public void setKeyPreviewPopupEnabled(boolean previewEnabled, int delay) {
        // Suggestions pane needs no pop-up key preview displayed, so we pass always false with a
        // delay of 0. The delay does not matter actually since the popup is not shown anyway.
        super.setKeyPreviewPopupEnabled(false, 0);
    }

    @Override
    public void setShifted(boolean shifted) {
        // Nothing to do with.
    }

    @Override
    public void showMoreKeysPanel(View parentView, Controller controller, int pointX, int pointY,
            PopupWindow window, KeyboardActionListener listener) {
        mController = controller;
        mListener = listener;
        final View container = (View)getParent();
        final MoreSuggestions pane = (MoreSuggestions)getKeyboard();

        parentView.getLocationInWindow(mCoordinates);
        final int paneLeft = pointX - (pane.mOccupiedWidth / 2) + parentView.getPaddingLeft();
        final int x = wrapUp(Math.max(0, Math.min(paneLeft,
                parentView.getWidth() - pane.mOccupiedWidth))
                - container.getPaddingLeft() + mCoordinates[0],
                container.getMeasuredWidth(), 0, parentView.getWidth());
        final int y = pointY
                - (container.getMeasuredHeight() - container.getPaddingBottom())
                + parentView.getPaddingTop() + mCoordinates[1];

        window.setContentView(container);
        window.setWidth(container.getMeasuredWidth());
        window.setHeight(container.getMeasuredHeight());
        window.showAtLocation(parentView, Gravity.NO_GRAVITY, x, y);

        mOriginX = x + container.getPaddingLeft() - mCoordinates[0];
        mOriginY = y + container.getPaddingTop() - mCoordinates[1];
    }

    private static int wrapUp(int x, int width, int left, int right) {
        if (x < left)
            return left;
        if (x + width > right)
            return right - width;
        return x;
    }

    @Override
    public boolean dismissMoreKeysPanel() {
        return mController.dismissMoreKeysPanel();
    }

    @Override
    public int translateX(int x) {
        return x - mOriginX;
    }

    @Override
    public int translateY(int y) {
        return y - mOriginY;
    }
}