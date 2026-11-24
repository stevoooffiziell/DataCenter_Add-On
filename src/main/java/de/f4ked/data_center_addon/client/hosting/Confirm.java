package de.f4ked.data_center_addon.client.hosting;

import java.util.Objects;

/**
 * "Click twice to confirm" for buttons that cost something (cancel a contract, take a loan): the
 * first click arms the button for {@link #WINDOW_MS}, the second one within that time runs the action.
 * One instance per view; {@code key} tells its buttons apart.
 */
final class Confirm {
    private static final long WINDOW_MS = 3000;

    private Object key;
    private long until;

    /** Whether the button with this key was clicked once and waits for the second click. */
    boolean armed(Object key) {
        return Objects.equals(this.key, key) && System.currentTimeMillis() < until;
    }

    void click(Object key, Runnable action) {
        if (armed(key)) {
            this.key = null;
            action.run();
        } else {
            this.key = key;
            until = System.currentTimeMillis() + WINDOW_MS;
        }
    }
}
