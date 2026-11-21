package ru.aeroclub.informer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** Shared UI tokens and shape helpers used by the app's screens. */
public final class AeroclubUi {
    public static final int BACKGROUND = Color.rgb(245, 247, 250);
    public static final int WHITE = Color.WHITE;
    public static final int NAVY = Color.rgb(31, 41, 55);
    public static final int MUTED = Color.rgb(156, 163, 175);
    public static final int BLUE = Color.rgb(0, 102, 255);
    public static final int GREEN = Color.rgb(0, 200, 83);
    public static final int BORDER = Color.rgb(229, 231, 235);
    public static final int BODY = Color.rgb(55, 65, 81);

    private AeroclubUi() {}

    public static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static GradientDrawable rounded(Context context, int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setStroke(dp(context, 1), strokeColor);
        return drawable;
    }
}
