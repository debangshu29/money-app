package com.debangshu.money;

import java.util.Locale;

/** Categories and the automatic guess (Swiggy → Food, Uber → Transport…). Plain Java. */
public final class Cats {

    private Cats() {}

    public static final String FOOD = "Food & Dining", GROCERY = "Groceries", SHOP = "Shopping", TRANSPORT = "Transport",
            FUEL = "Fuel", TRAVEL = "Travel", BILLS = "Bills & Utilities", RECHARGE = "Recharge", SUBS = "Subscriptions",
            FUN = "Entertainment", HEALTH = "Health", EDU = "Education", HOME = "Rent & Home", EMI = "EMI & Loans",
            INVEST = "Investments", INSURE = "Insurance", CASH = "Cash", PEOPLE = "People", PERSONAL = "Personal care",
            FEES = "Fees & Charges", GIFTS = "Gifts & Donations", INCOME = "Income", SALARY = "Salary", REFUND = "Refunds",
            TRANSFER = "Self transfer", CARDBILL = "Card bill payment", OTHER = "Other", UNRECORDED = "Unrecorded";

    /** name, emoji, colour */
    public static final Object[][] ALL = {
            {FOOD, "🍔", 0xFFF97316}, {GROCERY, "🛒", 0xFF22C55E}, {SHOP, "🛍️", 0xFFEC4899}, {TRANSPORT, "🚕", 0xFFEAB308},
            {FUEL, "⛽", 0xFFF59E0B}, {TRAVEL, "✈️", 0xFF06B6D4}, {BILLS, "💡", 0xFF3B82F6}, {RECHARGE, "📱", 0xFF6366F1},
            {SUBS, "📺", 0xFF8B5CF6}, {FUN, "🎬", 0xFFD946EF}, {HEALTH, "💊", 0xFFEF4444}, {EDU, "📚", 0xFF0EA5E9},
            {HOME, "🏠", 0xFF84CC16}, {EMI, "🏦", 0xFF64748B}, {INVEST, "📈", 0xFF10B981}, {INSURE, "🛡️", 0xFF14B8A6},
            {CASH, "💵", 0xFF65A30D}, {PEOPLE, "👥", 0xFFA855F7}, {PERSONAL, "💇", 0xFFF472B6}, {FEES, "🧾", 0xFF78716C},
            {GIFTS, "🎁", 0xFFE11D48}, {OTHER, "❓", 0xFF94A3B8}, {UNRECORDED, "⚠️", 0xFFF59E0B},
            {SALARY, "💼", 0xFF16A34A}, {INCOME, "💰", 0xFF15803D}, {REFUND, "↩️", 0xFF0D9488},
            {TRANSFER, "🔁", 0xFF94A3B8}, {CARDBILL, "💳", 0xFF94A3B8},
    };

    public static String emoji(String cat) {
        for (Object[] c : ALL) if (c[0].equals(cat)) return (String) c[1];
        return "❓";
    }

    public static int color(String cat) {
        for (Object[] c : ALL) if (c[0].equals(cat)) return (Integer) c[2];
        int h = cat == null ? 0 : cat.hashCode();
        return 0xFF000000 | (h & 0x7F7F7F) | 0x404040;
    }

    /** Categories you can pick for money going out / coming in. */
    public static String[] spendNames() {
        return new String[]{FOOD, GROCERY, SHOP, TRANSPORT, FUEL, TRAVEL, BILLS, RECHARGE, SUBS, FUN, HEALTH, EDU, HOME,
                EMI, INVEST, INSURE, CASH, PEOPLE, PERSONAL, FEES, GIFTS, OTHER};
    }

    public static String[] incomeNames() {
        return new String[]{SALARY, INCOME, REFUND, PEOPLE, INVEST, OTHER};
    }

    // ------------------------------------------------------------------ guessing

