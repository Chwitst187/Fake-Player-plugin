package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import me.bill.fakePlayerPlugin.util.TextUtil;

/** Opt-in canned trade chat for bots spawned with {@code --donutchat}. */
public final class DonutChatController {

    public static final String METADATA_KEY = "fpp.donut-chat";

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;
    private final Map<UUID, Integer> previousMessage = new ConcurrentHashMap<>();
    private final TickMessageDistributor messageDistributor = new TickMessageDistributor();
    private int taskId = -1;
    private int targetMessagesPerSecond;
    private int rateTicksRemaining;
    private int botCursor;

    public DonutChatController(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void start() {
        if (taskId != -1) return;
        taskId = FppScheduler.runSyncRepeatingWithId(plugin, this::tick, 20L, 1L);
    }

    public void shutdown() {
        if (taskId != -1) FppScheduler.cancelTask(taskId);
        taskId = -1;
        previousMessage.clear();
        messageDistributor.reset();
        targetMessagesPerSecond = 0;
        rateTicksRemaining = 0;
        botCursor = 0;
    }

    private void tick() {
        List<String> messages = Config.donutChatMessages();
        if (messages.isEmpty() || !hasRealAudience()) {
            messageDistributor.reset();
            rateTicksRemaining = 0;
            return;
        }

        List<FakePlayer> bots = null;
        for (FakePlayer fp : manager.getActivePlayers()) {
            if (!canChat(fp)) {
                continue;
            }
            if (bots == null) {
                bots = new ArrayList<>();
            }
            bots.add(fp);
        }

        if (bots == null || bots.isEmpty()) {
            messageDistributor.reset();
            rateTicksRemaining = 0;
            return;
        }

        if (rateTicksRemaining <= 0) {
            targetMessagesPerSecond = randomMessagesPerSecond();
            rateTicksRemaining = 20;

            Set<UUID> enabledBots = new HashSet<>();
            for (FakePlayer bot : bots) enabledBots.add(bot.getUuid());
            previousMessage.keySet().removeIf(uuid -> !enabledBots.contains(uuid));
        }

        int messagesThisTick = messageDistributor.advance(targetMessagesPerSecond);
        rateTicksRemaining--;

        for (int i = 0; i < messagesThisTick; i++) {
            FakePlayer fp = bots.get(Math.floorMod(botCursor++, bots.size()));
            UUID uuid = fp.getUuid();
            int messageIndex = chooseMessageIndex(messages.size(), previousMessage.getOrDefault(uuid, -1));
            previousMessage.put(uuid, messageIndex);
            broadcast(fp, messages.get(messageIndex));
        }
    }

    private static boolean canChat(FakePlayer fp) {
        if (!Boolean.TRUE.equals(fp.getMetadata(METADATA_KEY))) return false;
        Player body = fp.getPhysicsEntity();
        return body != null && body.isOnline() && !body.isDead();
    }

    private boolean hasRealAudience() {
        return !manager.getRealPlayers().isEmpty();
    }

    private void broadcast(FakePlayer fp, String rawMessage) {
        String botName = fp.getName();
        String message = rawMessage.replace("{bot}", botName);
        String line = Config.donutChatFormat()
                .replace("{bot}", botName)
                .replace("{display_name}", BotBroadcast.resolveDisplayName(fp))
                .replace("{message}", message);
        plugin.getServer().broadcast(TextUtil.colorize(line));
        Config.debugChat("Donut chat <" + botName + "> " + message);
    }

    private static int randomMessagesPerSecond() {
        int minimum = Config.donutChatMinMessagesPerSecond();
        int maximum = Config.donutChatMaxMessagesPerSecond();
        return minimum == maximum ? minimum : ThreadLocalRandom.current().nextInt(minimum, maximum + 1);
    }

    static int chooseMessageIndex(int messageCount, int previousIndex) {
        if (messageCount <= 1) return 0;
        if (previousIndex < 0 || previousIndex >= messageCount) {
            return ThreadLocalRandom.current().nextInt(messageCount);
        }
        int selected = ThreadLocalRandom.current().nextInt(messageCount - 1);
        return selected >= previousIndex ? selected + 1 : selected;
    }
}

final class TickMessageDistributor {

    private int messageUnits;

    int advance(int messagesPerSecond) {
        messageUnits += Math.max(0, messagesPerSecond);
        int messagesThisTick = messageUnits / 20;
        messageUnits %= 20;
        return messagesThisTick;
    }

    void reset() {
        messageUnits = 0;
    }
}
