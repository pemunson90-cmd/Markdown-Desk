package com.example.markdowndesk;

/** Bounded source-pane weight adjustments shared by keyboard and accessibility. */
final class SplitResizeController {
    private SplitResizeController() {}

    /** Negative/positive direction moves by five percent; zero centers the panes. */
    static float adjust(float ratio, int direction) {
        if (direction == 0) return 0.5f;
        return Math.max(0.28f, Math.min(0.72f, ratio + (direction > 0 ? 0.05f : -0.05f)));
    }
}
