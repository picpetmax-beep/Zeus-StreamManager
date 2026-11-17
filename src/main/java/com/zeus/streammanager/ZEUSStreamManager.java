package com.zeus.streammanager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ZEUSStreamManager extends JavaPlugin implements CommandExecutor, TabCompleter {
    private File dataFile;
    private YamlConfiguration data;
    private final Map<UUID, Long> lastRequest = new ConcurrentHashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try { getDataFolder().mkdirs(); dataFile.createNewFile(); }
            catch (IOException e) { getLogger().severe("Could not create data.yml: " + e.getMessage()); }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        Objects.requireNonNull(getCommand("stream")).setExecutor(this);
        Objects.requireNonNull(getCommand("stream")).setTabCompleter(this);
        getLogger().info("ZEUS Stream Manager enabled.");
    }

    @Override public void onDisable() { saveData(); }

    private void saveData() {
        try { data.save(dataFile); }
        catch (IOException e) { getLogger().severe("Could not save data.yml: " + e.getMessage()); }
    }

    private String msg(String key, Map<String, String> placeholders) {
        String raw = getConfig().getString("messages." + key, "&cMissing message: " + key);
        for (var entry : placeholders.entrySet()) raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.prefix", "") + raw);
    }
    private String msg(String key) { return msg(key, Map.of()); }
    private void tell(CommandSender sender, String key) { sender.sendMessage(msg(key)); }

    private boolean validLink(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return uri.getHost() != null && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && !value.contains(" ") && !value.contains("\n");
        } catch (IllegalArgumentException ex) { return false; }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { tell(sender, "usage"); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("approve") || sub.equals("deny")) {
            if (!sender.hasPermission("zeusstream.staff")) { tell(sender, "no-permission"); return true; }
            if (args.length < 2) { tell(sender, "usage"); return true; }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            String base = "pending." + target.getUniqueId();
            if (!data.contains(base + ".link")) { tell(sender, "no-pending"); return true; }
            String link = data.getString(base + ".link", "");
            String playerName = data.getString(base + ".name", args[1]);
            data.set(base, null);
            saveData();
            Player online = target.getPlayer();
            if (sub.equals("deny")) {
                if (online != null) online.sendMessage(msg("denied"));
                sender.sendMessage(msg("denied-staff", Map.of("player", playerName)));
                return true;
            }
            String template = getConfig().getString("announce-command", "announce {player} is live: {link}");
            String announce = template.replace("{player}", playerName).replace("{link}", link);
            boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), announce);
            if (dispatched) {
                if (online != null) online.sendMessage(msg("approved"));
                sender.sendMessage(msg("approved-staff", Map.of("player", playerName)));
            } else {
                sender.sendMessage(msg("announce-failed"));
                // Put the request back so staff can retry after fixing the announce command.
                data.set(base + ".link", link);
                data.set(base + ".name", playerName);
                saveData();
            }
            return true;
        }

        if (!(sender instanceof Player player)) { tell(sender, "player-only"); return true; }
        UUID uuid = player.getUniqueId();

        if (sub.equals("announce")) {
            String link = data.getString("streams." + uuid);
            if (link == null || link.isBlank()) { tell(player, "no-link"); return true; }
            long now = System.currentTimeMillis();
            long cooldown = Math.max(0, getConfig().getLong("request-cooldown-seconds", 300)) * 1000L;
            long remaining = cooldown - (now - lastRequest.getOrDefault(uuid, 0L));
            if (remaining > 0) {
                player.sendMessage(msg("cooldown", Map.of("seconds", String.valueOf((remaining + 999) / 1000))));
                return true;
            }
            String base = "pending." + uuid;
            data.set(base + ".link", link);
            data.set(base + ".name", player.getName());
            saveData();
            lastRequest.put(uuid, now);
            tell(player, "request-sent");
            Map<String, String> p = Map.of("player", player.getName(), "link", link);
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("zeusstream.staff")) {
                    staff.sendMessage(msg("staff-request", p));
                    staff.sendMessage(msg("staff-hint", Map.of("player", player.getName())));
                }
            }
            return true;
        }

        if (sub.equals("info")) {
            String link = data.getString("streams." + uuid);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6Your saved stream: &f" + (link == null ? "none" : link)));
            return true;
        }

        // Any non-subcommand argument is treated as the stream URL.
        if (!validLink(args[0]) || args.length != 1) { tell(player, "invalid-link"); return true; }
        data.set("streams." + uuid, args[0]);
        data.set("names." + uuid, player.getName());
        saveData();
        player.sendMessage(msg("registered", Map.of("link", args[0])));
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> choices = new ArrayList<>(List.of("announce", "info"));
            if (sender.hasPermission("zeusstream.staff")) { choices.add("approve"); choices.add("deny"); }
            return choices.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && sender.hasPermission("zeusstream.staff") && (args[0].equalsIgnoreCase("approve") || args[0].equalsIgnoreCase("deny"))) {
            ConfigurationSection pending = data.getConfigurationSection("pending");
            if (pending == null) return List.of();
            List<String> names = new ArrayList<>();
            for (String key : pending.getKeys(false)) names.add(pending.getString(key + ".name", ""));
            return names.stream().filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
