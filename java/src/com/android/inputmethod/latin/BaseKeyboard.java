/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Loads an XML description of a keyboard and stores the attributes of the keys. A keyboard
 * consists of rows of keys.
 * <p>The layout file for a keyboard contains XML that looks like the following snippet:</p>
 * <pre>
 * &lt;Keyboard
 *         latin:keyWidth="%10p"
 *         latin:keyHeight="50px"
 *         latin:horizontalGap="2px"
 *         latin:verticalGap="2px" &gt;
 *     &lt;Row latin:keyWidth="32px" &gt;
 *         &lt;Key latin:keyLabel="A" /&gt;
 *         ...
 *     &lt;/Row&gt;
 *     ...
 * &lt;/Keyboard&gt;
 * </pre>
 */
public class BaseKeyboard {

    static final String TAG = "BaseKeyboard";

    public static final int EDGE_LEFT = 0x01;
    public static final int EDGE_RIGHT = 0x02;
    public static final int EDGE_TOP = 0x04;
    public static final int EDGE_BOTTOM = 0x08;

    public static final int KEYCODE_SHIFT = -1;
    public static final int KEYCODE_MODE_CHANGE = -2;
    public static final int KEYCODE_CANCEL = -3;
    public static final int KEYCODE_DONE = -4;
    public static final int KEYCODE_DELETE = -5;
    public static final int KEYCODE_ALT = -6;

    /** Horizontal gap default for all rows */
    private int mDefaultHorizontalGap;

    /** Default key width */
    private int mDefaultWidth;

    /** Default key height */
    private int mDefaultHeight;

    /** Default gap between rows */
    private int mDefaultVerticalGap;

    /** Is the keyboard in the shifted state */
    private boolean mShifted;

    /** List of shift keys in this keyboard */
    private final List<Key> mShiftKeys = new ArrayList<Key>();

    /** Total height of the keyboard, including the padding and keys */
    private int mTotalHeight;

    /**
     * Total width of the keyboard, including left side gaps and keys, but not any gaps on the
     * right side.
     */
    private int mTotalWidth;

    /** List of keys in this keyboard */
    private final List<Key> mKeys = new ArrayList<Key>();

    /** Width of the screen available to fit the keyboard */
    private final int mDisplayWidth;

    /** Height of the screen */
    private final int mDisplayHeight;

    /** Keyboard mode, or zero, if none.  */
    private final int mKeyboardMode;

    // Variables for pre-computing nearest keys.

    private final int GRID_WIDTH;
    private final int GRID_HEIGHT;
    private final int GRID_SIZE;
    private int mCellWidth;
    private int mCellHeight;
    private int[][] mGridNeighbors;
    private int mProximityThreshold;
    /** Number of key widths from current touch point to search for nearest keys. */
    private static float SEARCH_DISTANCE = 1.8f;

    /**
     * Container for keys in the keyboard. All keys in a row are at the same Y-coordinate.
     * Some of the key size defaults can be overridden per row from what the {@link BaseKeyboard}
     * defines.
     */
    public static class Row {
        /** Default width of a key in this row. */
        public int defaultWidth;
        /** Default height of a key in this row. */
        public int defaultHeight;
        /** Default horizontal gap between keys in this row. */
        public int defaultHorizontalGap;
        /** Vertical gap following this row. */
        public int verticalGap;
        /**
         * Edge flags for this row of keys. Possible values that can be assigned are
         * {@link BaseKeyboard#EDGE_TOP EDGE_TOP} and {@link BaseKeyboard#EDGE_BOTTOM EDGE_BOTTOM}
         */
        public int rowEdgeFlags;

        /** The keyboard mode for this row */
        public int mode;

        private final BaseKeyboard parent;

        private Row(BaseKeyboard parent) {
            this.parent = parent;
        }

