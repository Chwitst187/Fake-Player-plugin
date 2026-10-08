package me.bill.fakePlayerPlugin.command;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.BotType;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.RankIntegration;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;

/**
 * Spawns one or more bots — sequentially named ({@code bot}, {@code bot2}, …), randomly named via
 * {@code --random}, custom-named via {@code --name} for a single spawn, or evenly distributed around
 * the sender via {@code --spread}. A target world can be selected via {@code --world};
 * {@code --randompos} chooses independent random positions in that world.
 *
 * <p>In-game only: bots spawn at the commanding player's location by default. An explicit target
 * world uses that world's spawn as the shared origin.
 */
public class SpawnCommand implements FppCommand {

    private final FakePlayerManager manager;

    public SpawnCommand(FakePlayerManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "spawn";
    }

    @Override
    public String getUsage() {
        return "[--name <name>] [--count <amount>] [--random] [--world <world>] "
                + "[--randompos | --spread <radius>] [--rank <maximum-group>] [--god] [--donutchat]";
    }

    @Override
    public String getDescription() {
        return "Spawns one or more fake player bots in a selected world and position mode.";
    }

    @Override
    public String getPermission() {
        return Perm.SPAWN;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.SPAWN) || Perm.has(sender, Perm.USER_SPAWN);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("player-only"));
            return true;
        }

        boolean isAdmin = Perm.has(sender, Perm.SPAWN);
        boolean isUser = !isAdmin && Perm.has(sender, Perm.USER_SPAWN);

        if (!isAdmin && !isUser) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        SpawnOptions options = SpawnOptions.parse(args);
        if (!options.isValid()) {
            String errorKey =
                    switch (options.error()) {
                        case INVALID_COUNT -> "invalid-number";
                        case INVALID_SPREAD -> "spawn-invalid-spread";
                        default -> "spawn-invalid-options";
                    };
            sender.sendMessage(Lang.get(errorKey));
            return true;
        }

        if (options.maximumRank() != null) {
            if (!Perm.has(sender, Perm.SPAWN_RANK)) {
                sender.sendMessage(Lang.get("no-permission"));
                return true;
            }
            if (!RankIntegration.isAvailable()) {
                sender.sendMessage(Lang.get("spawn-rank-no-provider"));
                return true;
            }
            if (!RankIntegration.rankExists(options.maximumRank())) {
                sender.sendMessage(Lang.get(
                        "spawn-rank-not-found",
                        "rank",
                        options.maximumRank(),
                        "provider",
                        RankIntegration.providerName()));
                return true;
            }
        }
        if (options.godMode() && !Perm.has(sender, Perm.SPAWN_GOD)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }
        if (options.donutChat() && !Perm.has(sender, Perm.SPAWN_DONUT_CHAT)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        Location location = resolveSpawnOrigin(player, options.worldName());
        if (location == null) {
            sender.sendMessage(Lang.get("spawn-world-not-found", "world", String.valueOf(options.worldName())));
            return true;
        }

        if (!Perm.has(sender, Perm.BYPASS_COOLDOWN) && manager.isOnCooldown(player.getUniqueId())) {
            long remaining = manager.getRemainingCooldown(player.getUniqueId());
            sender.sendMessage(Lang.get("spawn-cooldown", "seconds", String.valueOf(remaining)));
            return true;
        }

        if (isUser) {
            int permLimit = Perm.resolveUserBotLimit(sender);
            int limit = permLimit >= 0 ? permLimit : Config.userBotLimit();
            int alreadyOwned = manager.getBotsOwnedBy(player.getUniqueId()).size();
            if (alreadyOwned >= limit) {
                sender.sendMessage(Lang.get("spawn-user-limit-reached", "limit", String.valueOf(limit)));
                return true;
            }
            int count = Math.min(options.count(), limit - alreadyOwned);

            int result = options.customName() != null
                    ? manager.spawn(
                            location,
                            1,
                            player,
                            options.customName(),
                            false,
                            BotType.AFK,
                            false,
                            0,
                            options.randomPositions(),
                            options.maximumRank(),
                            options.godMode(),
                            options.donutChat())
                    : manager.spawnUserBot(
                            location,
                            count,
                            player,
                            false,
                            BotType.AFK,
                            options.randomNames(),
                            options.spreadRadius(),
                            options.randomPositions(),
                            options.maximumRank(),
                            options.godMode(),
                            options.donutChat());
            if (handleSpawnResult(sender, result, options.customName())) {
                manager.recordSpawnCooldown(player.getUniqueId());
                sender.sendMessage(Lang.get(
                        "spawn-success", "count", String.valueOf(result), "total", String.valueOf(manager.getCount())));
            }
            return true;
        }

        boolean bypassMax = Perm.has(sender, Perm.BYPASS_MAX);

        int result = manager.spawn(
                location,
                options.count(),
                player,
                options.customName(),
                bypassMax,
                BotType.AFK,
                options.randomNames(),
                options.spreadRadius(),
                options.randomPositions(),
                options.maximumRank(),
                options.godMode(),
                options.donutChat());
        if (handleSpawnResult(sender, result, options.customName())) {
            manager.recordSpawnCooldown(player.getUniqueId());
            sender.sendMessage(Lang.get(
                    "spawn-success", "count", String.valueOf(result), "total", String.valueOf(manager.getCount())));
        }
        return true;
    }

    /** Sends the failure message for a spawn result code; returns true when the spawn succeeded. */
    private boolean handleSpawnResult(CommandSender sender, int result, String customName) {
        switch (result) {
            case -1 -> sender.sendMessage(Lang.get("spawn-max-reached", "max", String.valueOf(Config.maxBots())));
            case -2 -> sender.sendMessage(Lang.get("spawn-invalid-name", "name", String.valueOf(customName)));
            case -4 -> sender.sendMessage(Lang.get("spawn-name-online", "name", String.valueOf(customName)));
            case 0 -> sender.sendMessage(
                    customName != null
                            ? Lang.get("spawn-name-taken", "name", customName)
                            : Lang.get("spawn-no-names-left"));
            default -> {
                return result > 0;
            }
        }
        return false;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) return List.of();

        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        List<String> completed = args.length <= 1 ? List.of() : List.of(args).subList(0, args.length - 1);

        if (!completed.isEmpty() && "--name".equalsIgnoreCase(completed.getLast())) {
            return List.of();
        }
        if (!completed.isEmpty() && "--count".equalsIgnoreCase(completed.getLast())) {
            return countSuggestions(sender, typed);
        }
        if (!completed.isEmpty() && "--spread".equalsIgnoreCase(completed.getLast())) {
            return spreadSuggestions(typed);
        }
        if (!completed.isEmpty() && "--world".equalsIgnoreCase(completed.getLast())) {
            return worldSuggestions(typed);
        }
        if (!completed.isEmpty() && "--rank".equalsIgnoreCase(completed.getLast())) {
            return rankSuggestions(typed);
        }

        boolean nameUsed = completed.stream().anyMatch("--name"::equalsIgnoreCase);
        boolean countUsed = completed.stream().anyMatch("--count"::equalsIgnoreCase);
        boolean randomUsed = completed.stream().anyMatch("--random"::equalsIgnoreCase);
        boolean spreadUsed = completed.stream().anyMatch("--spread"::equalsIgnoreCase);
        boolean worldUsed = completed.stream().anyMatch("--world"::equalsIgnoreCase);
        boolean randomPositionsUsed = completed.stream().anyMatch("--randompos"::equalsIgnoreCase);
        boolean rankUsed = completed.stream().anyMatch("--rank"::equalsIgnoreCase);
        boolean godModeUsed = completed.stream().anyMatch("--god"::equalsIgnoreCase);
        boolean donutChatUsed = completed.stream().anyMatch("--donutchat"::equalsIgnoreCase);

        List<String> suggestions = new java.util.ArrayList<>();
        if (!nameUsed && !countUsed && !randomUsed && !spreadUsed && "--name".startsWith(typed)) {
            suggestions.add("--name");
        }
        if (!nameUsed && !countUsed && "--count".startsWith(typed)) suggestions.add("--count");
        if (!nameUsed && !randomUsed && "--random".startsWith(typed)) suggestions.add("--random");
        if (!randomPositionsUsed && !spreadUsed && "--spread".startsWith(typed)) suggestions.add("--spread");
        if (!worldUsed && "--world".startsWith(typed)) suggestions.add("--world");
        if (!spreadUsed && !randomPositionsUsed && "--randompos".startsWith(typed)) suggestions.add("--randompos");
        if (!rankUsed && Perm.has(sender, Perm.SPAWN_RANK) && "--rank".startsWith(typed)) {
            suggestions.add("--rank");
        }
        if (!godModeUsed && Perm.has(sender, Perm.SPAWN_GOD) && "--god".startsWith(typed)) {
            suggestions.add("--god");
        }
        if (!donutChatUsed && Perm.has(sender, Perm.SPAWN_DONUT_CHAT) && "--donutchat".startsWith(typed)) {
            suggestions.add("--donutchat");
        }
        return suggestions;
    }

    private Location resolveSpawnOrigin(Player player, String worldName) {
        if (worldName == null) return player.getLocation().clone();

        World target = Bukkit.getWorld(worldName);
        if (target == null) {
            target = Bukkit.getWorlds().stream()
                    .filter(world -> world.getName().equalsIgnoreCase(worldName))
                    .findFirst()
                    .orElse(null);
        }
        return target != null ? target.getSpawnLocation().clone() : null;
    }

    private List<String> worldSuggestions(String typed) {
        return Bukkit.getWorlds().stream()
                .map(World::getName)
                .filter(name -> name.toLowerCase().startsWith(typed.toLowerCase()))
                .toList();
    }

    private List<String> spreadSuggestions(String typed) {
        return List.of("2", "4", "6", "8", "10", "16").stream()
                .filter(value -> value.startsWith(typed))
                .toList();
    }

    private List<String> rankSuggestions(String typed) {
        return RankIntegration.rankNames().stream()
                .filter(group -> group.toLowerCase().startsWith(typed.toLowerCase()))
                .toList();
    }

    private List<String> countSuggestions(CommandSender sender, String typed) {
        if (Perm.has(sender, Perm.SPAWN)) {
            return Config.spawnCountPresets().stream()
                    .filter(value -> value.startsWith(typed))
                    .toList();
        }

        int permissionLimit = Perm.resolveUserBotLimit(sender);
        int limit = permissionLimit >= 0 ? permissionLimit : Config.userBotLimit();
        return java.util.stream.IntStream.rangeClosed(1, Math.min(limit, 10))
                .mapToObj(String::valueOf)
                .filter(value -> value.startsWith(typed))
                .toList();
    }
}
