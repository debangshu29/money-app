package com.debangshu.money;

import java.util.Calendar;
import java.util.Locale;

/** Rupees the Indian way (₹1,25,000), and friendly dates. Plain Java. */
public final class Fmt {

    private Fmt() {}

    public static boolean hide = false;   // "hide amounts" (eye button)

    /** ₹1,25,000 or ₹1,234.50 */
    public static String rs(long paise) {
        if (hide) return "₹ ••••";
        return plain(paise);
    }

    public static String plain(long paise) {
        boolean neg = paise < 0;
        long p = Math.abs(paise);
        long rupees = p / 100, frac = p % 100;
        String s = "₹" + group(rupees) + (frac == 0 ? "" : String.format(Locale.ROOT, ".%02d", frac));
        return neg ? "−" + s : s;
    }

    /** With a sign: +₹500 / −₹500 */
    public static String signed(long paise) {
        if (hide) return "₹ ••••";
        return (paise >= 0 ? "+" : "−") + plain(Math.abs(paise));
    }

    /** Short: ₹950, ₹12.5K, ₹1.2L, ₹2.3Cr */
    public static String shortRs(long paise) {
        if (hide) return "₹••";
        long r = Math.abs(paise) / 100;
        String sign = paise < 0 ? "−" : "";
        if (r < 1000) return sign + "₹" + r;
        if (r < 100000) return sign + "₹" + trim(r / 1000.0) + "K";
        if (r < 10000000) return sign + "₹" + trim(r / 100000.0) + "L";
        return sign + "₹" + trim(r / 10000000.0) + "Cr";
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, v >= 100 ? "%.0f" : "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** 1234567 → 12,34,567 */
    public static String group(long n) {
        String s = Long.toString(n);
        if (s.length() <= 3) return s;
        String last3 = s.substring(s.length() - 3);
        String rest = s.substring(0, s.length() - 3);
        StringBuilder b = new StringBuilder();
        int first = rest.length() % 2;
        if (first == 1) b.append(rest.charAt(0));
        for (int i = first; i < rest.length(); i += 2) {
            if (b.length() > 0) b.append(',');
            b.append(rest, i, i + 2);
        }
        return b.append(',').append(last3).toString();
    }

    // ------------------------------------------------------------------ dates

    static final String[] MON = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    static final String[] MONTH = {"January", "February", "March", "April", "May", "June", "July", "August",
            "September", "October", "November", "December"};
    static final String[] DAY = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    public static Calendar cal(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return c;
    }

    public static long startOfDay(long t) {
        Calendar c = cal(t);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static long startOfMonth(long t) {
        Calendar c = cal(startOfDay(t));
        c.set(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    public static long addMonths(long t, int n) {
        Calendar c = cal(t);
        c.add(Calendar.MONTH, n);
        return c.getTimeInMillis();
    }

    public static long addDays(long t, int n) {
        Calendar c = cal(t);
        c.add(Calendar.DAY_OF_MONTH, n);
        return c.getTimeInMillis();
    }

    public static int daysInMonth(long t) {
        return cal(t).getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    public static String time(long t) {
        Calendar c = cal(t);
        int h = c.get(Calendar.HOUR_OF_DAY), m = c.get(Calendar.MINUTE);
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format(Locale.ROOT, "%d:%02d %s", h12, m, h < 12 ? "am" : "pm");
    }

    /** "Today", "Yesterday", "Mon, 10 Nov", "10 Nov 2025" */
    public static String day(long t) {
        long today = startOfDay(System.currentTimeMillis());
        long d = startOfDay(t);
        if (d == today) return "Today";
        if (d == addDays(today, -1)) return "Yesterday";
        Calendar c = cal(t);
        String s = c.get(Calendar.DAY_OF_MONTH) + " " + MON[c.get(Calendar.MONTH)];
        if (c.get(Calendar.YEAR) != cal(today).get(Calendar.YEAR)) return s + " " + c.get(Calendar.YEAR);
        return DAY[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + s;
    }

    public static String date(long t) {
        Calendar c = cal(t);
        return c.get(Calendar.DAY_OF_MONTH) + " " + MON[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR);
    }

    public static String dayTime(long t) { return day(t) + ", " + time(t); }

    public static String month(long t) {
        Calendar c = cal(t);
        return MONTH[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR);
    }

    public static String monthShort(long t) { return MON[cal(t).get(Calendar.MONTH)]; }

    /** "in 3 days", "tomorrow", "today", "2 days ago" */
    public static String rel(long t) {
        long days = Math.round((startOfDay(t) - startOfDay(System.currentTimeMillis())) / 86400000.0);
        if (days == 0) return "today";
        if (days == 1) return "tomorrow";
        if (days == -1) return "yesterday";
        return days > 0 ? "in " + days + " days" : (-days) + " days ago";
    }

    public static String title(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        boolean up = true;
        for (char ch : s.toCharArray()) {
            if (Character.isLetter(ch)) { b.append(up ? Character.toUpperCase(ch) : Character.toLowerCase(ch)); up = false; }
            else { b.append(ch); up = ch == ' ' || ch == '-' || ch == '.' || ch == '(' || ch == '/'; }
        }
        return b.toString();
    }
}
