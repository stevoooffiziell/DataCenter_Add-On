package de.f4ked.data_center_addon.hosting.reputation;

/**
 * The Ruf: how good an account's service has been over the last {@link #WINDOW_DAYS} in-game days,
 * from 0 to 100 %. It is a sliding value of the actual quality, not a score that grows: with the same
 * quality it stays at the same level.
 * <pre>Ruf = 1 − 5 × downtime − 0.5 × overload − 0.5 × throttling − incidents</pre>
 * <ul>
 *   <li><b>Downtime</b>: share of servers (or colocation customers) that did not run. 99 % uptime costs 5 %.</li>
 *   <li><b>Overload</b>: how far serving servers ran over their real capacity.</li>
 *   <li><b>Throttling</b>: share of the serving that ran throttled (heat, attack, a network too slow).</li>
 *   <li><b>Incidents</b>: a crash is a serious incident. It presses the Ruf by {@link #INCIDENT} at once,
 *       fading out over {@link #WINDOW_DAYS} days.</li>
 * </ul>
 * All hosting racks of the account count, dedicated servers too. Without any service yet the Ruf is 100 %.
 */
public final class Ruf {
    public static final int WINDOW_DAYS = 30;
    /** Fields of a day record. */
    static final int SERVED = 0, DOWN = 1, OVERLOAD = 2, THROTTLED = 3, CRASHES = 4, FIELDS = 5;
    static final double W_DOWN = 5.0, W_OVERLOAD = 0.5, W_THROTTLE = 0.5;
    /** What a serious incident (a crash) takes from the Ruf at once; it fades out over the window. */
    public static final double INCIDENT = 0.08;

    private Ruf() {
    }

    /**
     * The service of the window.
     *
     * @param uptime   share of servers that ran (1 without data)
     * @param overload how far serving servers ran over capacity
     * @param throttle share of the serving that ran throttled
     * @param crashes  crashes in the window
     * @param days     days with service in the window
     * @param incidents what the incidents of the window still take from the Ruf
     */
    public record Figures(double uptime, double overload, double throttle, int crashes, int days, double incidents) {
        public boolean measured() {
            return days > 0;
        }

        /** The Ruf, 0..1. */
        public double ruf() {
            if (!measured()) {
                return Math.max(0, 1 - incidents);
            }
            double v = 1 - W_DOWN * (1 - uptime) - W_OVERLOAD * overload - W_THROTTLE * throttle - incidents;
            return Math.max(0, Math.min(1, v));
        }
    }

    /** The figures of the finished days in the window and the day being recorded. */
    public static Figures figures(ReputationBook.Account a) {
        double[] sum = new double[FIELDS];
        int days = 0;
        for (double[] d : a.days) {
            days += add(sum, d);
        }
        days += add(sum, a.today);
        double total = sum[SERVED] + sum[DOWN];
        double served = sum[SERVED];
        return new Figures(total <= 0 ? 1 : served / total, served <= 0 ? 0 : sum[OVERLOAD] / served,
                served <= 0 ? 0 : sum[THROTTLED] / served, (int) Math.round(sum[CRASHES]), days, incidents(a));
    }

    /** What the incidents of the window still take: each {@link #INCIDENT}, fading linearly over the window. */
    static double incidents(ReputationBook.Account a) {
        double sum = 0;
        for (long day : a.incidents) {
            sum += INCIDENT * Math.max(0, 1 - (double) (a.day - day) / WINDOW_DAYS);
        }
        return sum;
    }

    private static int add(double[] sum, double[] day) {
        for (int i = 0; i < FIELDS; i++) {
            sum[i] += day[i];
        }
        return day[SERVED] + day[DOWN] > 0 ? 1 : 0;
    }

    /** Factor on the reachable share of the markets: ×0.5 at 0 % Ruf up to ×1 at 100 %. */
    public static double reachFactor(double ruf) {
        return 0.5 + 0.5 * Math.max(0, Math.min(1, ruf));
    }
}
