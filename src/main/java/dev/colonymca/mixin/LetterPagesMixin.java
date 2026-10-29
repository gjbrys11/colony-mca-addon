package dev.colonymca.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Pinned MCA release writes pages as a list but reads them with getCompound, losing all text. */
@Mixin(value=PlayerSaveData.Letter.class,remap=false)
public abstract class LetterPagesMixin {
    @Redirect(method="<init>(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V",
            at=@At(value="INVOKE",target="Lcom/mojang/serialization/Codec;parse(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"))
    private static DataResult<?> colonyMca$readActualPages(Codec<Object> codec,DynamicOps<Object> ops,Object input,
                                                         CompoundTag nbt,HolderLookup.Provider registries) {
        Object pages=nbt.get("pages");
        return codec.parse(ops,pages==null?input:pages);
    }
}
