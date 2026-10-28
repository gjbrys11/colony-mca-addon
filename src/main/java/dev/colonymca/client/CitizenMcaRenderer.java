package dev.colonymca.client;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.colonymca.bridge.CitizenMcaActor;
import net.conczin.mca.client.render.VillagerEntityMCARenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/** Reuses MCA's real renderer, layered skins and models; no copied texture assets. */
public final class CitizenMcaRenderer extends EntityRenderer<AbstractEntityCitizen> {
    private final VillagerEntityMCARenderer renderer;

    public CitizenMcaRenderer(EntityRendererProvider.Context context) {
        super(context);
        renderer = new VillagerEntityMCARenderer(context);
        shadowRadius = 0.5F;
    }

    @Override public void render(AbstractEntityCitizen citizen, float yaw, float partialTick,
                                  PoseStack pose, MultiBufferSource buffer, int light) {
        if (!(citizen instanceof CitizenMcaActor bridge)) return;
        var actor = bridge.colonyMca$actor();
        actor.xo = citizen.xo; actor.yo = citizen.yo; actor.zo = citizen.zo;
        actor.xOld = citizen.xOld; actor.yOld = citizen.yOld; actor.zOld = citizen.zOld;
        actor.yBodyRot = citizen.yBodyRot; actor.yBodyRotO = citizen.yBodyRotO;
        actor.yHeadRot = citizen.yHeadRot; actor.yHeadRotO = citizen.yHeadRotO;
        actor.setYRot(citizen.getYRot()); actor.yRotO = citizen.yRotO;
        actor.setXRot(citizen.getXRot()); actor.xRotO = citizen.xRotO;
        actor.tickCount = citizen.tickCount;
        actor.attackAnim = citizen.attackAnim; actor.oAttackAnim = citizen.oAttackAnim;
        actor.hurtTime = citizen.hurtTime; actor.deathTime = citizen.deathTime;
        actor.swinging = citizen.swinging; actor.swingingArm = citizen.swingingArm;
        dev.colonymca.bridge.WalkAnimationSync.copy(citizen.walkAnimation, actor.walkAnimation);
        actor.setDeltaMovement(citizen.getDeltaMovement());
        actor.setOnGround(citizen.onGround());
        actor.setInvisible(citizen.isInvisible());
        actor.setPose(citizen.getPose());
        actor.setShiftKeyDown(citizen.isShiftKeyDown());
        actor.setAggressive(citizen.isAggressive());
        for (EquipmentSlot slot : EquipmentSlot.values()) actor.setItemSlot(slot, citizen.getItemBySlot(slot).copy());
        if (citizen.isUsingItem()) {
            actor.startUsingItem(citizen.getUsedItemHand());
            ((dev.colonymca.mixin.LivingEntityUseAccessor) actor).colonyMca$setUseItemRemaining(citizen.getUseItemRemainingTicks());
        }
        else actor.stopUsingItem();
        renderer.render(actor, yaw, partialTick, pose, buffer, light);
    }

    @Override public ResourceLocation getTextureLocation(AbstractEntityCitizen citizen) {
        return renderer.getTextureLocation(((CitizenMcaActor) citizen).colonyMca$actor());
    }
}
