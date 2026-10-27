package dev.colonymca.bridge;

import net.conczin.mca.entity.VillagerEntityMCA;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class ActorInteractions {
    private static final Map<VillagerEntityMCA, WeakReference<ColonyInteractions>> HANDLERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private ActorInteractions() {}

    public static void register(VillagerEntityMCA actor, ColonyInteractions interactions) {
        HANDLERS.put(actor, new WeakReference<>(interactions));
    }

    public static ColonyInteractions get(VillagerEntityMCA actor) {
        var reference = HANDLERS.get(actor);
        return reference == null ? null : reference.get();
    }
}
