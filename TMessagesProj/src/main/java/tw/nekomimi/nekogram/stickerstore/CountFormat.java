package tw.nekomimi.nekogram.stickerstore;

/** Short counts for the store UI: exact below 1,000, then 1k / 1.1k / 10k / 1M. One decimal
 *  at most; the decimal is cut (not rounded up) so 999,999 reads 999.9k, never "1000k". */
public final class CountFormat {

    private CountFormat() {
    }

    public static String format(long value) {
        if (value < 1000) {
            return String.valueOf(Math.max(value, 0));
        }
        String[] units = {"k", "M", "B"};
        double scaled = value;
        int unit = -1;
        while (scaled >= 1000 && unit < units.length - 1) {
            scaled /= 1000;
            unit++;
        }
        long tenths = (long) Math.floor(scaled * 10 + 1e-9);
        long whole = tenths / 10;
        long decimal = tenths % 10;
        return (decimal == 0 ? String.valueOf(whole) : whole + "." + decimal) + units[unit];
    }
}
