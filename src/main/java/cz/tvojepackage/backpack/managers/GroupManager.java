package cz.tvojepackage.backpack.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Stará se o načtení skupin z configu a o zjištění, jaká skupina
 * (a tedy kolik batohů) hráči náleží na základě jeho permissions.
 */
public class GroupManager {

    /**
     * Jednoduchý datový nositel jedné skupiny z config.yml.
     */
    public static final class BackpackGroup {
        private final String id;
        private final String permission;
        private final int backpacks;
        private final int inventorySize;
        private final String title;

        public BackpackGroup(String id, String permission, int backpacks, int inventorySize, String title) {
            this.id = id;
            this.permission = permission;
            this.backpacks = backpacks;
            this.inventorySize = inventorySize;
            this.title = title;
        }

        public String getId() {
            return id;
        }

        public String getPermission() {
            return permission;
        }

        public int getBackpacks() {
            return backpacks;
        }

        public int getInventorySize() {
            return inventorySize;
        }

        public String getTitle() {
            return title;
        }
    }

    private final ConfigManager configManager;
    private final Logger logger;
    private List<BackpackGroup> groups = new ArrayList<>();

    public GroupManager(ConfigManager configManager, Logger logger) {
        this.configManager = configManager;
        this.logger = logger;
        reload();
    }

    /**
     * Znovu načte skupiny z aktuálního configu a seřadí je
     * podle počtu batohů sestupně - díky tomu při hledání
     * skupiny hráče vždy najdeme tu "nejvyšší", kterou vlastní.
     */
    public void reload() {
        List<BackpackGroup> loaded = new ArrayList<>();
        ConfigurationSection groupsSection = configManager.getConfig().getConfigurationSection("groups");

        if (groupsSection == null) {
            logger.warning("[BackpackSystem] Sekce 'groups' v config.yml chybí! Používám výchozí skupinu 'default'.");
            loaded.add(new BackpackGroup("default", "backpack.default", 1, 54, "&7Batoh #%number%"));
            this.groups = loaded;
            return;
        }

        for (String key : groupsSection.getKeys(false)) {
            try {
                ConfigurationSection group = groupsSection.getConfigurationSection(key);
                if (group == null) {
                    continue;
                }
                String permission = group.getString("permission", "backpack." + key);
                int backpacks = group.getInt("backpacks", 1);
                int inventorySize = group.getInt("inventory-size", 54);
                String title = group.getString("title", "&7Batoh #%number%");

                if (inventorySize <= 0 || inventorySize % 9 != 0 || inventorySize > 54) {
                    logger.warning("[BackpackSystem] Skupina '" + key + "' má neplatnou inventory-size (" +
                            inventorySize + "), musí být násobek 9 a max. 54. Nastavuji na 54.");
                    inventorySize = 54;
                }

                loaded.add(new BackpackGroup(key, permission, backpacks, inventorySize, title));
            } catch (Exception e) {
                logger.warning("[BackpackSystem] Chyba při načítání skupiny '" + key + "': " + e.getMessage());
            }
        }

        if (loaded.isEmpty()) {
            loaded.add(new BackpackGroup("default", "backpack.default", 1, 54, "&7Batoh #%number%"));
        }

        loaded.sort(Comparator.comparingInt(BackpackGroup::getBackpacks).reversed());
        this.groups = loaded;
    }

    /**
     * Vrátí nejvyšší skupinu, kterou hráč vlastní (podle permission).
     * Pokud hráč nemá žádnou nakonfigurovanou permission, vrátí null.
     */
    public BackpackGroup getGroupFor(Player player) {
        for (BackpackGroup group : groups) {
            if (player.hasPermission(group.getPermission())) {
                return group;
            }
        }
        return null;
    }

    public List<BackpackGroup> getGroups() {
        return groups;
    }
}
