package ru.aeroclub.informer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** Shared UI tokens and shape helpers used by the app's screens. */
public final class AeroclubUi {
    public static final int BACKGROUND = Color.rgb(245, 247, 250);
    public static final int WHITE = Color.WHITE;
    public static final int NAVY = Color.rgb(23, 43, 77);
    public static final int MUTED = Color.rgb(82, 100, 119);
    public static final int BLUE = Color.rgb(25, 94, 168);
    public static final int GREEN = Color.rgb(19, 117, 71);
    public static final int BORDER = Color.rgb(220, 228, 236);
    public static final int BODY = Color.rgb(52, 70, 90);

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
