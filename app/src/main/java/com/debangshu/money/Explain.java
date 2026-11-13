package com.debangshu.money;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Grow's knowledge, said simply — the way you'd explain it to a 5-year-old.
 * Every Grow screen starts with one of these boxes ("What is this?"). It's open the first time; after you close it,
 * a slim "What is this?" row brings it back.
 */
final class Explain {

    private Explain() {}

    /** key → {title, simple explanation} */
    static final String[][] TOPICS = {
            {"rule", "What is 50 / 30 / 20?",
                    "Think of your salary as 10 laddoos 🍬\n\n"
                            + "🏠 Needs — things you can't live without: home, food, electricity, bus fare, medicine, EMIs.\n"
                            + "🎉 Wants — nice extras: eating out, movies, new clothes, a new phone.\n"
                            + "🐷 Saved — laddoos you hide in a box for “future you”.\n\n"
                            + "A rule simply says how many laddoos go in each plate. Tap a rule below to see what it would change for you."},
            {"health", "What is money health?",
                    "It's like a doctor's check-up, but for your money 🩺\n\n"
                            + "Money does five small tests — saving, emergency fund, EMIs, budget and goals. Each test is out of 20.\n"
                            + "Add them up: 100 means your money is super healthy. Below 45 means it needs some care — and each test tells you one easy thing to do."},
            {"runway", "What is a runway?",
                    "Imagine your salary stopped tomorrow ✈️\n\n"
                            + "How many months could you still pay for everything with the money you have? That's your runway — like how far a plane can fly with the fuel already in its tank.\n\n"
                            + "6 months is a full tank. Less than 1 month means even a small problem could push you into a loan."},
            {"monthend", "How does Money guess the month end?",
                    "Money looks at what you usually spend in a day, adds the bills still coming this month, and takes it away from what you have now 🔮\n\n"
                            + "It's a guess, not a promise — like a weather forecast for your wallet."},
            {"leaks", "What is a money leak?",
                    "A dripping tap loses only a drop at a time 💧 — but by morning, the bucket is full.\n\n"
                            + "Small spends work the same way: ₹40 tea, ₹30 delivery fee, a ₹149 app you forgot about. Each one feels tiny. Together they can be thousands a year.\n\n"
                            + "Fixing a leak doesn't mean never enjoying anything — just stopping the drops you don't even notice."},
            {"wait", "Why a wait-list?",
                    "When you really, really want something, put it on a shelf for a few days 🧸\n\n"
                            + "If you still want it after the wait, it's a real want — buy it happily!\n"
                            + "If you forgot about it, it was just a “shiny thing” moment — and you saved that money.\n\n"
                            + "Shops use “only today!” offers because they know waiting makes us change our mind."},
            {"habit", "What is the true price of a habit?",
                    "₹50 a day doesn't feel like much 🥤\n\n"
                            + "But it's ₹1,500 a month… ₹18,000 a year… And if that money went into a piggy bank that adds a little extra every year (interest), after 10 years it's more than ₹3 lakh.\n\n"
                            + "You don't have to stop — even cutting a few times a month makes a big difference. Slide and see."},
            {"hours", "What does “hours of work” mean?",
                    "Every rupee you earn costs a little bit of your time ⏰\n\n"
                            + "If you earn ₹200 an hour, a ₹3,000 pair of shoes isn't just ₹3,000 — it's 15 hours of your work.\n\n"
                            + "Before buying, ask: “Would I work 15 hours for this?” Sometimes yes! Sometimes… no."},
            {"future", "What is “Future you”?",
                    "Imagine planting a money tree 🌱\n\n"
                            + "Every month you plant a seed — your saving. Seeds grow into trees, and trees drop more seeds (that's interest earning interest).\n\n"
                            + "One day the tree gives you enough fruit to live on without working. The amount you need for that day is your freedom number."},
            {"freedom", "Why 25 years of spending?",
                    "Imagine 25 boxes 📦, each holding one year of what you spend.\n\n"
                            + "If your money keeps growing a little every year, you can open one box a year to live on — and the growth keeps refilling them. Done right, the boxes almost never run out.\n\n"
                            + "That's the “4% rule”: taking out 4% a year from 25 boxes = 1 box. It's a rule of thumb, not a promise."},
            {"salary", "Why split on salary day?",
                    "On salary day, fill the boxes in this order 📥\n\n"
                            + "1. 🐷 Future-you box first (goals and saving)\n"
                            + "2. 🏠 Must-pay box (rent, bills, food)\n"
                            + "3. 🎉 Whatever is left is fun money — spend it without guilt!\n\n"
                            + "If you save “whatever is left at the end of the month”, usually nothing is left. Saving first makes it automatic."},
            {"jar", "What is a round-up jar?",
                    "When you pay ₹47, pretend it was ₹50 and drop the ₹3 change into a jar 🫙\n\n"
                            + "You'll never miss ₹3. But after 100 payments, the jar has hundreds of rupees — money you saved without trying."},
            {"emergency", "What is an emergency fund?",
                    "It's an umbrella for rainy days ☂️\n\n"
                            + "If you suddenly lose a job, someone falls sick, or the bike breaks down, this money keeps you dry — so you don't need a loan or a credit card.\n\n"
                            + "Keep it somewhere safe and easy to take out (savings account or FD), and only use it for real emergencies."},
            {"where", "Where should goal money sit?",
                    "Think of it like travel 🚲🚌🚆\n\n"
                            + "For a short trip you walk or cycle — slow but safe. For a long trip you take a train — faster, with a few bumps.\n\n"
                            + "Money is the same: money you need soon stays somewhere safe; money you need after many years can take the faster, bumpier road (shares) because it has time to recover from the bumps."},
            {"split", "How does “Split my saving” work?",
                    "You have one pot of saving and many goals 🍕\n\n"
                            + "Money cuts the pot like a pizza: goals with a closer date get their slice first, then the rest is shared fairly. Slide the amount and see when each goal would be done."},
            {"goals", "How do goals work?",
                    "A goal is a jar with a name on it — “Laptop”, “Puja”, “Trip” 🏺\n\n"
                            + "Tell Money how much and by when. If the money sits in a bank account, Money watches that balance and tells you if you're ahead, on track or behind — all by itself."},
    };

