package dev.colonymca.smoke;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;

final class GameplayProfileChecks {
    static int run(ServerLevel world) {
        require(com.arxyt.colonypathingedition.core.config.PathingConfig.BUILDER_MODE.get().name().equals("SENTRY"),"SENTRY builder config active");
        require(!com.arxyt.colonypathingedition.core.config.PathingConfig.ALLOW_RESURRECT.get(),"extra totem resurrection disabled");
        require(steve_gall.minecolonies_tweaks.core.common.config.MCTweaksConfigServer.INSTANCE.jobs.blockBuildingDelay.get()==1,"1 tick building setting active");
        var planks=BuiltInRegistries.ITEM.get(ResourceLocation.parse("material_unification:planks_unification"));
        require(planks!=Items.AIR,"KubeJS unified resources registered");
        var input=CraftingInput.of(2,2,java.util.Collections.nCopies(4,new ItemStack(planks)));
        var recipe=world.getServer().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,world).orElseThrow();
        require(recipe.value().assemble(input,world.registryAccess()).is(Items.CRAFTING_TABLE),"native crafting consumes unified planks for crafting table");
        var all=com.minecolonies.core.colony.crafting.CustomRecipeManager.getInstance().getAllRecipes();
        long count=all.values().stream().flatMap(map->map.keySet().stream()).filter(id->id.getNamespace().equals("material_unification_minecolonies")).count();
        require(count>=1100,"native colony custom recipes loaded: "+count);
        var homes=new com.minecolonies.core.colony.buildings.modules.LivingBuildingModule();
        var buildingType=com.minecolonies.api.colony.buildings.IBuilding.class;
        homes.setBuilding((com.minecolonies.api.colony.buildings.IBuilding)java.lang.reflect.Proxy.newProxyInstance(buildingType.getClassLoader(),new Class[]{buildingType},
                (proxy,method,args)->method.getName().equals("getBuildingLevel")?4:null));
        require(homes.getModuleMax()==4,"native housing capacity retained despite pathing addon");
        return 7;
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
