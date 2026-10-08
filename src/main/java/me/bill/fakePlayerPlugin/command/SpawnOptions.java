package me.bill.fakePlayerPlugin.command;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Parsed, validated flags for {@code /fpp spawn}. */
record SpawnOptions(
        int count,
        boolean randomNames,
        String customName,
        int spreadRadius,
        String worldName,
        boolean randomPositions,
        String maximumRank,
        boolean godMode,
        boolean donutChat,
        Error error) {

    static final int MAX_SPREAD_RADIUS = 1_000;
    static final int MAX_SPAWN_COUNT = 1_000;

    enum Error {
        NONE,
        INVALID_COUNT,
        INVALID_SPREAD,
        INVALID_ARGUMENTS
    }

    static SpawnOptions parse(String[] args) {
        int count = 1;
        boolean countUsed = false;
        boolean randomNames = false;
        String customName = null;
        int spreadRadius = 0;
        String worldName = null;
        boolean randomPositions = false;
        String maximumRank = null;
        boolean godMode = false;
        boolean donutChat = false;
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < args.length; i++) {
            String flag = args[i].toLowerCase(Locale.ROOT);
            if (!flag.startsWith("--") || !seen.add(flag)) {
                return invalid(Error.INVALID_ARGUMENTS);
            }

            switch (flag) {
                case "--random" -> randomNames = true;
                case "--count" -> {
                    countUsed = true;
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                        return invalid(Error.INVALID_COUNT);
                    }
                    try {
                        count = Integer.parseInt(args[++i]);
                        if (count < 1 || count > MAX_SPAWN_COUNT) return invalid(Error.INVALID_COUNT);
                    } catch (NumberFormatException ignored) {
                        return invalid(Error.INVALID_COUNT);
                    }
                }
                case "--name" -> {
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                        return invalid(Error.INVALID_ARGUMENTS);
                    }
                    customName = args[++i];
                }
                case "--spread" -> {
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                        return invalid(Error.INVALID_SPREAD);
                    }
                    try {
                        spreadRadius = Integer.parseInt(args[++i]);
                        if (spreadRadius < 1 || spreadRadius > MAX_SPREAD_RADIUS) {
                            return invalid(Error.INVALID_SPREAD);
                        }
                    } catch (NumberFormatException ignored) {
                        return invalid(Error.INVALID_SPREAD);
                    }
                }
                case "--world" -> {
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                        return invalid(Error.INVALID_ARGUMENTS);
                    }
                    worldName = args[++i];
                }
                case "--randompos" -> randomPositions = true;
                case "--god" -> godMode = true;
                case "--donutchat" -> donutChat = true;
                case "--rank" -> {
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                        return invalid(Error.INVALID_ARGUMENTS);
                    }
                    maximumRank = args[++i];
                }
                default -> {
                    return invalid(Error.INVALID_ARGUMENTS);
                }
            }
        }

        if (customName != null && (randomNames || countUsed || spreadRadius > 0)) {
            return invalid(Error.INVALID_ARGUMENTS);
        }
        if (spreadRadius > 0 && randomPositions) {
            return invalid(Error.INVALID_ARGUMENTS);
        }
        return new SpawnOptions(
                count,
                randomNames,
                customName,
                spreadRadius,
                worldName,
                randomPositions,
                maximumRank,
                godMode,
                donutChat,
                Error.NONE);
    }

    boolean isValid() {
        return error == Error.NONE;
    }

    private static SpawnOptions invalid(Error error) {
        return new SpawnOptions(1, false, null, 0, null, false, null, false, false, error);
    }
}