    private static final String[][] RULES = {
            {SUBS, "netflix|spotify|prime video|amazon prime|hotstar|disney|jiocinema|jio cinema|sonyliv|zee5|youtube|google play|"
                    + "google one|apple\\.com|icloud|itunes|microsoft|office 365|adobe|canva|chatgpt|openai|anthropic|claude|notion|"
                    + "linkedin|audible|kindle|gaana|wynk|jiosaavn|apple music|swiggy one|zomato gold|subscription|membership"},
            {FOOD, "swiggy|zomato|eatsure|domino|pizza|mcdonald|kfc|burger|subway|starbucks|cafe|coffee|chai|tea stall|restaurant|"
                    + "dhaba|biryani|bakery|sweets|haldiram|barbeque|food|kitchen|hotel |dining|eatery|juice|canteen|mess\\b|boba|wow momo|faasos|box8|behrouz|chaayos|ccd|dunkin|baskin|naturals ice"},
            {GROCERY, "blinkit|grofers|zepto|bigbasket|big basket|instamart|dmart|d-mart|jiomart|reliance fresh|reliance smart|more retail|"
                    + "spencer|nature.?s basket|kirana|grocery|supermarket|provision|milk|dairy|amul|mother dairy|country delight|milkbasket|vegetable|fruits|bb now|swiggy instamart|kpn fresh|star bazaar|vishal mega"},
            {SHOP, "amazon|flipkart|myntra|ajio|meesho|nykaa|tata cliq|snapdeal|shopsy|lifestyle|westside|pantaloons|max fashion|zudio|"
                    + "decathlon|ikea|croma|reliance digital|vijay sales|mall|store|mart\\b|retail|trends|bata|puma|nike|adidas|lenskart|firstcry|purplle|boat|apple|samsung|mi store|shoppers stop|h&m|uniqlo|trent"},
            {TRANSPORT, "uber|\\bola\\b|olacabs|rapido|namma yatri|blusmart|metro|dmrc|bmrcl|kmrl|irctc|railway|indian rail|redbus|abhibus|"
                    + "\\bbus\\b|\\bauto\\b|\\bcab\\b|taxi|parking|fastag|toll|nhai|yulu|bounce|quick ride|m-indicator|kolkata metro|wbtc|\\bksrtc|msrtc|tsrtc|apsrtc"},
            {FUEL, "petrol|diesel|fuel|\\bhpcl|\\bbpcl|\\biocl|indian oil|bharat petroleum|hindustan petroleum|shell|nayara|\\bhp pay|filling station|\\bcng\\b|service station"},
            {TRAVEL, "makemytrip|goibibo|cleartrip|yatra|ixigo|easemytrip|indigo|air india|vistara|spicejet|akasa|airasia|oyo|"
                    + "booking\\.com|agoda|airbnb|treebo|fabhotel|zostel|airport|flight|resort|holiday|thomas cook"},
            {BILLS, "electricity|cesc|wbsedcl|bses|tata power|adani|msedcl|bescom|tneb|kseb|uppcl|pspcl|water|\\bgas\\b|indane|bharat gas|"
                    + "hp gas|lpg|piped gas|mgl|igl|broadband|act fibernet|hathway|jiofiber|airtel xstream|excitel|tikona|bsnl|dth|tata play|tatasky|"
                    + "dish tv|d2h|sun direct|municipal|property tax|maintenance|society|bill ?desk|billpay|bbps|utility"},
            {RECHARGE, "recharge|prepaid|postpaid|\\bjio\\b|airtel|vodafone|\\bvi\\b|vodafone idea|\\bbsnl mobile|mobile bill"},
            {FUN, "bookmyshow|book my show|pvr|inox|cinepolis|cinema|movie|paytm insider|district|steam|playstation|xbox|gaming|"
                    + "dream11|mpl|zupee|winzo|my11circle|concert|event|amusement|wonderla|bowling|\\bclub\\b"},
            {HEALTH, "pharmacy|pharma|medical|medicine|chemist|apollo|1mg|tata 1mg|pharmeasy|netmeds|medplus|hospital|clinic|"
                    + "diagnostic|pathology|\\blab\\b|dr\\.|doctor|dental|practo|cult\\.?fit|cultfit|gym|fitness|healthkart|optical|wellness|ayurved"},
            {EDU, "school|college|university|tuition|coaching|academy|institute|udemy|coursera|byju|unacademy|vedantu|physics wallah|"
                    + "\\bpw\\b|upgrad|great learning|simplilearn|exam|fees|education|books|stationery|library|scaler|leetcode|duolingo"},
            {HOME, "\\brent\\b|landlord|nobroker|nestaway|housing\\.com|magicbricks|furniture|pepperfry|urban company|urbanclap|plumber|"
                    + "electrician|carpenter|laundry|dry clean|repair|home centre|hardware|paint"},
            {EMI, "\\bemi\\b|loan|bajaj finserv|bajaj finance|home credit|hdb financial|tata capital|muthoot|manappuram|lazypay|simpl|"
                    + "zestmoney|kreditbee|moneyview|navi loan|fibe|earlysalary|cashe|\\bnach\\b|\\becs\\b"},
            {INVEST, "zerodha|groww|upstox|angel one|angelone|kite|coin by|mutual fund|\\bmf\\b|\\bsip\\b|indmoney|kuvera|paytm money|"
                    + "smallcase|etmoney|nps|ppf|fixed deposit|\\bfd\\b|recurring deposit|\\brd\\b|stocks|demat|cams|kfintech|bse|nse|gold|sovereign|digigold|jar app|\\bjar\\b"},
            {INSURE, "insurance|\\blic\\b|policy|premium|hdfc life|icici pru|sbi life|max life|tata aia|star health|niva bupa|care health|acko|\\bdigit\\b|policybazaar|bajaj allianz|new india assurance"},
            {PERSONAL, "salon|parlour|parlor|\\bspa\\b|barber|haircut|grooming|beauty|cosmetic|naturals|lakme salon|jawed habib"},
            {FEES, "charges|\\bcharge\\b|\\bfee\\b|penalty|late fee|annual fee|gst on|service charge|sms charges|amc|interest debited|processing fee|convenience fee|surcharge"},
            {GIFTS, "donation|donate|charity|temple|church|mosque|gurudwara|ngo|relief fund|ketto|milaap|giveindia|gift|ferns|igp"},
            {CASH, "\\batm\\b|cash withdrawal|atm cash"},
    };

