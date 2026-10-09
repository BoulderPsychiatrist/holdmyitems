package com.leclowndu93150.holdmyitems.config;

import com.leclowndu93150.holdmyitems.HoldMyItems;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.regex.Pattern;

public class HoldMyItemsClientConfig {
    public static final ForgeConfigSpec CLIENT_CONFIG;
    public static final ForgeConfigSpec.DoubleValue ANIMATION_SPEED;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SWIMMING_ANIM;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CLIMB_AND_CRAWL;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PUNCHING;
    public static final ForgeConfigSpec.DoubleValue VIEWMODEL_X_OFFSET;
    public static final ForgeConfigSpec.DoubleValue VIEWMODEL_Y_OFFSET;
    public static final ForgeConfigSpec.DoubleValue VIEWMODEL_Z_OFFSET;
    public static final ForgeConfigSpec.BooleanValue MB3D_COMPAT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MODS_THAT_HANDLE_THEIR_OWN_RENDERING;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLED_ITEMS_STRINGS;

    private static final List<Pattern> disabledItemPatterns = new ArrayList<Pattern>();
    private static final Set<ResourceLocation> disabledItemIds = new HashSet<ResourceLocation>();
    private static boolean initialized = false;

    private HoldMyItemsClientConfig() {}

    private static void initPatterns() {
        if (initialized) return;
        initialized = true;
        disabledItemIds.clear();
        disabledItemPatterns.clear();

        for (String itemName : DISABLED_ITEMS_STRINGS.get()) {
            if (itemName.contains("*")) {
                try {
                    String regex = itemName.replace(".", "\\.").replace("*", ".*");
                    disabledItemPatterns.add(Pattern.compile(regex));
                } catch (Exception e) {
                    HoldMyItems.LOGGER.error("Invalid pattern in config: {}", itemName, e);
                }
            } else {
                ResourceLocation id = ResourceLocation.tryCreate(itemName);
                if (id != null) {
                    disabledItemIds.add(id);
                } else {
                    HoldMyItems.LOGGER.warn("Invalid item id in config: {}", itemName);
                }
            }
        }
    }

    public static boolean isItemDisabled(Item item) {
        if (item == null) return false;
        initPatterns();

        ResourceLocation itemId = item.getRegistryName();
        if (itemId == null) return false;

        if (disabledItemIds.contains(itemId)) {
            return true;
        }

        String idString = itemId.toString();
        for (Pattern pattern : disabledItemPatterns) {
            if (pattern.matcher(idString).matches()) {
                return true;
            }
        }
        return false;
    }

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("animations");
        ANIMATION_SPEED = builder
                .comment("Choose your preferred animation speed (1-15)")
                .defineInRange("animationSpeed", 8.0D, 1.0D, 15.0D);
        ENABLE_SWIMMING_ANIM = builder
                .comment("Enable or disable swimming animation")
                .define("enableSwimmingAnimation", true);
        ENABLE_CLIMB_AND_CRAWL = builder
                .comment("Enable or disable climb and crawl animation")
                .define("enableClimbAndCrawlAnimation", true);
        ENABLE_PUNCHING = builder
                .comment("Enable or disable punching animation")
                .define("enablePunchingAnimation", true);
        builder.pop();

        builder.push("positions");
        VIEWMODEL_X_OFFSET = builder
                .comment("Viewmodel X Offset")
                .defineInRange("viewmodelXOffset", 0.0D, -10.0D, 10.0D);
        VIEWMODEL_Y_OFFSET = builder
                .comment("Viewmodel Y Offset")
                .defineInRange("viewmodelYOffset", 0.0D, -10.0D, 10.0D);
        VIEWMODEL_Z_OFFSET = builder
                .comment("Viewmodel Z Offset")
                .defineInRange("viewmodelZOffset", 0.0D, -10.0D, 10.0D);
        builder.pop();

        builder.push("misc");
        MB3D_COMPAT = builder
                .comment("Enable MB3D compatibility mode")
                .define("mb3DCompat", false);
        builder.pop();

        builder.push("modRenderExclusions");
        MODS_THAT_HANDLE_THEIR_OWN_RENDERING = builder
                .comment("List of mod IDs whose items handle their own first-person rendering. Hold My Items will skip its custom logic when such an item is held.")
                .defineList("modsThatHandleTheirOwnRendering",
                        Arrays.asList("pointblank", "jeg"),
                        obj -> obj instanceof String);
        builder.pop();

        builder.push("itemRenderExclusions");
        DISABLED_ITEMS_STRINGS = builder
                .comment("List of items to disable custom rendering for. Can use patterns with * as wildcard.")
                .defineList("disabledItems",
                        new ArrayList<String>(),
                        obj -> obj instanceof String);
        builder.pop();

        CLIENT_CONFIG = builder.build();
    }
}
