package dev.colonymca.bridge;

import com.minecolonies.core.entity.citizen.EntityCitizen;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class ChildGrowthBridge {
    private static final Map<EntityCitizen, WeakReference<ChildGrowth>> CHILDREN =
            Collections.synchronizedMap(new WeakHashMap<>());
    private ChildGrowthBridge() {}

    public static void register(EntityCitizen child, ChildGrowth ai) { CHILDREN.put(child,new WeakReference<>(ai)); }
    public static void advance(EntityCitizen child, int seconds) {
        if (!child.isBaby() || seconds <= 0 || child.level().isClientSide) return;
        var reference=CHILDREN.get(child);
        var ai=reference==null?null:reference.get();
        if(ai!=null) ai.colonyMca$addGrowth((int)Math.min(Integer.MAX_VALUE,(long)seconds*20));
        ((CitizenMcaActor)child).colonyMca$actor();
        ((CitizenMcaActor)child).colonyMca$flush();
    }
}
