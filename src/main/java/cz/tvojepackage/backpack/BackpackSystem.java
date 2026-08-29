package cz.tvojepackage.backpack;

import cz.tvojepackage.backpack.commands.BackpackCommand;
import cz.tvojepackage.backpack.listeners.BackpackListener;
import cz.tvojepackage.backpack.managers.BackpackManager;
import cz.tvojepackage.backpack.managers.ConfigManager;
import cz.tvojepackage.backpack.managers.GroupManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Hlavní třída pluginu BackpackSystem.
 * Stará se o inicializaci všech manažerů, registraci příkazu/listeneru
 * a o pravidelné automatické ukládání dat.
 */
public class BackpackSystem extends JavaPlugin {

    private ConfigManager configManager;
    private GroupManager groupManager;
    private BackpackManager backpackManager;
    private BukkitTask autoSaveTask;

    @Override
    public void onEnable() {
        try {
            this.configManager = new ConfigManager(this);
            this.groupManager = new GroupManager(configManager, getLogger());
            this.backpackManager = new BackpackManager(this, configManager);

            registerCommand();
            getServer().getPluginManager().registerEvents(new BackpackListener(this), this);
            startAutoSave();

            getLogger().info("BackpackSystem byl úspěšně zapnut! Nalezeno " + groupManager.getGroups().size() + " skupin.");
        } catch (Exception e) {
            getLogger().severe("Chyba při zapínání BackpackSystem: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }

        if (backpackManager != null) {
            getLogger().info("Ukládám všechny batohy před vypnutím...");
            backpackManager.saveAll();
            getLogger().info("Uloženo " + backpackManager.getCachedCount() + " batohů.");
        }

        getLogger().info("BackpackSystem byl vypnut.");
    }

    private void registerCommand() {
        PluginCommand command = getCommand("backpack");
        if (command == null) {
            getLogger().severe("Příkaz 'backpack' nebyl nalezen v plugin.yml!");
            return;
        }
        BackpackCommand executor = new BackpackCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void startAutoSave() {
        int seconds = configManager.getAutoSaveSeconds();
        if (seconds <= 0) {
            getLogger().info("Automatické ukládání je vypnuté (auto-save <= 0).");
            return;
        }

        long ticks = seconds * 20L;
        this.autoSaveTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                backpackManager.saveAll();
                getLogger().info("[BackpackSystem] Automatické ukládání dokončeno (" + backpackManager.getCachedCount() + " batohů v cache).");
            }
        }.runTaskTimerAsynchronously(this, ticks, ticks);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GroupManager getGroupManager() {
        return groupManager;
    }

    public BackpackManager getBackpackManager() {
        return backpackManager;
    }
}