    private static final String[][] INCOME_RULES = {
            {SALARY, "salary|\\bsal\\b|payroll|wages|stipend|salary credit"},
            {INCOME, "interest|int\\.? credit|int\\.?pd|dividend|bonus|incentive|commission|freelance|upwork|fiverr|payout|settlement"},
            {REFUND, "refund|reversal|reversed|cashback|chargeback|return"},
    };

    /** Best guess for a new transaction (you can always change it). */
    public static String guess(Txn t) {
        String m = (t.merchant + " " + t.vpa).toLowerCase(Locale.ROOT);
        String raw = t.raw == null ? "" : t.raw.toLowerCase(Locale.ROOT);
        if (t.kind == Txn.TRANSFER) return t.raw != null && raw.contains("card") && !t.credit ? CARDBILL : TRANSFER;
        if (t.kind == Txn.REFUND) return REFUND;
        if ((t.sources & Txn.SRC_GAP) != 0) return UNRECORDED;
        if (t.credit) {
            for (String[] r : INCOME_RULES) if (find(r[1], m) || find(r[1], raw)) return r[0];
            if (raw.matches("(?s).*\\b(neft|rtgs)\\b.*") && t.amount >= 1000000 && looksLikeCompany(t.merchant)) return SALARY;
            if (looksLikePerson(t)) return PEOPLE;
            return INCOME;
        }
        for (String[] r : RULES) if (find(r[1], m)) return r[0];
        if (looksLikePerson(t)) return PEOPLE;
        // fall back to the whole message ("… for NETFLIX", "… towards electricity bill")
        for (String[] r : RULES) {
            if (r[0].equals(CASH) || r[0].equals(FEES)) continue;
            if (find(r[1], raw.replaceAll("\\b(?:a/c|acct|account|card|bank|upi|ref|avl|bal|rs|inr|not you|call|sms|block)\\b", " "))) return r[0];
        }
        if (raw.contains("atm") && raw.contains("withdraw")) return CASH;
        return OTHER;
    }

    private static boolean find(String regex, String s) {
        return java.util.regex.Pattern.compile("(?:" + regex + ")").matcher(s).find();
    }

    static boolean looksLikeCompany(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).matches(".*\\b(ltd|limited|pvt|private|llp|inc|technologies|solutions|services|corp|corporation|company|co\\.?|industries|enterprises|bank|infotech|systems|labs)\\b.*");
    }

    /** "Ramesh Kumar", "98300 12345", rahul.k@okicici → a person, not a shop */
    static boolean looksLikePerson(Txn t) {
        String n = t.merchant == null ? "" : t.merchant.trim();
        String v = t.vpa == null ? "" : t.vpa.toLowerCase(Locale.ROOT);
        if (n.startsWith("Shop (QR)")) return false;
        if (n.matches("[0-9]{5} [0-9]{5}")) return true;
        if (looksLikeCompany(n)) return false;
        if (n.toLowerCase(Locale.ROOT).matches(".*\\b(store|stores|mart|shop|traders|medical|hotel|restaurant|cafe|centre|center|agency|agencies|bazaar|enterprise|kirana|general|foods|motors|pharmacy|electronics)\\b.*")) return false;
        if (v.matches("[0-9]{10}@.*")) return true;
        if (v.matches("[a-z]+[._]?[a-z]*[0-9]{0,4}@ok(?:axis|icici|hdfcbank|sbi)") || v.matches(".*@(?:ybl|ibl|axl|apl|upi|paytm|okaxis|okicici|oksbi|okhdfcbank)")) {
            return n.matches("[A-Z][a-z]+(?: [A-Z][a-z]*\\.?){0,3}") && !v.startsWith("paytm") ;
        }
        return n.matches("[A-Z][a-z]+ [A-Z][a-z]+(?: [A-Z][a-z]+)?") && n.split(" ").length <= 3;
    }

    /** Short key for learning: "Zomato Ltd" / zomato@hdfcbank → "zomato ltd" */
    public static String key(Txn t) {
        if (t.vpa != null && !t.vpa.isEmpty()) return "vpa:" + t.vpa.toLowerCase(Locale.ROOT);
        String m = t.merchant == null ? "" : t.merchant.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        return m.isEmpty() ? "" : "name:" + m;
    }
}
