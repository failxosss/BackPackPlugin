package cz.tvojepackage.backpack.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Zapouzdřuje práci s config.yml, aby zbytek pluginu
 * nemusel volat plugin.reloadConfig() / saveDefaultConfig() přímo.
 */
public class ConfigManager {

    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    /**
     * Znovu načte config.yml ze souboru na disku.
     *
     * @return true pokud se načtení povedlo bez chyby
     */
    public boolean reload() {
        try {
            plugin.reloadConfig();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[BackpackSystem] Nepodařilo se znovu načíst config.yml: " + e.getMessage());
            return false;
        }
    }

    public String getMessage(String path) {
        return getConfig().getString("messages." + path, "");
    }

    public String getPrefix() {
        return getMessage("prefix");
    }

    public int getCooldownSeconds() {
        return getConfig().getInt("protection.cooldown", 2);
    }

    public boolean isCloseOnQuit() {
        return getConfig().getBoolean("protection.close-on-quit", true);
    }

    public boolean isPreventDupe() {
        return getConfig().getBoolean("protection.prevent-dupe", true);
    }

    public int getAutoSaveSeconds() {
        return getConfig().getInt("storage.auto-save", 300);
    }

    public String getStorageFolder() {
        return getConfig().getString("storage.folder", "backpacks");
    }

    public boolean isBackupEnabled() {
        return getConfig().getBoolean("storage.backup", true);
    }

    public int getCacheMaxSize() {
        return getConfig().getInt("cache.max-size", 100);
    }
}
