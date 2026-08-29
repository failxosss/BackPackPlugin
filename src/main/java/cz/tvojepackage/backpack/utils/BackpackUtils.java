package cz.tvojepackage.backpack.utils;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Pomocné statické metody používané napříč pluginem.
 */
public final class BackpackUtils {

    private BackpackUtils() {
        // utility třída - žádné instance
    }

    /**
     * Přeloží '&' barevné kódy na formát, který Minecraft umí vykreslit.
     */
    public static String color(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    /**
     * Nahradí placeholder v textu a rovnou obarví výsledek.
     */
    public static String replace(String input, String placeholder, String value) {
        if (input == null) {
            return "";
        }
        return color(input.replace(placeholder, value));
    }

    /**
     * Pošle hráči obarvenou zprávu s prefixem z configu.
     */
    public static void sendPrefixed(CommandSender sender, String prefix, String message) {
        sender.sendMessage(color(prefix) + color(message));
    }

    /**
     * Bezpečně naparsuje String na int, vrátí null pokud se to nepovede.
     */
    public static Integer parseInt(String input) {
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }
}
