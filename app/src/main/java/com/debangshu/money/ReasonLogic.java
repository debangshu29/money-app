package com.debangshu.money;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** When to ask "what for?" and which one-tap reasons to offer (plain Java, tested on a computer). */
public final class ReasonLogic {

    private ReasonLogic() {}

    private static final long DAY = 86400000L;

    /** Earlier payments to the same shop / person (newest first). */
    static List<Txn> before(List<Txn> all, Txn t) {
        String k = Cats.key(t);
        List<Txn> out = new ArrayList<>();
        if (k.isEmpty()) return out;
        for (Txn x : all) {
            if (x == t || x.id.equals(t.id) || x.credit != t.credit || x.kind == Txn.IGNORED || x.kind == Txn.SNAPSHOT) continue;
            if (x.time >= t.time || t.time - x.time > 180 * DAY) continue;
            if (k.equals(Cats.key(x))) out.add(x);
        }
        java.util.Collections.sort(out, (a, b) -> Long.compare(b.time, a.time));
        return out;
    }

    /** A regular (same place, usual amount, you've paid there before) → don't ask. New person / place or an odd amount → ask. */
    public static boolean unusual(List<Txn> all, Txn t) {
        if (t.unnamed()) return true;
        List<Txn> prev = before(all, t);
        if (prev.size() < 2) return true;
        List<Long> amts = new ArrayList<>();
        for (Txn x : prev) amts.add(x.amount);
        java.util.Collections.sort(amts);
        long med = amts.get(amts.size() / 2);
        if (t.amount > Math.max(3 * med, med + 50000)) return true;      // much bigger than usual
        boolean known = false;
        for (Txn x : prev) if (!x.note.isEmpty() || (!Cats.OTHER.equals(x.category) && !x.category.isEmpty())) { known = true; break; }
        return !known;
    }

    /** The note you always give this payee (2+ times, most of the time) → filled in by itself. */
    public static String autoNote(List<Txn> all, Txn t) {
        List<Txn> prev = before(all, t);
        Map<String, Integer> n = new HashMap<>();
        int withNote = 0;
        for (Txn x : prev) {
            if (x.note.isEmpty() || x.note.startsWith("Failed payment") || x.note.startsWith("Money back")) continue;
            withNote++;
            String k = x.note.trim();
            n.put(k, (n.containsKey(k) ? n.get(k) : 0) + 1);
        }
        for (Map.Entry<String, Integer> e : n.entrySet())
            if (e.getValue() >= 2 && e.getValue() * 10 >= withNote * 6 && withNote * 10 >= prev.size() * 5) return e.getKey();
        return null;
    }

