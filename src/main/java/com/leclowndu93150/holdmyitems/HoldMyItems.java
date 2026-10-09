package com.leclowndu93150.holdmyitems;

import com.leclowndu93150.holdmyitems.config.HoldMyItemsClientConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(HoldMyItems.MODID)
public class HoldMyItems {
    public static final String MODID = "holdmyitems";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static double deltaTime = 0.0D;

    public HoldMyItems() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, HoldMyItemsClientConfig.CLIENT_CONFIG);
    }
}