    static String[] topic(String key) {
        for (String[] t : TOPICS) if (t[0].equals(key)) return t;
        return null;
    }

    /** The "explain it simply" box for a screen. Open the first time; closed later as a slim row. */
    static View box(Context c, String key) {
        String[] t = topic(key);
        if (t == null) return new View(c);
        LinearLayout wrap = Ui.vbox(c);
        paint(c, wrap, t, !Store.get(c).prefs().getBoolean("kid_" + key, false));
        return wrap;
    }

    private static void paint(Context c, LinearLayout wrap, String[] t, boolean open) {
        wrap.removeAllViews();
        if (!open) {
            LinearLayout row = Ui.hbox(c);
            row.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
            row.setBackground(Ui.rippleRound(c, Ui.col(c, R.color.surface), 16));
            row.addView(Ui.iconBadge(c, "💡", 0xFFF59E0B, 28));
            TextView l = Ui.text(c, t[1], 14, Ui.SEMIBOLD);
            l.setPadding(Ui.dp(c, 10), 0, 0, 0);
            row.addView(l, Ui.weight1());
            row.addView(Ui.chevron(c));
            row.setOnClickListener(v -> { Store.get(c).prefs().edit().putBoolean("kid_" + t[0], false).apply(); paint(c, wrap, t, true); });
            wrap.addView(row);
            return;
        }
        LinearLayout card = Ui.vbox(c);
        card.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 12));
        card.setBackground(Ui.round(0x1FF59E0B, Ui.dp(c, 20)));
        LinearLayout h = Ui.hbox(c);
        h.addView(Ui.iconBadge(c, "💡", 0xFFF59E0B, 32));
        TextView ti = Ui.text(c, t[1], 15.5f, Ui.BOLD);
        ti.setPadding(Ui.dp(c, 10), 0, 0, 0);
        h.addView(ti, Ui.weight1());
        card.addView(h);
        TextView body = Ui.text(c, t[2], 14.5f, Ui.col(c, R.color.text), Ui.REGULAR);
        body.setLineSpacing(Ui.dp(c, 3), 1f);
        body.setPadding(0, Ui.dp(c, 10), 0, 0);
        card.addView(body);
        TextView got = Ui.text(c, "Got it — hide", 13, Ui.col(c, R.color.accent), Ui.BOLD);
        got.setGravity(Gravity.END);
        got.setPadding(0, Ui.dp(c, 8), 0, 0);
        got.setOnClickListener(v -> { Store.get(c).prefs().edit().putBoolean("kid_" + t[0], true).apply(); paint(c, wrap, t, false); });
        card.addView(got);
        wrap.addView(card);
    }

    /** The same explanation in a sheet (for small "?" buttons). */
    static void sheet(android.app.Activity a, String key) {
        String[] t = topic(key);
        if (t == null) return;
        TextView body = Ui.text(a, t[2], 15, Ui.col(a, R.color.text), Ui.REGULAR);
        body.setLineSpacing(Ui.dp(a, 3), 1f);
        int p = Ui.dp(a, 22);
        body.setPadding(p, 0, p, Ui.dp(a, 8));
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(body);
        Ui.Popup pop = new Ui.Popup(a);
        pop.setTitle(t[1]);
        pop.setView(sv).setPositiveButton("Got it", null);
        pop.sheet().show();
    }

    // ------------------------------------------------------------------ the four rules, simply

    /** For each rule in SaveMath.RULES: {the story, who it's good for, the hard part} */
    static final String[][] RULE_STORY = {
            {"Split your salary into 10 laddoos 🍬\n\n5 laddoos 🏠 for things you must have — rent, food, bills, bus.\n3 laddoos 🎉 for fun — eating out, movies, shopping.\n2 laddoos 🐷 go in a box for later.\n\nEvery month the box gets a little fuller — without you even feeling it.",
                    "Most people with a steady salary. It's the classic place to start.",
                    "If rent alone eats half your salary, needs won't fit in 5 laddoos — try 60/20/20 instead."},
            {"Same 10 laddoos — but big-city life costs more 🏙️\n\n6 laddoos 🏠 for must-haves (rent is high!).\n2 laddoos 🎉 for fun.\n2 laddoos 🐷 still go in the box.\n\nYou keep saving the same, you just have a little less for fun.",
                    "People paying high rent, or supporting family at home.",
                    "Less fun money — so spend it on fun you really love."},
            {"Money is tight right now — first job, studies or a hard month 🌱\n\n7 laddoos 🏠 for must-haves.\n2 laddoos 🎉 for fun.\n1 laddoo 🐷 in the box.\n\nOne laddoo is small — but one is SO much better than none. It builds the habit.",
                    "Students, first jobs, or months with big family costs.",
                    "Goals take longer. Every time your salary grows, move up to 60/20/20."},
            {"On salary day, before you buy anything, hide 2 laddoos in the box 🐷\n\nThe other 8 laddoos are yours — spend them however you like. No counting needs and wants!\n\nSaving happens first, so it never gets forgotten.",
                    "People who don't like tracking every rupee.",
                    "You must really move the money on salary day — Money's salary-day notification reminds you."},
    };
}
