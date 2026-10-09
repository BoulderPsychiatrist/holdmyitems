package com.leclowndu93150.holdmyitems.client;

import com.leclowndu93150.holdmyitems.config.HoldMyItemsClientConfig;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;

import java.util.Locale;

public class HoldMyItemsConfigScreen extends Screen {

    private final Screen parent;

    public HoldMyItemsConfigScreen(Screen parent) {
        super(new StringTextComponent("Hold My Items"));
        this.parent = parent;
    }

    /** Registers this screen as the mod's "Config" button in the mod list. Client only. */
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY,
                () -> (mc, parentScreen) -> new HoldMyItemsConfigScreen(parentScreen));
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 155;
        int right = this.width / 2 + 5;
        int y = 40;
        int step = 24;

        addNumeric(left, y, "Animation speed", HoldMyItemsClientConfig.ANIMATION_SPEED, 1.0D, 1.0D, 15.0D, 8.0D);
        addNumeric(left, y + step, "Viewmodel X", HoldMyItemsClientConfig.VIEWMODEL_X_OFFSET, 0.05D, -10.0D, 10.0D, 0.0D);
        addNumeric(left, y + step * 2, "Viewmodel Y", HoldMyItemsClientConfig.VIEWMODEL_Y_OFFSET, 0.05D, -10.0D, 10.0D, 0.0D);
        addNumeric(left, y + step * 3, "Viewmodel Z", HoldMyItemsClientConfig.VIEWMODEL_Z_OFFSET, 0.05D, -10.0D, 10.0D, 0.0D);

        addToggle(right, y, "Swimming animation", HoldMyItemsClientConfig.ENABLE_SWIMMING_ANIM);
        addToggle(right, y + step, "Climb & crawl animation", HoldMyItemsClientConfig.ENABLE_CLIMB_AND_CRAWL);
        addToggle(right, y + step * 2, "Punching animation", HoldMyItemsClientConfig.ENABLE_PUNCHING);
        addToggle(right, y + step * 3, "MB3D compatibility", HoldMyItemsClientConfig.MB3D_COMPAT);

        this.addButton(new Button(this.width / 2 - 100, this.height - 40, 200, 20,
                new StringTextComponent("Done"), b -> this.closeScreen()));
    }

    private void addNumeric(int x, int y, final String name, final ForgeConfigSpec.DoubleValue value,
                            final double stepSize, final double min, final double max, final double def) {
        final Button[] center = new Button[1];
        this.addButton(new Button(x, y, 20, 20, new StringTextComponent("-"), b -> {
            setValue(value, value.get() - stepSize, min, max);
            center[0].setMessage(label(name, value));
        }));
        center[0] = this.addButton(new Button(x + 22, y, 106, 20, label(name, value), b -> {
            setValue(value, def, min, max);
            b.setMessage(label(name, value));
        }));
        this.addButton(new Button(x + 130, y, 20, 20, new StringTextComponent("+"), b -> {
            setValue(value, value.get() + stepSize, min, max);
            center[0].setMessage(label(name, value));
        }));
    }

    private void addToggle(int x, int y, final String name, final ForgeConfigSpec.BooleanValue value) {
        this.addButton(new Button(x, y, 150, 20, toggleLabel(name, value), b -> {
            value.set(!value.get());
            b.setMessage(toggleLabel(name, value));
        }));
    }

    private static void setValue(ForgeConfigSpec.DoubleValue value, double v, double min, double max) {
        double clamped = Math.max(min, Math.min(max, v));
        value.set(Math.round(clamped * 100.0D) / 100.0D);
    }

    private static StringTextComponent label(String name, ForgeConfigSpec.DoubleValue value) {
        return new StringTextComponent(name + ": " + String.format(Locale.ROOT, "%.2f", value.get()));
    }

    private static StringTextComponent toggleLabel(String name, ForgeConfigSpec.BooleanValue value) {
        return new StringTextComponent(name + ": " + (value.get() ? "ON" : "OFF"));
    }

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(ms);
        drawCenteredString(ms, this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        drawCenteredString(ms, this.font, new StringTextComponent("Click the middle button to reset a value"),
                this.width / 2, this.height - 58, 0xAAAAAA);
        super.render(ms, mouseX, mouseY, partialTicks);
    }

    @Override
    public void closeScreen() {
        HoldMyItemsClientConfig.CLIENT_CONFIG.save();
        this.minecraft.displayGuiScreen(this.parent);
    }
}
