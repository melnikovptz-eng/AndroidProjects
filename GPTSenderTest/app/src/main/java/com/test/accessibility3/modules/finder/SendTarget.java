package com.test.accessibility3.modules.finder;

import com.test.accessibility3.config.GptSenderConstants;

/**
 * Backward-compatible target adapter.
 * The canonical project-specific values live in GptSenderConstants.
 */
public final class SendTarget {
    public static final String ID = GptSenderConstants.Targets.CHATGPT_SEND_ID;
    public static final String PACKAGE_NAME = GptSenderConstants.Packages.CHATGPT;
    public static final int X = GptSenderConstants.Coordinates.CHATGPT_SEND_X;
    public static final int Y = GptSenderConstants.Coordinates.CHATGPT_SEND_Y;

    private SendTarget() { }
}
