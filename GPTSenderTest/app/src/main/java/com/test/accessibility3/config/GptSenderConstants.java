package com.test.accessibility3.config;

/**
 * Central constants for GPTSenderTest.
 *
 * Keep project-specific coordinates and package identifiers here.
 * Shared modules must receive these values as parameters rather than hard-code them.
 * Screen coordinates are physical display pixels and must be rechecked after layout,
 * orientation, display scaling, or navigation changes.
 */
public final class GptSenderConstants {
    private GptSenderConstants() { }

    public static final class Packages {
        public static final String CHATGPT = "com.openai.chatgpt";
        private Packages() { }
    }

    public static final class Targets {
        public static final String CHATGPT_SEND_ID = "CHATGPT_SEND";
        private Targets() { }
    }

    public static final class Coordinates {
        /** User-confirmed ChatGPT send target, in physical display pixels: x, y. */
        public static final int CHATGPT_SEND_X = 965;
        public static final int CHATGPT_SEND_Y = 2125;

        /** Input coordinates intentionally omitted: input must be found through Accessibility. */
        private Coordinates() { }
    }

    public static final class Timing {
        /** Wait after inserting a command before attempting the send action. */
        public static final long BEFORE_SEND_MS = 1000L;
        private Timing() { }
    }

    public static final class Debug {
        public static final boolean ENABLED_BY_DEFAULT = true;
        private Debug() { }
    }
}
