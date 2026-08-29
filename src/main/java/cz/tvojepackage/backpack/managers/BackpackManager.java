package cz.tvojepackage.backpack.managers;

import cz.tvojepackage.backpack.utils.BackpackHolder;
import cz.tvojepackage.backpack.utils.BackpackUtils;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Spravuje veškerá data batohů: cache v paměti, ukládání a načítání
 * ze souborů, cooldown mezi otevřeními a ochranu proti duplikaci itemů.
 * <p>
 * Soubory jsou uloženy jako UUID_index.dat a obsah inventáře je
 * serializován pomocí BukkitObjectOutputStream (obálka nad ObjectOutputStream,
 * která umí serializovat ItemStack přes ConfigurationSerializable).
 */
public class BackpackManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Logger logger;
    private final File storageFolder;

    // Cache: klíč "uuid_index" -> Inventory
    private final Map<String, Inventory> cache;
    // Zámek proti duplikaci - drží klíče, které se právě ukládají/načítají
    private final Set<String> locks = ConcurrentHashMap.newKeySet();
    // Cooldown: hráč -> čas posledního otevření (millis)
    private final Map<UUID, Long> lastOpen = new ConcurrentHashMap<>();

    public BackpackManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.logger = plugin.getLogger();
        this.storageFolder = new File(plugin.getDataFolder(), configManager.getStorageFolder());

        // LinkedHashMap s ochranou proti přetečení cache (jednoduché LRU)
        int maxSize = configManager.getCacheMaxSize();
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Inventory> eldest) {
                if (size() > maxSize) {
                    // Před odstraněním z cache se ujistíme, že je uloženo na disk
                    saveToDisk(eldest.getKey(), eldest.getValue());
                    return true;
                }
                return false;
            }
        };

        if (!storageFolder.exists()) {
            boolean created = storageFolder.mkdirs();
            if (!created) {
                logger.severe("[BackpackSystem] Nepodařilo se vytvořit složku pro ukládání batohů: " + storageFolder.getAbsolutePath());
            }
        }
    }

    private String key(UUID owner, int index) {
        return owner.toString() + "_" + index;
    }

    private File fileFor(UUID owner, int index) {
        return new File(storageFolder, owner.toString() + "_" + index + ".dat");
    }

    /**
     * Vrátí (a v případě potřeby vytvoří/načte) inventář daného batohu.
     */
    public synchronized Inventory getBackpack(UUID owner, int index, int size, String rawTitle) {
        String cacheKey = key(owner, index);
        String title = BackpackUtils.replace(rawTitle, "%number%", String.valueOf(index));

        Inventory cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        BackpackHolder holder = new BackpackHolder(owner, index);
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);
        ItemStack[] loaded = loadFromDisk(owner, index);

        if (loaded != null) {
            int limit = Math.min(loaded.length, size);
            for (int i = 0; i < limit; i++) {
                inventory.setItem(i, loaded[i]);
            }
        }

        cache.put(cacheKey, inventory);
        return inventory;
    }

    /**
     * Vrátí inventář z cache, pokud existuje (bez vytváření nového).
     * Používá se např. pro zjištění, jestli je batoh momentálně otevřený.
     */
    public synchronized Inventory peekBackpack(UUID owner, int index) {
        return cache.get(key(owner, index));
    }

    /**
     * Uloží konkrétní batoh na disk (voláno při zavření inventáře, odchodu hráče apod.).
     */
    public synchronized void saveBackpack(UUID owner, int index) {
        String cacheKey = key(owner, index);
        Inventory inventory = cache.get(cacheKey);
        if (inventory != null) {
            saveToDisk(cacheKey, inventory);
        }
    }

    /**
     * Uloží úplně vše, co je aktuálně v cache - používá se při autosave a při vypnutí serveru.
     */
    public synchronized void saveAll() {
        for (Map.Entry<String, Inventory> entry : cache.entrySet()) {
            saveToDisk(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Uloží na disk všechny aktuálně cachované batohy patřící danému hráči.
     */
    public synchronized void saveAllForPlayer(UUID owner) {
        String ownerPrefix = owner.toString() + "_";
        for (Map.Entry<String, Inventory> entry : cache.entrySet()) {
            if (entry.getKey().startsWith(ownerPrefix)) {
                saveToDisk(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Odstraní hráčovy batohy z cache (voláno při odchodu, pokud je zapnuto close-on-quit).
     */
    public synchronized void unloadPlayer(UUID owner) {
        cache.keySet().removeIf(k -> k.startsWith(owner.toString() + "_"));
    }

    private void saveToDisk(String cacheKey, Inventory inventory) {
        if (configManager.isPreventDupe()) {
            if (!locks.add(cacheKey)) {
                // Už se ukládá jinde, přeskočíme abychom nezpůsobili race condition
                return;
            }
        }

        try {
            String[] parts = cacheKey.split("_");
            UUID owner = UUID.fromString(parts[0]);
            int index = Integer.parseInt(parts[1]);
            File target = fileFor(owner, index);

            if (configManager.isBackupEnabled() && target.exists()) {
                File backup = new File(target.getParentFile(), target.getName() + ".bak");
                try {
                    java.nio.file.Files.copy(target.toPath(), backup.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException backupError) {
                    logger.warning("[BackpackSystem] Nepodařilo se vytvořit zálohu pro " + target.getName() + ": " + backupError.getMessage());
                }
            }

            ItemStack[] contents = inventory.getContents();

            try (FileOutputStream fos = new FileOutputStream(target);
                 BukkitObjectOutputStream out = new BukkitObjectOutputStream(fos)) {

                out.writeInt(contents.length);
                for (ItemStack item : contents) {
                    out.writeObject(item);
                }
                out.flush();
            }
        } catch (Exception e) {
            logger.severe("[BackpackSystem] Chyba při ukládání batohu (" + cacheKey + "): " + e.getMessage());
            e.printStackTrace();
        } finally {
            locks.remove(cacheKey);
        }
    }

    private ItemStack[] loadFromDisk(UUID owner, int index) {
        File source = fileFor(owner, index);
        if (!source.exists()) {
            return null;
        }

        String cacheKey = key(owner, index);
        if (configManager.isPreventDupe() && !locks.add(cacheKey)) {
            // Soubor se právě ukládá - počkáme jednu chvíli a zkusíme to bezpečně znovu
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        try (FileInputStream fis = new FileInputStream(source);
             BukkitObjectInputStream in = new BukkitObjectInputStream(fis)) {

            int length = in.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) in.readObject();
            }
            return items;
        } catch (Exception e) {
            logger.severe("[BackpackSystem] Chyba při načítání batohu ze souboru " + source.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            locks.remove(cacheKey);
        }
    }

    // ---------------- Cooldown ----------------

    public boolean isOnCooldown(UUID player) {
        return getRemainingCooldown(player) > 0;
    }

    public long getRemainingCooldown(UUID player) {
        long cooldownMillis = configManager.getCooldownSeconds() * 1000L;
        Long last = lastOpen.get(player);
        if (last == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - last;
        long remaining = cooldownMillis - elapsed;
        return Math.max(0, remaining);
    }

    public void updateCooldown(UUID player) {
        lastOpen.put(player, System.currentTimeMillis());
    }

    public synchronized int getCachedCount() {
        return cache.size();
    }
}
