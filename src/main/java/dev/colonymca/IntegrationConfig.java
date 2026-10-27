package dev.colonymca;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class IntegrationConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue FAMILY_WEIGHT;
    public static final ModConfigSpec.DoubleValue FAMILY_TOGETHER;
    public static final ModConfigSpec.DoubleValue FAMILY_APART;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("happiness");
        FAMILY_WEIGHT = builder.comment("Weight of the family housing happiness factor.")
                .defineInRange("familyHousingWeight", 1.0, 0.0, 10.0);
        FAMILY_TOGETHER = builder.comment("Factor when all known resident family members share a home. 1 is neutral.")
                .defineInRange("familyTogetherFactor", 1.1, 0.0, 2.0);
        FAMILY_APART = builder.comment("Factor when a known resident family member lives elsewhere or has no home.")
                .defineInRange("familyApartFactor", 0.9, 0.0, 2.0);
        builder.pop();
        SPEC = builder.build();
    }

    private IntegrationConfig() {}
}
