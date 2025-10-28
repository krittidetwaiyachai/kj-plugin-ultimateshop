package xyz.kaijiieow.kjshopplus.economy;

import java.text.DecimalFormat;

public class PriceUtil {

    // Using DecimalFormat for performance is good, but String.format is requested.
    // private static final DecimalFormat formatter = new DecimalFormat("#,##0.00");
    
    /**
     * Formats a double value to exactly two decimal places.
     * e.g., 10.0 -> "10.00", 10.123 -> "10.12"
     * @param value The value to format.
     * @return A string formatted to two decimal places.
     */
    public static String format(double value) {
        return String.format("%.2f", value);
    }
}