    /** One-tap reasons: {label, note, category}. */
    public static List<String[]> suggest(List<Txn> all, Txn t, int max) {
        LinkedHashMap<String, String[]> out = new LinkedHashMap<>();
        // what you wrote for this payee before
        for (Txn x : before(all, t)) {
            if (x.note.isEmpty() || x.note.startsWith("Failed payment") || x.note.startsWith("Money back")) continue;
            String lbl = "📝 " + (x.note.length() > 18 ? x.note.substring(0, 17) + "…" : x.note);
            if (!out.containsKey(lbl.toLowerCase(Locale.ROOT))) out.put(lbl.toLowerCase(Locale.ROOT), new String[]{lbl, x.note, x.category});
            if (out.size() >= max) break;
        }
        boolean person = Cats.PEOPLE.equals(t.category) || Cats.looksLikePerson(t);
        if (person) {
            add(out, "👥 Split", "Split", Cats.PEOPLE);
            add(out, "🤝 Lent", "Lent", Cats.PEOPLE);
            add(out, "🎁 Gift", "Gift", Cats.GIFTS);
        }
        // the category it already looks like
        if (!t.category.isEmpty() && !Cats.OTHER.equals(t.category) && !Cats.PEOPLE.equals(t.category) && !Cats.UNRECORDED.equals(t.category))
            add(out, Cats.emoji(t.category) + " " + shortCat(t.category), "", t.category);
        // what you usually pay for at this time of day
        int h = Fmt.cal(t.time).get(java.util.Calendar.HOUR_OF_DAY);
        Map<String, Integer> score = new HashMap<>();
        for (Txn x : all) {
            if (!x.isSpend() || x.time > t.time || t.time - x.time > 90 * DAY) continue;
            String c = x.category;
            if (c.isEmpty() || Cats.OTHER.equals(c) || Cats.PEOPLE.equals(c) || Cats.UNRECORDED.equals(c) || Cats.CASH.equals(c)) continue;
            int xh = Fmt.cal(x.time).get(java.util.Calendar.HOUR_OF_DAY);
            int w = Math.abs(xh - h) <= 2 ? 3 : 1;
            score.put(c, (score.containsKey(c) ? score.get(c) : 0) + w);
        }
        List<Map.Entry<String, Integer>> es = new ArrayList<>(score.entrySet());
        java.util.Collections.sort(es, (a, b) -> b.getValue() - a.getValue());
        for (Map.Entry<String, Integer> e : es) add(out, Cats.emoji(e.getKey()) + " " + shortCat(e.getKey()), "", e.getKey());
        for (String c : new String[]{Cats.FOOD, Cats.GROCERY, Cats.TRANSPORT, Cats.SHOP})
            add(out, Cats.emoji(c) + " " + shortCat(c), "", c);
        List<String[]> l = new ArrayList<>(out.values());
        return l.size() > max ? l.subList(0, max) : l;
    }

    private static void add(Map<String, String[]> m, String label, String note, String cat) {
        String k = label.toLowerCase(Locale.ROOT);
        if (!m.containsKey(k)) m.put(k, new String[]{label, note, cat});
    }

    static String shortCat(String c) {
        int i = c.indexOf(" & ");
        return i > 0 ? c.substring(0, i) : c;
    }

    /** "1 Ramesh for test" / "₹250 sharma kirana - vegetables" → {paise, name, reason}. */
    public static Object[] paidText(String s) {
        if (s == null) return null;
        String t = s.trim().replace("₹", " ").replaceAll("(?i)\\b(?:rs\\.?|inr|rupees?)\\b", " ").replaceAll("\\s+", " ").trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]{1,2})?)").matcher(t);
        if (!m.find()) return null;
        long paise = SmsParser.paise(m.group(1).replace(",", ""));
        if (paise <= 0) return null;
        String rest = (t.substring(0, m.start()) + " " + t.substring(m.end())).replaceAll("(?i)^\\s*(?:paid|sent|gave|to)\\b", " ")
                .replaceAll("(?i)^\\s*to\\b", " ").replaceAll("\\s+", " ").trim();
        String name = rest, reason = "";
        java.util.regex.Matcher sp = java.util.regex.Pattern.compile("(?i)\\s+for\\s+|\\s+-\\s+|\\s*,\\s*|\\s+—\\s+").matcher(rest);
        if (sp.find()) { name = rest.substring(0, sp.start()).trim(); reason = rest.substring(sp.end()).trim(); }
        name = name.replaceAll("(?i)^(?:to)\\s+", "").trim();
        if (!name.isEmpty()) name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return new Object[]{paise, name, reason};
    }

    /** "Ramesh - test" / "Ramesh for test" → {name, reason}. */
    public static String[] nameAndReason(String s) {
        if (s == null) return new String[]{"", ""};
        String t = s.trim();
        java.util.regex.Matcher sp = java.util.regex.Pattern.compile("(?i)\\s+for\\s+|\\s+-\\s+|\\s*,\\s*|\\s+—\\s+").matcher(t);
        if (sp.find()) return new String[]{t.substring(0, sp.start()).trim(), t.substring(sp.end()).trim()};
        return new String[]{t, ""};
    }
}
