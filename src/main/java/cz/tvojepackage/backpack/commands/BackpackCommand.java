package cz.tvojepackage.backpack.commands;

import cz.tvojepackage.backpack.BackpackSystem;
import cz.tvojepackage.backpack.managers.BackpackManager;
import cz.tvojepackage.backpack.managers.ConfigManager;
import cz.tvojepackage.backpack.managers.GroupManager;
import cz.tvojepackage.backpack.utils.BackpackUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Zpracovává příkaz /backpack (alias /bp) a všechny jeho podpříkazy.
 */
public class BackpackCommand implements CommandExecutor, TabCompleter {

    private final BackpackSystem plugin;
    private final BackpackManager backpackManager;
    private final GroupManager groupManager;
    private final ConfigManager configManager;

    public BackpackCommand(BackpackSystem plugin) {
        this.plugin = plugin;
        this.backpackManager = plugin.getBackpackManager();
        this.groupManager = plugin.getGroupManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = configManager.getPrefix();

        if (args.length == 0) {
            sender.sendMessage(BackpackUtils.color(prefix) +
                    BackpackUtils.color("&cPoužij: /" + label + " <číslo|list|reload|give>"));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "list":
                return handleList(sender);
            case "reload":
                return handleReload(sender);
            case "give":
                return handleGive(sender, args);
            default:
                return handleOpen(sender, args[0]);
        }
    }

    // ---------------- /backpack <číslo> ----------------