        public Row(Resources res, BaseKeyboard parent, XmlResourceParser parser) {
            this.parent = parent;
            TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.BaseKeyboard);
            defaultWidth = BaseKeyboardParser.getDimensionOrFraction(a,
                    R.styleable.BaseKeyboard_keyWidth,
                    parent.mDisplayWidth, parent.mDefaultWidth);
            defaultHeight = BaseKeyboardParser.getDimensionOrFraction(a,
                    R.styleable.BaseKeyboard_keyHeight,
                    parent.mDisplayHeight, parent.mDefaultHeight);
            defaultHorizontalGap = BaseKeyboardParser.getDimensionOrFraction(a,
                    R.styleable.BaseKeyboard_horizontalGap,
                    parent.mDisplayWidth, parent.mDefaultHorizontalGap);
            verticalGap = BaseKeyboardParser.getDimensionOrFraction(a,
                    R.styleable.BaseKeyboard_verticalGap,
                    parent.mDisplayHeight, parent.mDefaultVerticalGap);
            a.recycle();
            a = res.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.BaseKeyboard_Row);
            rowEdgeFlags = a.getInt(R.styleable.BaseKeyboard_Row_rowEdgeFlags, 0);
            mode = a.getResourceId(R.styleable.BaseKeyboard_Row_keyboardMode, 0);
        }
    }

    /**
     * Class for describing the position and characteristics of a single key in the keyboard.
     */
    public static class Key {
        /**
         * All the key codes (unicode or custom code) that this key could generate, zero'th
         * being the most important.
         */
        public int[] codes;

        /** Label to display */
        public CharSequence label;
        /** Label to display when keyboard is in temporary shift mode */
        public CharSequence temporaryShiftLabel;

        /** Icon to display instead of a label. Icon takes precedence over a label */
        public Drawable icon;
        /** Hint icon to display on the key in conjunction with the label */
        public Drawable hintIcon;
        /** Preview version of the icon, for the preview popup */
        public Drawable iconPreview;
        /** Width of the key, not including the gap */
        public int width;
        /** Height of the key, not including the gap */
        public int height;
        /** The horizontal gap before this key */
        public int gap;
        /** Whether this key is sticky, i.e., a toggle key */
        public boolean sticky;
        /** X coordinate of the key in the keyboard layout */
        public int x;
        /** Y coordinate of the key in the keyboard layout */
        public int y;
        /** The current pressed state of this key */
        public boolean pressed;
        /** If this is a sticky key, is it on? */
        public boolean on;
        /** Text to output when pressed. This can be multiple characters, like ".com" */
        public CharSequence text;
        /** Popup characters */
        public CharSequence popupCharacters;

        /**
         * Flags that specify the anchoring to edges of the keyboard for detecting touch events
         * that are just out of the boundary of the key. This is a bit mask of
         * {@link BaseKeyboard#EDGE_LEFT}, {@link BaseKeyboard#EDGE_RIGHT},
         * {@link BaseKeyboard#EDGE_TOP} and {@link BaseKeyboard#EDGE_BOTTOM}.
         */
        public int edgeFlags;
        /** Whether this is a modifier key, such as Shift or Alt */
        public boolean modifier;
        /** The BaseKeyboard that this key belongs to */
        protected final BaseKeyboard keyboard;
        /**
         * If this key pops up a mini keyboard, this is the resource id for the XML layout for that
         * keyboard.
         */
        public int popupResId;
        /** Whether this key repeats itself when held down */
        public boolean repeatable;


        private final static int[] KEY_STATE_NORMAL_ON = {
            android.R.attr.state_checkable,
            android.R.attr.state_checked
        };

        private final static int[] KEY_STATE_PRESSED_ON = {
            android.R.attr.state_pressed,
            android.R.attr.state_checkable,
            android.R.attr.state_checked
        };

        private final static int[] KEY_STATE_NORMAL_OFF = {
            android.R.attr.state_checkable
        };

        private final static int[] KEY_STATE_PRESSED_OFF = {
            android.R.attr.state_pressed,
            android.R.attr.state_checkable
        };

        private final static int[] KEY_STATE_NORMAL = {
        };

        private final static int[] KEY_STATE_PRESSED = {
            android.R.attr.state_pressed
        };

        /** Create an empty key with no attributes. */
        public Key(Row parent) {
            keyboard = parent.parent;
            height = parent.defaultHeight;
            gap = parent.defaultHorizontalGap;
            width = parent.defaultWidth - gap;
            edgeFlags = parent.rowEdgeFlags;
        }

        /** Create a key with the given top-left coordinate and extract its attributes from
         * the XML parser.
         * @param res resources associated with the caller's context
         * @param parent the row that this key belongs to. The row must already be attached to
         * a {@link BaseKeyboard}.
         * @param x the x coordinate of the top-left
         * @param y the y coordinate of the top-left
         * @param parser the XML parser containing the attributes for this key
         */
        public Key(Resources res, Row parent, int x, int y, XmlResourceParser parser) {
            this(parent);

            TypedArray a = res.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.BaseKeyboard);
            height = BaseKeyboardParser.getDimensionOrFraction(a,
                    R.styleable.BaseKeyboard_keyHeight,
                    keyboard.mDisplayHeight, parent.defaultHeight);
            gap = BaseKeyboardParser.getDimensionOrFraction(a,
                    R.styleable.BaseKeyboard_horizontalGap,
                    keyboard.mDisplayWidth, parent.defaultHorizontalGap);
            width = BaseKeyboardParser.getDimensionOrFraction(a,
                    R.styleable.BaseKeyboard_keyWidth,
                    keyboard.mDisplayWidth, parent.defaultWidth) - gap;
            a.recycle();
            a = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.BaseKeyboard_Key);

            // Horizontal gap is divided equally to both sides of the key.
            this.x = x + gap / 2;
            this.y = y;

            TypedValue codesValue = new TypedValue();
            a.getValue(R.styleable.BaseKeyboard_Key_codes, codesValue);
            if (codesValue.type == TypedValue.TYPE_INT_DEC
                    || codesValue.type == TypedValue.TYPE_INT_HEX) {
                codes = new int[] { codesValue.data };
            } else if (codesValue.type == TypedValue.TYPE_STRING) {
                codes = parseCSV(codesValue.string.toString());
            }

            iconPreview = a.getDrawable(R.styleable.BaseKeyboard_Key_iconPreview);
            setDefaultBounds(iconPreview);
            popupCharacters = a.getText(R.styleable.BaseKeyboard_Key_popupCharacters);
            popupResId = a.getResourceId(R.styleable.BaseKeyboard_Key_popupKeyboard, 0);
            repeatable = a.getBoolean(R.styleable.BaseKeyboard_Key_isRepeatable, false);
            modifier = a.getBoolean(R.styleable.BaseKeyboard_Key_isModifier, false);
            sticky = a.getBoolean(R.styleable.BaseKeyboard_Key_isSticky, false);
            edgeFlags = a.getInt(R.styleable.BaseKeyboard_Key_keyEdgeFlags, 0);
            edgeFlags |= parent.rowEdgeFlags;

            icon = a.getDrawable(R.styleable.BaseKeyboard_Key_keyIcon);
            setDefaultBounds(icon);
            hintIcon = a.getDrawable(R.styleable.BaseKeyboard_Key_keyHintIcon);
            setDefaultBounds(hintIcon);

            label = a.getText(R.styleable.BaseKeyboard_Key_keyLabel);
            temporaryShiftLabel = a.getText(R.styleable.BaseKeyboard_Key_temporaryShiftKeyLabel);
            text = a.getText(R.styleable.BaseKeyboard_Key_keyOutputText);

            if (codes == null && !TextUtils.isEmpty(label)) {
                codes = new int[] { label.charAt(0) };
            }
            a.recycle();
        }

        /**
         * Informs the key that it has been pressed, in case it needs to change its appearance or
         * state.
         * @see #onReleased(boolean)
         */
        public void onPressed() {
            pressed = !pressed;
        }

        /**
         * Changes the pressed state of the key. If it is a sticky key, it will also change the
         * toggled state of the key if the finger was release inside.
         * @param inside whether the finger was released inside the key
         * @see #onPressed()
         */
        public void onReleased(boolean inside) {
            pressed = !pressed;
            if (sticky) {
                on = !on;
            }
        }

        private int[] parseCSV(String value) {
            int count = 0;
            int lastIndex = 0;
            if (value.length() > 0) {
                count++;
                while ((lastIndex = value.indexOf(",", lastIndex + 1)) > 0) {
                    count++;
                }
            }
            int[] values = new int[count];
            count = 0;
            StringTokenizer st = new StringTokenizer(value, ",");
            while (st.hasMoreTokens()) {
                try {
                    values[count++] = Integer.parseInt(st.nextToken());
                } catch (NumberFormatException nfe) {
                    Log.e(TAG, "Error parsing keycodes " + value);
                }
            }
            return values;
        }

        /**
         * Detects if a point falls inside this key.
         * @param x the x-coordinate of the point
         * @param y the y-coordinate of the point
         * @return whether or not the point falls inside the key. If the key is attached to an
         * edge, it will assume that all points between the key and the edge are considered to be
         * inside the key.
         */
        public boolean isInside(int x, int y) {
            boolean leftEdge = (edgeFlags & EDGE_LEFT) > 0;
            boolean rightEdge = (edgeFlags & EDGE_RIGHT) > 0;
            boolean topEdge = (edgeFlags & EDGE_TOP) > 0;
            boolean bottomEdge = (edgeFlags & EDGE_BOTTOM) > 0;
            if ((x >= this.x || (leftEdge && x <= this.x + this.width))
                    && (x < this.x + this.width || (rightEdge && x >= this.x))
                    && (y >= this.y || (topEdge && y <= this.y + this.height))
                    && (y < this.y + this.height || (bottomEdge && y >= this.y))) {
                return true;
            } else {
                return false;
            }
        }

        /**
         * Returns the square of the distance between the center of the key and the given point.
         * @param x the x-coordinate of the point
         * @param y the y-coordinate of the point
         * @return the square of the distance of the point from the center of the key
         */
        public int squaredDistanceFrom(int x, int y) {
            // We should count vertical gap between rows to calculate the center of this Key.
            // TODO: We should re-think how we define the center of the key.
            final int verticalGap = keyboard.getVerticalGap();
            int xDist = this.x + width / 2 - x;
            int yDist = this.y + (height + verticalGap) / 2 - y;
            return xDist * xDist + yDist * yDist;
        }

        /**
         * Returns the drawable state for the key, based on the current state and type of the key.
         * @return the drawable state of the key.
         * @see android.graphics.drawable.StateListDrawable#setState(int[])
         */
        public int[] getCurrentDrawableState() {
            int[] states = KEY_STATE_NORMAL;

            if (on) {
                if (pressed) {
                    states = KEY_STATE_PRESSED_ON;
                } else {
                    states = KEY_STATE_NORMAL_ON;
                }
            } else {
                if (sticky) {
                    if (pressed) {
                        states = KEY_STATE_PRESSED_OFF;
                    } else {
                        states = KEY_STATE_NORMAL_OFF;
                    }
                } else {
                    if (pressed) {
                        states = KEY_STATE_PRESSED;
                    }
                }
            }
            return states;
        }
    }

    /**
     * Creates a keyboard from the given xml key layout file.
     * @param context the application or service context
     * @param xmlLayoutResId the resource file that contains the keyboard layout and keys.
     */
    public BaseKeyboard(Context context, int xmlLayoutResId) {
        this(context, xmlLayoutResId, 0);
    }

    /**
     * Creates a keyboard from the given xml key layout file. Weeds out rows
     * that have a keyboard mode defined but don't match the specified mode.
     * @param context the application or service context
     * @param xmlLayoutResId the resource file that contains the keyboard layout and keys.
     * @param modeId keyboard mode identifier
     * @param width sets width of keyboard
     * @param height sets height of keyboard
     */
    public BaseKeyboard(Context context, int xmlLayoutResId, int modeId, int width, int height) {
        Resources res = context.getResources();
        GRID_WIDTH = res.getInteger(R.integer.config_keyboard_grid_width);
        GRID_HEIGHT = res.getInteger(R.integer.config_keyboard_grid_height);
        GRID_SIZE = GRID_WIDTH * GRID_HEIGHT;

        mDisplayWidth = width;
        mDisplayHeight = height;

        mDefaultHorizontalGap = 0;
        setKeyWidth(mDisplayWidth / 10);
        mDefaultVerticalGap = 0;
        mDefaultHeight = mDefaultWidth;
        mKeyboardMode = modeId;
        loadKeyboard(context, xmlLayoutResId);
    }

    /**
     * Creates a keyboard from the given xml key layout file. Weeds out rows
     * that have a keyboard mode defined but don't match the specified mode.
     * @param context the application or service context
     * @param xmlLayoutResId the resource file that contains the keyboard layout and keys.
     * @param modeId keyboard mode identifier
     */
    public BaseKeyboard(Context context, int xmlLayoutResId, int modeId) {
        this(context, xmlLayoutResId, modeId,
                context.getResources().getDisplayMetrics().widthPixels,
                context.getResources().getDisplayMetrics().heightPixels);
    }

    /**
     * <p>Creates a blank keyboard from the given resource file and populates it with the specified
     * characters in left-to-right, top-to-bottom fashion, using the specified number of columns.
     * </p>
     * <p>If the specified number of columns is -1, then the keyboard will fit as many keys as
     * possible in each row.</p>
     * @param context the application or service context
     * @param layoutTemplateResId the layout template file, containing no keys.
     * @param characters the list of characters to display on the keyboard. One key will be created
     * for each character.
     * @param columns the number of columns of keys to display. If this number is greater than the
     * number of keys that can fit in a row, it will be ignored. If this number is -1, the
     * keyboard will fit as many keys as possible in each row.
     */
    public BaseKeyboard(Context context, int layoutTemplateResId,
            CharSequence characters, int columns, int horizontalPadding) {
        this(context, layoutTemplateResId);
        int x = 0;
        int y = 0;
        int column = 0;
        mTotalWidth = 0;

        Row row = new Row(this);
        row.defaultHeight = mDefaultHeight;
        row.defaultWidth = mDefaultWidth;
        row.defaultHorizontalGap = mDefaultHorizontalGap;
        row.verticalGap = mDefaultVerticalGap;
        row.rowEdgeFlags = EDGE_TOP | EDGE_BOTTOM;
        final int maxColumns = columns == -1 ? Integer.MAX_VALUE : columns;
        for (int i = 0; i < characters.length(); i++) {
            char c = characters.charAt(i);
            if (column >= maxColumns
                    || x + mDefaultWidth + horizontalPadding > mDisplayWidth) {
                x = 0;
                y += mDefaultVerticalGap + mDefaultHeight;
                column = 0;
            }
            final Key key = new Key(row);
            key.x = x;
            key.y = y;
            key.label = String.valueOf(c);
            key.codes = new int[] { c };
            column++;
            x += key.width + key.gap;
            mKeys.add(key);
            if (x > mTotalWidth) {
                mTotalWidth = x;
            }
        }
        mTotalHeight = y + mDefaultHeight;
    }

    public List<Key> getKeys() {
        return mKeys;
    }

    protected int getHorizontalGap() {
        return mDefaultHorizontalGap;
    }

    protected void setHorizontalGap(int gap) {
        mDefaultHorizontalGap = gap;
    }

    protected int getVerticalGap() {
        return mDefaultVerticalGap;
    }

    protected void setVerticalGap(int gap) {
        mDefaultVerticalGap = gap;
    }

    protected int getKeyHeight() {
        return mDefaultHeight;
    }

    protected void setKeyHeight(int height) {
        mDefaultHeight = height;
    }

    protected int getKeyWidth() {
        return mDefaultWidth;
    }

    protected void setKeyWidth(int width) {
        mDefaultWidth = width;
        final int threshold = (int) (width * SEARCH_DISTANCE);
        mProximityThreshold = threshold * threshold;
    }

    /**
     * Returns the total height of the keyboard
     * @return the total height of the keyboard
     */
    public int getHeight() {
        return mTotalHeight;
    }

    public int getMinWidth() {
        return mTotalWidth;
    }

    public int getKeyboardHeight() {
        return mDisplayHeight;
    }

    public int getKeyboardWidth() {
        return mDisplayWidth;
    }

    public int getKeyboardMode() {
        return mKeyboardMode;
    }

    public boolean setShifted(boolean shiftState) {
        for (final Key key : mShiftKeys) {
            key.on = shiftState;
        }
        if (mShifted != shiftState) {
            mShifted = shiftState;
            return true;
        }
        return false;
    }

    public boolean isShifted() {
        return mShifted;
    }

    public List<Key> getShiftKeys() {
        return mShiftKeys;
    }

    private void computeNearestNeighbors() {
        // Round-up so we don't have any pixels outside the grid
        mCellWidth = (getMinWidth() + GRID_WIDTH - 1) / GRID_WIDTH;
        mCellHeight = (getHeight() + GRID_HEIGHT - 1) / GRID_HEIGHT;
        mGridNeighbors = new int[GRID_SIZE][];
        int[] indices = new int[mKeys.size()];
        final int gridWidth = GRID_WIDTH * mCellWidth;
        final int gridHeight = GRID_HEIGHT * mCellHeight;
        for (int x = 0; x < gridWidth; x += mCellWidth) {
            for (int y = 0; y < gridHeight; y += mCellHeight) {
                int count = 0;
                for (int i = 0; i < mKeys.size(); i++) {
                    final Key key = mKeys.get(i);
                    final int threshold = mProximityThreshold;
                    if (key.squaredDistanceFrom(x, y) < threshold ||
                            key.squaredDistanceFrom(x + mCellWidth - 1, y) < threshold ||
                            key.squaredDistanceFrom(x + mCellWidth - 1, y + mCellHeight - 1)
                                < threshold ||
                            key.squaredDistanceFrom(x, y + mCellHeight - 1) < threshold) {
                        indices[count++] = i;
                    }
                }
                int [] cell = new int[count];
                System.arraycopy(indices, 0, cell, 0, count);
                mGridNeighbors[(y / mCellHeight) * GRID_WIDTH + (x / mCellWidth)] = cell;
            }
        }
    }

    /**
     * Returns the indices of the keys that are closest to the given point.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the array of integer indices for the nearest keys to the given point. If the given
     * point is out of range, then an array of size zero is returned.
     */
    public int[] getNearestKeys(int x, int y) {
        if (mGridNeighbors == null) computeNearestNeighbors();
        if (x >= 0 && x < getMinWidth() && y >= 0 && y < getHeight()) {
            int index = (y / mCellHeight) * GRID_WIDTH + (x / mCellWidth);
            if (index < GRID_SIZE) {
                return mGridNeighbors[index];
            }
        }
        return new int[0];
    }

    // TODO should be private
    protected BaseKeyboard.Row createRowFromXml(Resources res, XmlResourceParser parser) {
        return new BaseKeyboard.Row(res, this, parser);
    }

    // TODO should be private
    protected BaseKeyboard.Key createKeyFromXml(Resources res, Row parent, int x, int y,
            XmlResourceParser parser) {
        return new BaseKeyboard.Key(res, parent, x, y, parser);
    }

    private void loadKeyboard(Context context, int xmlLayoutResId) {
        try {
            BaseKeyboardParser parser = new BaseKeyboardParser(this, context.getResources());
            parser.parseKeyboard(context.getResources().getXml(xmlLayoutResId));
            // mTotalWidth is the width of this keyboard which is maximum width of row.
            mTotalWidth = parser.getMaxRowWidth();
            mTotalHeight = parser.getTotalHeight();
        } catch (XmlPullParserException e) {
            throw new IllegalArgumentException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected static void setDefaultBounds(Drawable drawable)  {
        if (drawable != null)
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(),
                    drawable.getIntrinsicHeight());
    }
}