package cz.tvojepackage.backpack.listeners;

import cz.tvojepackage.backpack.BackpackSystem;
import cz.tvojepackage.backpack.managers.BackpackManager;
import cz.tvojepackage.backpack.managers.ConfigManager;
import cz.tvojepackage.backpack.utils.BackpackHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Naslouchá eventům, které se týkají otevřených batohů - zavření okna
 * a odchod hráče ze serveru. V obou případech se batoh uloží na disk.
 */
public class BackpackListener implements Listener {

    private final BackpackSystem plugin;
    private final BackpackManager backpackManager;
    private final ConfigManager configManager;

    public BackpackListener(BackpackSystem plugin) {
        this.plugin = plugin;
        this.backpackManager = plugin.getBackpackManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (!(holder instanceof BackpackHolder backpackHolder)) {
            return;
        }

        // Uložení provedeme asynchronně o tick později, aby jistě doběhly
        // všechny ostatní pluginy zpracovávající tento event (ochrana proti dupe).
        new BukkitRunnable() {
            @Override
            public void run() {
                backpackManager.saveBackpack(backpackHolder.getOwner(), backpackHolder.getIndex());
            }
        }.runTaskAsynchronously(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Uložíme jen batohy odcházejícího hráče - odchod je bod, kdy chceme
        // mít jistotu, že jsou jeho data v pořádku na disku.
        backpackManager.saveAllForPlayer(player.getUniqueId());

        if (configManager.isCloseOnQuit()) {
            backpackManager.unloadPlayer(player.getUniqueId());
        }
    }
}