    private boolean handleOpen(CommandSender sender, String rawNumber) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tento příkaz může použít pouze hráč.");
            return true;
        }

        String prefix = configManager.getPrefix();

        if (!player.hasPermission("backpack.use")) {
            BackpackUtils.sendPrefixed(player, prefix, configManager.getMessage("no-permission"));
            return true;
        }

        GroupManager.BackpackGroup group = groupManager.getGroupFor(player);
        if (group == null) {
            BackpackUtils.sendPrefixed(player, prefix, configManager.getMessage("no-permission"));
            return true;
        }

        Integer number = BackpackUtils.parseInt(rawNumber);
        if (number == null || number <= 0) {
            String msg = configManager.getMessage("invalid-number").replace("%max%", String.valueOf(group.getBackpacks()));
            BackpackUtils.sendPrefixed(player, prefix, msg);
            return true;
        }

        if (number > group.getBackpacks()) {
            String msg = configManager.getMessage("out-of-range").replace("%max%", String.valueOf(group.getBackpacks()));
            BackpackUtils.sendPrefixed(player, prefix, msg);
            return true;
        }

        if (backpackManager.isOnCooldown(player.getUniqueId())) {
            long seconds = (backpackManager.getRemainingCooldown(player.getUniqueId()) + 999) / 1000;
            String msg = configManager.getMessage("cooldown").replace("%seconds%", String.valueOf(seconds));
            BackpackUtils.sendPrefixed(player, prefix, msg);
            return true;
        }

        Inventory inventory = backpackManager.getBackpack(player.getUniqueId(), number, group.getInventorySize(), group.getTitle());
        player.openInventory(inventory);
        backpackManager.updateCooldown(player.getUniqueId());

        String msg = configManager.getMessage("opened").replace("%number%", String.valueOf(number));
        BackpackUtils.sendPrefixed(player, prefix, msg);
        return true;
    }

    // ---------------- /backpack list ----------------

    private boolean handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tento příkaz může použít pouze hráč.");
            return true;
        }

        String prefix = configManager.getPrefix();
        GroupManager.BackpackGroup group = groupManager.getGroupFor(player);

        if (group == null) {
            BackpackUtils.sendPrefixed(player, prefix, configManager.getMessage("no-permission"));
            return true;
        }

        player.sendMessage(BackpackUtils.color(configManager.getMessage("list-header")));

        // U skupin s extrémně vysokým limitem (např. ADMIN = 999) zobrazíme
        // v listu jen prvních 50, aby se hráči nezahltila obrazovka.
        int shown = Math.min(group.getBackpacks(), 50);

        for (int i = 1; i <= shown; i++) {
            Inventory cached = backpackManager.peekBackpack(player.getUniqueId(), i);
            boolean isOpen = cached != null && !cached.getViewers().isEmpty();

            String status = isOpen
                    ? configManager.getMessage("list-status-open")
                    : configManager.getMessage("list-status-closed");

            String line = configManager.getMessage("list-format")
                    .replace("%number%", String.valueOf(i))
                    .replace("%status%", status);

            player.sendMessage(BackpackUtils.color(line));
        }

        if (group.getBackpacks() > shown) {
            player.sendMessage(BackpackUtils.color("&7... a dalších " + (group.getBackpacks() - shown) + " batohů"));
        }

        return true;
    }

    // ---------------- /backpack reload ----------------

    private boolean handleReload(CommandSender sender) {
        String prefix = configManager.getPrefix();

        if (!sender.hasPermission("backpack.admin")) {
            BackpackUtils.sendPrefixed(sender, prefix, configManager.getMessage("no-permission"));
            return true;
        }

        boolean success = configManager.reload();
        if (success) {
            groupManager.reload();
            BackpackUtils.sendPrefixed(sender, prefix, configManager.getMessage("reload-success"));
        } else {
            BackpackUtils.sendPrefixed(sender, prefix, configManager.getMessage("reload-error"));
        }
        return true;
    }

    // ---------------- /backpack give <hráč> <číslo> ----------------

    private boolean handleGive(CommandSender sender, String[] args) {
        String prefix = configManager.getPrefix();

        if (!sender.hasPermission("backpack.admin")) {
            BackpackUtils.sendPrefixed(sender, prefix, configManager.getMessage("no-permission"));
            return true;
        }

        if (!(sender instanceof Player admin)) {
            sender.sendMessage("Tento příkaz může použít pouze hráč.");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(BackpackUtils.color(prefix) + BackpackUtils.color("&cPoužij: /backpack give <hráč> <číslo>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            String msg = configManager.getMessage("player-not-found").replace("%player%", args[1]);
            BackpackUtils.sendPrefixed(admin, prefix, msg);
            return true;
        }

        Integer number = BackpackUtils.parseInt(args[2]);
        GroupManager.BackpackGroup targetGroup = groupManager.getGroupFor(target);

        if (targetGroup == null) {
            BackpackUtils.sendPrefixed(admin, prefix, configManager.getMessage("no-permission"));
            return true;
        }

        if (number == null || number <= 0) {
            String msg = configManager.getMessage("invalid-number").replace("%max%", String.valueOf(targetGroup.getBackpacks()));
            BackpackUtils.sendPrefixed(admin, prefix, msg);
            return true;
        }

        if (number > targetGroup.getBackpacks()) {
            String msg = configManager.getMessage("out-of-range").replace("%max%", String.valueOf(targetGroup.getBackpacks()));
            BackpackUtils.sendPrefixed(admin, prefix, msg);
            return true;
        }

        Inventory inventory = backpackManager.getBackpack(target.getUniqueId(), number, targetGroup.getInventorySize(), targetGroup.getTitle());
        admin.openInventory(inventory);

        String msg = configManager.getMessage("give-success")
                .replace("%number%", String.valueOf(number))
                .replace("%player%", target.getName());
        BackpackUtils.sendPrefixed(admin, prefix, msg);
        return true;
    }

    // ---------------- Tab completion ----------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            options.add("list");
            if (sender.hasPermission("backpack.admin")) {
                options.add("reload");
                options.add("give");
            }
            options.add("1");
            return filter(options, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("backpack.admin")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give") && sender.hasPermission("backpack.admin")) {
            options.add("1");
            return filter(options, args[2]);
        }

        return options;
    }

    private List<String> filter(List<String> options, String input) {
        return options.stream()
                .filter(opt -> opt.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}
