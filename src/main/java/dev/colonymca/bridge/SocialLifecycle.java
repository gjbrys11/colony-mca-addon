package dev.colonymca.bridge;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;

/** Advances MCA's social clocks without ticking its movement AI, pregnancy or inventory. */
public final class SocialLifecycle {
    private SocialLifecycle() {}

    public static void tick(VillagerEntityMCA actor, CompoundTag control, long now) {
        long fatigueSteps = elapsed(control, "fatigueRecovery", now, Config.getInstance().interactionFatigueCooldown);
        if (fatigueSteps > 0) {
            for (var memory : actor.getVillagerBrain().getMemories().values()) {
                if (memory.getInteractionFatigue() > 0)
                    memory.setInteractionFatigue((int) Math.max(0L, memory.getInteractionFatigue() - fatigueSteps));
            }
        }
        long giftSteps = elapsed(control, "giftRecovery", now, Config.getInstance().giftDesaturationReset);
        var gifts = actor.getRelationships().getGiftSaturation();
        int count = gifts.toNbt().size();
        for (int i = 0; i < Math.min(giftSteps, count); i++) gifts.pop();
        if (elapsed(control, "moodRecovery", now, 12000) > 0) {
            int baseline = Math.round(actor.getVillagerBrain().getMoodValue() / 12.0F);
            actor.getVillagerBrain().modifyMoodValue(actor.getRandom().nextInt(7) - 3 - baseline);
        }
    }

    private static long elapsed(CompoundTag control, String key, long now, int configuredPeriod) {
        long period = Math.max(1, configuredPeriod);
        if (!control.contains(key) || now < control.getLong(key)) {
            control.putLong(key, now);
            return 0;
        }
        long steps = (now - control.getLong(key)) / period;
        if (steps > 0) control.putLong(key, now - (now - control.getLong(key)) % period);
        return steps;
    }
}
