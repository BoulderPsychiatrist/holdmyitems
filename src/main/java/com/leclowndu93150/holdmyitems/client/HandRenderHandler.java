package com.leclowndu93150.holdmyitems.client;

import com.leclowndu93150.holdmyitems.HoldMyItems;
import com.leclowndu93150.holdmyitems.config.HoldMyItemsClientConfig;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.PlayerRenderer;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.Pose;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.*;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.HandSide;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.model.data.EmptyModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

/**
 * Forge 1.16.5 port. The original mod used a mixin on ItemInHandRenderer; here the same logic runs
 * from RenderHandEvent (cancelled when the mod renders the hand itself).
 */
@Mod.EventBusSubscriber(modid = HoldMyItems.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class HandRenderHandler {

    private static boolean failed = false;

    private static double prevTime = 0.0D;

    private static boolean repPower = false;
    private static float prevAge = 0.0F;
    private static double previousRotation = 0.0D;
    private static float swingAngleY = 0.0F;
    private static float swingAngleX = 0.0F;
    private static float swingVelocityY = 0.0F;
    private static float swingVelocityX = 0.0F;
    private static float swingVelocityZ = 0.0F;
    private static float vertAngleY = 0.0F;
    private static float vertVelocityYSlime = 0.0F;
    private static float vertAngleYSlime = 0.0F;
    private static float riptideCounter = 0.0F;
    private static float netherCounter = 0.0F;
    private static float inWaterCounter = 0.0F;
    private static float clCount = 0.0F;
    private static float crawlCount = 0.0F;
    private static float directionalCrawlCount = 0.0F;
    private static float climbCount = 0.0F;
    private static float mouseHolding = 1.0F;
    private static boolean isAttacking = false;
    private static boolean left = false;

    private HandRenderHandler() {}

    // ------------------------------------------------------------------ event

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (failed) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null) {
            return;
        }
        if (event.getHand() == Hand.MAIN_HAND) {
            updateDeltaTime(mc);
        }
        try {
            boolean handled = render(mc, p, event.getPartialTicks(), event.getInterpolatedPitch(), event.getHand(),
                    event.getSwingProgress(), event.getItemStack(), event.getEquipProgress(),
                    event.getMatrixStack(), event.getBuffers(), event.getLight());
            if (handled) {
                event.setCanceled(true);
            }
        } catch (Throwable t) {
            failed = true;
            HoldMyItems.LOGGER.error("Hold My Items rendering failed, falling back to vanilla hand rendering", t);
        }
    }

    private static void updateDeltaTime(Minecraft mc) {
        double now = GLFW.glfwGetTime();
        double dt = now - prevTime;
        prevTime = now;
        if (mc.isGamePaused()) {
            dt = 0.0D;
        } else {
            dt = Math.min(0.05D, dt);
        }
        HoldMyItems.deltaTime = dt;
    }

    // ------------------------------------------------------------------ helpers

    private static float easeInOutBack(float x) {
        float c1 = 1.70158F;
        float c2 = c1 * 1.525F;
        return (float) ((double) x < 0.5D
                ? Math.pow(2.0F * x, 2.0D) * ((c2 + 1.0F) * 2.0F * x - c2) / 2.0D
                : (Math.pow(2.0F * x - 2.0F, 2.0D) * ((c2 + 1.0F) * (x * 2.0F - 2.0F) + c2) + 2.0D) / 2.0D);
    }

    private static float getAttackDamage(ItemStack stack) {
        float total = 0.0F;
        for (AttributeModifier m : stack.getAttributeModifiers(EquipmentSlotType.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
            total += (float) m.getAmount();
        }
        return total;
    }

    private static boolean isCrawling(ClientPlayerEntity p) {
        return p.getPose() == Pose.SWIMMING && !p.isInWater();
    }

    private static boolean isLantern(Item item) {
        return item == Items.LANTERN || item == Items.SOUL_LANTERN;
    }

    private static boolean isBucket(Item item) {
        return item instanceof BucketItem || item == Items.MILK_BUCKET;
    }

    private static boolean isTool(Item item) {
        return item instanceof ToolItem || item instanceof ShearsItem || item == Items.FLINT_AND_STEEL
                || item == Items.CARROT_ON_A_STICK || item == Items.WARPED_FUNGUS_ON_A_STICK
                || item instanceof FishingRodItem;
    }

    private static boolean isArmorOrBook(Item item) {
        return item instanceof ArmorItem || item instanceof EnchantedBookItem || item instanceof WritableBookItem
                || item instanceof WrittenBookItem || item == Items.BOOK;
    }

    private static void altSwing(MatrixStack ms, HandSide arm, float swingProgress) {
        int direction = arm == HandSide.RIGHT ? 1 : -1;
        float swingSin = MathHelper.sin(swingProgress * (float) Math.PI);
        ms.rotate(Vector3f.YP.rotationDegrees((float) direction * (45.0F + swingSin * 0.0F)));
        ms.rotate(Vector3f.YP.rotationDegrees((float) direction * -45.0F));
    }

    private static void transformSide(MatrixStack ms, HandSide side, float equipProgress) {
        int i = side == HandSide.RIGHT ? 1 : -1;
        ms.translate((double) ((float) i * 0.56F), (double) (-0.52F + equipProgress * -0.6F), -0.72D);
    }

    private static void transformAttack(MatrixStack ms, HandSide side, float swingProgress) {
        int i = side == HandSide.RIGHT ? 1 : -1;
        float f = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        ms.rotate(Vector3f.YP.rotationDegrees((float) i * (45.0F + f * -20.0F)));
        float f1 = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        ms.rotate(Vector3f.ZP.rotationDegrees((float) i * f1 * -20.0F));
        ms.rotate(Vector3f.XP.rotationDegrees(f1 * -80.0F));
        ms.rotate(Vector3f.YP.rotationDegrees((float) i * -45.0F));
    }

    private static void renderPlayerArm(Minecraft mc, ClientPlayerEntity player, MatrixStack ms, IRenderTypeBuffer buffer,
                                        int light, float equipProgress, float swingProgress, HandSide side) {
        boolean right = side != HandSide.LEFT;
        float f = right ? 1.0F : -1.0F;
        float f1 = MathHelper.sqrt(swingProgress);
        float f2 = -0.3F * MathHelper.sin(f1 * (float) Math.PI);
        float f3 = 0.4F * MathHelper.sin(f1 * ((float) Math.PI * 2F));
        float f4 = -0.4F * MathHelper.sin(swingProgress * (float) Math.PI);
        ms.translate((double) (f * (f2 + 0.64000005F)), (double) (f3 + -0.6F + equipProgress * -0.6F), (double) (f4 + -0.71999997F));
        ms.rotate(Vector3f.YP.rotationDegrees(f * 45.0F));
        float f5 = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float f6 = MathHelper.sin(f1 * (float) Math.PI);
        ms.rotate(Vector3f.YP.rotationDegrees(f * f6 * 70.0F));
        ms.rotate(Vector3f.ZP.rotationDegrees(f * f5 * -20.0F));
        ms.translate((double) (f * -1.0F), 3.6D, 3.5D);
        ms.rotate(Vector3f.ZP.rotationDegrees(f * 120.0F));
        ms.rotate(Vector3f.XP.rotationDegrees(200.0F));
        ms.rotate(Vector3f.YP.rotationDegrees(f * -135.0F));
        ms.translate((double) (f * 5.6F), 0.0D, 0.0D);
        PlayerRenderer renderer = (PlayerRenderer) mc.getRenderManager().getRenderer(player);
        if (right) {
            renderer.renderRightArm(ms, buffer, light, player);
        } else {
            renderer.renderLeftArm(ms, buffer, light, player);
        }
    }

    private static boolean isBlockedNamespace(Item item, List<? extends String> blockedModIds) {
        ResourceLocation id = item.getRegistryName();
        if (id == null) {
            return false;
        }
        String namespace = id.getNamespace().toLowerCase();
        for (String modId : blockedModIds) {
            if (namespace.equalsIgnoreCase(modId)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ main render

    /** @return true if the hand was rendered here (vanilla rendering must be cancelled). */
    private static boolean render(Minecraft mc, ClientPlayerEntity p, float partialTicks, float pitch, Hand hand,
                                  float swingProgress, ItemStack stack, float equipProgress,
                                  MatrixStack ms, IRenderTypeBuffer buffer, int light) {
        boolean swimming = p.isSwimming();
        boolean crawling = isCrawling(p);
        boolean climbing = p.isOnLadder();

        if (!(HoldMyItemsClientConfig.ENABLE_PUNCHING.get() || !stack.isEmpty() || swimming || crawling || climbing)) {
            return false;
        }

        Item item = stack.getItem();

        // vanilla keeps handling maps and crossbows
        if (item == Items.FILLED_MAP || item instanceof CrossbowItem) {
            return false;
        }
        if (HoldMyItemsClientConfig.isItemDisabled(item)) {
            return false;
        }
        List<? extends String> blockedModIds = HoldMyItemsClientConfig.MODS_THAT_HANDLE_THEIR_OWN_RENDERING.get();
        if (isBlockedNamespace(item, blockedModIds) || isBlockedNamespace(p.getHeldItemMainhand().getItem(), blockedModIds)) {
            return false;
        }

        float yaw = p.rotationYaw;
        double radians = Math.toRadians((double) yaw);
        double forwardX = -Math.sin(radians);
        double forwardZ = Math.cos(radians);
        Vector3d horizontalVelocity = p.getMotion();
        double dotProduct = horizontalVelocity.x * forwardX + horizontalVelocity.z * forwardZ;
        double crossProduct = horizontalVelocity.x * forwardZ - horizontalVelocity.z * forwardX;
        float al;
        if (p.rotationPitch != 0.0F) {
            al = 90.0F / p.rotationPitch / 10.0F;
        } else {
            al = 1.0F;
        }
        if (al > 1.0F) {
            al = 1.0F;
        }
        if (al < 0.0F) {
            al = 1.0F;
        }

        boolean bl = hand == Hand.MAIN_HAND;
        HandSide arm = bl ? p.getPrimaryHand() : p.getPrimaryHand().opposite();
        float kj = bl ? 1.0F : -1.0F;

        UseAction useAction = stack.getUseAction();
        Block blk = Block.getBlockFromItem(item);
        BlockState defState = blk.getDefaultState();
        boolean hasBlock = blk != Blocks.AIR;
        boolean sword = item instanceof SwordItem;
        boolean axe = item instanceof AxeItem;
        boolean shovel = item instanceof ShovelItem;
        boolean hoe = item instanceof HoeItem;
        boolean tool = isTool(item);
        boolean lantern = isLantern(item);
        boolean bucket = isBucket(item);
        boolean shears = item instanceof ShearsItem;
        boolean fishing = item instanceof FishingRodItem;
        boolean sticks = item == Items.WARPED_FUNGUS_ON_A_STICK || item == Items.CARROT_ON_A_STICK;
        boolean armorOrBook = isArmorOrBook(item);
        float attackDamage = getAttackDamage(stack);
        boolean toolLike = tool && !armorOrBook && useAction != UseAction.EAT && stack.isEnchantable();
        // "small item" hold style: everything that is not a weapon / tool / bow / shield
        boolean smallItem = !toolLike && useAction != UseAction.BOW && attackDamage == 0.0F && useAction != UseAction.BLOCK
                && !sticks && !fishing && !shears;

        ms.push();
        ms.push();
        ms.translate(HoldMyItemsClientConfig.VIEWMODEL_X_OFFSET.get() * (double) kj,
                HoldMyItemsClientConfig.VIEWMODEL_Y_OFFSET.get(),
                HoldMyItemsClientConfig.VIEWMODEL_Z_OFFSET.get());
        double tt = HoldMyItems.deltaTime * HoldMyItemsClientConfig.ANIMATION_SPEED.get();
        float swing_rot = (double) swingProgress < 0.6D
                ? MathHelper.sin(MathHelper.clamp(swingProgress, 0.0F, 0.12506F) * 12.56F)
                : MathHelper.sin(MathHelper.clamp(swingProgress, 0.62532F, 0.75038F) * 12.56F);
        float swing = MathHelper.sin(swingProgress * 3.14F);
        swing = easeInOutBack(swing);

        boolean throwable = item == Items.EXPERIENCE_BOTTLE || item == Items.EGG || item == Items.ENDER_EYE
                || item == Items.SNOWBALL || item == Items.ENDER_PEARL || item instanceof SplashPotionItem
                || item instanceof LingeringPotionItem;
        if (throwable && p.getHeldItemOffhand().isEmpty() && useAction != UseAction.SPEAR && item != Items.FIRE_CHARGE
                && !swimming && !crawling && !climbing) {
            if (p.getPrimaryHand() == HandSide.LEFT) {
                bl = !bl;
            }

            float ll = bl ? 1.0F : -1.0F;
            ms.push();
            ms.rotate(Vector3f.YP.rotationDegrees(-25.0F * ll));
            ms.rotate(Vector3f.XP.rotationDegrees(-10.0F));
            ms.rotate(Vector3f.YP.rotationDegrees(25.0F * ll * swing));
            ms.rotate(Vector3f.XP.rotationDegrees(30.0F * swing));
            ms.translate(-0.15D * (double) ll, 0.1D, 0.1D);
            ms.translate(0.0D, -0.55D * (double) swing, 0.4D * (double) swing * 3.14D);
            renderPlayerArm(mc, p, ms, buffer, light, equipProgress, 0.0F, arm.opposite());
            ms.pop();
        }

        if (mc.gameSettings.keyBindAttack.isKeyDown() && !isAttacking && (double) swingProgress == 0.0D) {
            left = !left;
        }

        if (!stack.isEmpty()) {
            if (p.getPrimaryHand() == HandSide.LEFT) {
                bl = !bl;
            }

            float ll = bl ? 1.0F : -1.0F;
            if ((left || axe || useAction == UseAction.SPEAR || useAction == UseAction.BLOCK) && !shovel) {
                if (!sword && !axe) {
                    if (useAction == UseAction.SPEAR) {
                        ms.translate(0.0D, 0.0D, 0.45D * (double) swing_rot);
                        ms.translate(-0.25D * (double) kj * (double) swing, -0.35D * (double) swing_rot, -0.6D * (double) swing);
                        ms.translate(0.0D, 0.1D * (double) swing, 0.0D);
                        ms.rotate(Vector3f.YP.rotationDegrees(15.0F * swing_rot * ll));
                        ms.rotate(Vector3f.ZP.rotationDegrees(30.0F * swing_rot * ll));
                    } else if (tool && useAction != UseAction.BLOCK && !shovel) {
                        ms.translate(0.1D * (double) ll * (double) swing_rot, 0.1D * (double) swing_rot, -0.5D * (double) swing);
                        ms.rotate(Vector3f.XN.rotationDegrees(-30.0F * swing_rot));
                        ms.rotate(Vector3f.ZP.rotationDegrees(-20.0F * swing_rot * ll));
                        ms.rotate(Vector3f.XN.rotationDegrees(40.0F * swing));
                    } else if (useAction != UseAction.BLOCK) {
                        ms.translate(0.1D * (double) ll * (double) swing_rot, 0.1D * (double) swing_rot, -0.1D * (double) swing);
                        ms.rotate(Vector3f.XN.rotationDegrees(-30.0F * swing_rot));
                        ms.rotate(Vector3f.ZP.rotationDegrees(-10.0F * swing_rot * ll));
                        ms.rotate(Vector3f.XN.rotationDegrees(40.0F * swing));
                        ms.rotate(Vector3f.YP.rotationDegrees(10.0F * swing * ll));
                    } else {
                        ms.translate(0.1D * (double) ll * (double) swing_rot, 0.1D * (double) swing_rot, -0.2D * (double) swing);
                        ms.rotate(Vector3f.XN.rotationDegrees(-10.0F * swing_rot));
                        ms.rotate(Vector3f.ZP.rotationDegrees(-10.0F * swing_rot * ll));
                        ms.rotate(Vector3f.XN.rotationDegrees(20.0F * swing));
                    }
                } else {
                    ms.translate(0.8D * (double) ll * (double) swing_rot, 0.3D * (double) swing_rot, -0.5D * (double) swing);
                    ms.rotate(Vector3f.YP.rotationDegrees(15.0F * swing_rot * ll));
                    ms.rotate(Vector3f.XN.rotationDegrees(-20.0F * swing_rot));
                    ms.rotate(Vector3f.ZP.rotationDegrees(-70.0F * swing_rot * ll));
                    if (sword) {
                        ms.rotate(Vector3f.XN.rotationDegrees(40.0F * swing));
                    } else {
                        ms.rotate(Vector3f.XN.rotationDegrees(30.0F * swing));
                    }
                }
            } else if (!shovel) {
                if (sword) {
                    ms.translate(-0.55D * (double) ll * (double) swing_rot, -0.8D * (double) swing_rot, -0.77D * (double) swing);
                    ms.rotate(Vector3f.YP.rotationDegrees(5.0F * swing_rot * ll));
                    ms.rotate(Vector3f.XN.rotationDegrees(-30.0F * swing_rot));
                    ms.rotate(Vector3f.ZP.rotationDegrees(70.0F * swing_rot * ll));
                    ms.rotate(Vector3f.XN.rotationDegrees(50.0F * swing));
                } else if (tool) {
                    ms.translate(0.1D * (double) ll * (double) swing_rot, 0.1D * (double) swing_rot, -0.5D * (double) swing);
                    ms.rotate(Vector3f.XN.rotationDegrees(-30.0F * swing_rot));
                    ms.rotate(Vector3f.ZP.rotationDegrees(-20.0F * swing_rot * ll));
                    ms.rotate(Vector3f.XN.rotationDegrees(40.0F * swing));
                } else {
                    ms.translate(0.1D * (double) ll * (double) swing_rot, 0.1D * (double) swing_rot, -0.1D * (double) swing);
                    ms.rotate(Vector3f.XN.rotationDegrees(-30.0F * swing_rot));
                    ms.rotate(Vector3f.ZP.rotationDegrees(-10.0F * swing_rot * ll));
                    ms.rotate(Vector3f.XN.rotationDegrees(40.0F * swing));
                    ms.rotate(Vector3f.YP.rotationDegrees(10.0F * swing * ll));
                }
            } else {
                ms.translate(0.0D, 0.15D * (double) swing_rot, -0.25D * (double) swing_rot);
                ms.translate(0.0D, 0.0D, -0.2D * (double) swing);
                ms.rotate(Vector3f.YP.rotationDegrees(15.0F * swing_rot));
                ms.rotate(Vector3f.XP.rotationDegrees(-35.0F * swing_rot));
                ms.rotate(Vector3f.XP.rotationDegrees(30.0F * swing));
            }
        } else {
            swingProgress = (float) ((double) swingProgress * 1.5D);
            if (swingProgress > 1.0F) {
                swingProgress = 0.0F;
            }
        }

        if (p.getMotion().length() >= 0.08D) {
            crawlCount = (float) ((double) crawlCount + 0.1D * p.getMotion().length() * 2.0D * tt);
            directionalCrawlCount = (float) ((double) directionalCrawlCount + 0.1D * dotProduct * 4.0D * tt);
            directionalCrawlCount = (float) ((double) directionalCrawlCount + (dotProduct > 0.0D
                    ? 0.1D * Math.abs(crossProduct) * 4.0D * tt
                    : 0.1D * Math.abs(crossProduct) * -1.0D * 4.0D * tt));
        }

        if (p.getMotion().y > 0.0D) {
            climbCount = (float) ((double) climbCount + 0.1D * tt);
        }
        if (p.getMotion().y < 0.0D) {
            climbCount = (float) ((double) climbCount - 0.1D * tt);
        }

        boolean climbAnim = HoldMyItemsClientConfig.ENABLE_CLIMB_AND_CRAWL.get();
        if (((crawling && climbAnim) || (climbing && !p.isOnGround() && Math.abs(p.getMotion().y) > 0.0D && climbAnim))
                && !p.isHandActive() && swingProgress == 0.0F) {
            clCount = (float) ((double) clCount + 0.1D * tt);
            if (clCount > 1.0F) {
                clCount = 1.0F;
            }
            if (!lantern) {
                ms.rotate(Vector3f.XP.rotationDegrees(-20.0F * clCount));
            }
        } else {
            clCount = (float) ((double) clCount * Math.pow(0.88D, tt));
        }

        if (swingProgress == 0.0F) {
            ms.translate(bl ? p.rotationPitch / 650.0F * clCount * -1.0F : p.rotationPitch / 650.0F * clCount, 0.0D, 0.0D);
            ms.rotate(Vector3f.XP.rotationDegrees(p.rotationPitch * clCount));
        }

        if (!lantern) {
            ms.translate(0.0D, 0.0D, p.rotationPitch / 120.0F * clCount);
        } else if (swingProgress == 0.0F) {
            ms.translate(0.0D, 0.0D, p.rotationPitch / 80.0F * clCount);
        }

        if (climbing && climbAnim && !p.isOnGround() && !lantern && !p.isHandActive()) {
            ms.translate(0.0D, 0.1D, -0.2D);
        }

        boolean underwater = p.areEyesInFluid(FluidTags.WATER);
        if (p.isInWater() && !swimming && !underwater) {
            inWaterCounter = (float) ((double) inWaterCounter + 0.1D * tt);
            if (inWaterCounter >= 1.0F) {
                inWaterCounter = 1.0F;
            }
        } else {
            inWaterCounter = (float) ((double) inWaterCounter * Math.pow(0.88D, tt));
        }

        ms.translate(0.0D, 0.02D * (double) inWaterCounter, 0.0D);
        ms.rotate(Vector3f.ZP.rotationDegrees(8.0F * kj * inWaterCounter));

        vertAngleY = (float) ((double) vertAngleY + p.getMotion().y * 0.015D * tt);
        vertAngleY = (float) ((double) vertAngleY - (double) (0.1F * vertAngleY) * tt);
        vertAngleY = (float) ((double) vertAngleY * Math.pow(0.88D, tt));
        vertVelocityYSlime = (float) ((double) vertVelocityYSlime + p.getMotion().y * 0.015D * tt);
        vertVelocityYSlime = (float) ((double) vertVelocityYSlime - (double) (0.1F * vertAngleYSlime) * tt);
        vertVelocityYSlime = (float) ((double) vertVelocityYSlime * Math.pow(0.88D, tt));
        vertAngleYSlime = (float) ((double) vertAngleYSlime + (double) vertVelocityYSlime * tt);
        ms.translate(0.0D, vertAngleY * -1.0F, 0.0D);
        ms.translate(0.0D, Math.sin((double) p.ticksExisted * 0.1D) * 0.007D * (double) kj, 0.0D);
        ms.rotate(Vector3f.YP.rotationDegrees(0.15F * MathHelper.sin((float) p.ticksExisted * 0.15F) * kj));

        if (!stack.isEmpty() || crawling || (climbing && !p.isOnGround()) || swimming) {
            if (p.getPrimaryHand() == HandSide.LEFT) {
                bl = !bl;
            }

            if (useAction == UseAction.BLOCK) {
                ms.translate(0.0D, 0.0D, 0.0D);
            } else {
                ms.translate(0.0D, -0.1D, 0.1D);
            }
        }

        if (lantern) {
            ms.translate(0.0D, 0.1D, 0.0D);
            if (swimming) {
                ms.translate(0.0D, -0.1D, 0.1D);
            }
        }

        if (swimming && swingProgress == 0.0F && HoldMyItemsClientConfig.ENABLE_SWIMMING_ANIM.get()) {
            double s = (double) ((float) p.ticksExisted + partialTicks) * 0.1D;
            double swingAmplitude = 1.5D;
            double frequency = 2.0D;
            s *= frequency;
            double handRotation = Math.sin(s) * swingAmplitude;
            double smoothRotation = handRotation * 0.8D + previousRotation * 0.2D;
            ms.rotate(Vector3f.YP.rotationDegrees((float) (bl ? smoothRotation : -smoothRotation)));
            ms.translate(0.0D, 0.0D, smoothRotation * 0.2D);
            double k = (double) ((float) p.ticksExisted + partialTicks) * 0.2D;
            double a = Math.cos(k);
            double b = a;
            if (a <= 0.0D) {
                b = a * 0.5D;
            }

            ms.rotate(Vector3f.YN.rotationDegrees((float) (bl ? b * 30.0D : b * 30.0D * -1.0D)));
            ms.translate(0.0D, 0.0D, a * 0.2D);
            if (stack.isEmpty() && !bl && !p.isInvisible()) {
                float j1 = bl ? 1.0F : -1.0F;
                ms.translate((double) j1, 0.0D - (double) equipProgress * 0.3D, 0.3D);
                ms.rotate(Vector3f.YP.rotationDegrees(45.0F * j1));
                ms.rotate(Vector3f.ZP.rotationDegrees(-40.0F * j1));
                ms.rotate(Vector3f.XP.rotationDegrees(30.0F));
                altSwing(ms, arm, swingProgress);
                ms.scale(0.9F, 0.9F, 0.9F);
                renderPlayerArm(mc, p, ms, buffer, light, 0.0F, 0.0F, arm);
            }

            previousRotation = smoothRotation;
        }

        if (((climbing && !p.isOnGround()) || (crawling && swingProgress == 0.0F)) && !p.isHandActive()) {
            double s = (double) ((float) p.ticksExisted + partialTicks) * 0.1D;
            float h = MathHelper.cos((float) s * 2.0F);
            float j = bl ? 1.0F : -1.0F;
            if (climbing) {
                if (!lantern) {
                    ms.rotate(Vector3f.XP.rotationDegrees(20.0F * h * j));
                } else {
                    ms.rotate(Vector3f.XP.rotationDegrees(1.0F * h * j));
                }
            }

            if (crawling && !p.isHandActive() && swingProgress == 0.0F) {
                float timeValue = ((float) p.ticksExisted + partialTicks) * 0.4F;
                float l = MathHelper.sin(timeValue * mouseHolding);
                float dt = MathHelper.cos(timeValue * mouseHolding);
                if (lantern) {
                    l *= 0.14F;
                    dt *= 0.14F;
                }

                ms.translate(0.2D * (double) l, 0.3D * (double) l * (double) j, -0.2D * (double) l * (double) j * (double) al);
                ms.rotate(Vector3f.YP.rotationDegrees(25.0F * l));
                ms.rotate(Vector3f.XP.rotationDegrees(MathHelper.clamp(20.0F * dt * j, 0.0F, 20.0F)));
            }

            if (stack.isEmpty() && !bl && !p.isInvisible() && ((!p.isOnGround() && climbing) || crawling)) {
                float l = bl ? 1.0F : -1.0F;
                ms.translate((double) l, 0.0D - (double) equipProgress * 0.3D, 0.3D);
                ms.rotate(Vector3f.YP.rotationDegrees(45.0F * l));
                ms.rotate(Vector3f.ZP.rotationDegrees(-40.0F * l));
                ms.rotate(Vector3f.XP.rotationDegrees(30.0F));
                altSwing(ms, arm, swingProgress);
                ms.scale(0.9F, 0.9F, 0.9F);
                renderPlayerArm(mc, p, ms, buffer, light, 0.0F, 0.0F, arm);
            }
        }

        if (stack.isEmpty()) {
            if (bl && !p.isInvisible()) {
                float ll = bl ? 1.0F : -1.0F;
                if ((p.isOnGround() || !climbing) && !swimming && !crawling) {
                    if (p.getPrimaryHand() == HandSide.LEFT) {
                        bl = !bl;
                    }

                    ms.translate(0.0D, 0.2D * (double) swing_rot, 0.15D * (double) swing_rot);
                    ms.translate(0.1D * (double) ll * (double) swing, 0.15D * (double) swing, -0.45D * (double) swing);
                    ms.rotate(Vector3f.YP.rotationDegrees(35.0F * swing * ll));
                    ms.rotate(Vector3f.XP.rotationDegrees(-30.0F * swing));
                    ms.rotate(Vector3f.YP.rotationDegrees(-10.0F * swing_rot * ll));
                    ms.rotate(Vector3f.XP.rotationDegrees(10.0F * swing_rot));
                    renderPlayerArm(mc, p, ms, buffer, light, 0.0F, 0.0F, arm);
                } else {
                    ms.translate((double) ll, 0.0D - (double) equipProgress * 0.3D, 0.3D);
                    ms.rotate(Vector3f.YP.rotationDegrees(45.0F * ll));
                    ms.rotate(Vector3f.ZP.rotationDegrees(-40.0F * ll));
                    ms.rotate(Vector3f.XP.rotationDegrees(30.0F));
                    altSwing(ms, arm, swingProgress);
                    ms.scale(0.9F, 0.9F, 0.9F);
                    renderPlayerArm(mc, p, ms, buffer, light, 0.0F, 0.0F, arm);
                }
            }
        } else {
            boolean blockRendered = false;
            boolean bl2 = arm == HandSide.RIGHT;
            int l = bl2 ? 1 : -1;
            int remaining = p.getItemInUseCount();

            if (p.isHandActive() && remaining > 0 && p.getActiveHand() == hand) {
                float useTime = (float) stack.getUseDuration() - ((float) remaining - partialTicks + 1.0F);
                switch (useAction) {
                    case NONE: {
                        transformSide(ms, arm, equipProgress);
                        break;
                    }
                    case EAT:
                    case DRINK: {
                        float pitchDelta = useTime / 5.0F;
                        if (pitchDelta > 1.0F) {
                            pitchDelta = 1.0F;
                        }

                        float k = MathHelper.sin(useTime / 2.0F * 3.14F);
                        k /= 10.0F;
                        ms.translate((double) l, 0.1D, 0.3D);
                        ms.translate(0.2D * (double) l * (double) pitchDelta, -0.7D * (double) pitchDelta, -0.2D * (double) pitchDelta);
                        ms.translate(0.0D, -0.2D * (double) k, -0.2D * (double) k);
                        ms.translate(0.0D, 0.1D * (double) easeInOutBack(MathHelper.sin(pitchDelta * 3.14F)), 0.0D);
                        ms.rotate(Vector3f.YP.rotationDegrees((float) (45 * l)));
                        ms.rotate(Vector3f.ZP.rotationDegrees((float) (-40 * l)));
                        ms.rotate(Vector3f.XP.rotationDegrees(30.0F));
                        altSwing(ms, arm, swingProgress);
                        ms.scale(0.9F, 0.9F, 0.9F);
                        ms.rotate(Vector3f.YP.rotationDegrees(45.0F * pitchDelta * (float) l));
                        renderPlayerArm(mc, p, ms, buffer, light, 0.0F, swingProgress, arm);
                        break;
                    }
                    case BLOCK: {
                        double s = (double) (useTime / 4.0F);
                        float s2 = useTime / 6.0F;
                        if (s > 1.0D) {
                            s = 1.0D;
                        }
                        if (s2 > 1.0F) {
                            s2 = 1.0F;
                        }

                        ms.translate(0.0D, -0.2D, 0.0D);
                        ms.translate((double) l, 0.0D, 0.3D);
                        ms.translate(0.7D * s * (double) l, 0.0D, -1.3D * s);
                        ms.translate(-0.2D * (double) l * (double) s2, 0.0D, 0.0D);
                        ms.rotate(Vector3f.XP.rotationDegrees((float) (10.0D * Math.sin((double) s2 * 3.14D))));
                        ms.rotate(Vector3f.YP.rotationDegrees((float) (70.0D * s * (double) ((float) l))));
                        ms.rotate(Vector3f.YP.rotationDegrees((float) (45 * l)));
                        ms.rotate(Vector3f.ZP.rotationDegrees((float) (-40 * l)));
                        ms.rotate(Vector3f.XP.rotationDegrees(30.0F));
                        ms.rotate(Vector3f.YP.rotationDegrees((float) ((double) ((float) (5 * l)) * s)));
                        ms.rotate(Vector3f.XP.rotationDegrees((float) (-10.0D * s)));
                        ms.translate(0.0D, 0.0D, -0.2D * s);
                        altSwing(ms, arm, swingProgress);
                        ms.scale(0.9F, 0.9F, 0.9F);
                        renderPlayerArm(mc, p, ms, buffer, light, 0.0F, swingProgress, arm);
                        ms.translate(0.35D * (double) l, -0.13D, -0.12D);
                        ms.rotate(Vector3f.ZP.rotationDegrees(10.0F * (float) l));
                        ms.rotate(Vector3f.YP.rotationDegrees(10.0F * (float) l));
                        ms.rotate(Vector3f.XP.rotationDegrees(0.0F));
                        ms.translate(-0.2D * (double) l, -0.04D, 0.15D);
                        ms.scale(1.0F, 1.0F, 1.0F);
                        break;
                    }
                    case BOW: {
                        ms.push();
                        if (p.getPrimaryHand() == HandSide.LEFT) {
                            bl = !bl;
                        }

                        float m1 = useTime;
                        float f1 = m1 / 20.0F;
                        float f = (f1 * f1 + f1 * 2.0F) / 3.0F;
                        if (f1 > 1.0F) {
                            f1 = 1.0F;
                        }

                        if (f1 > 0.1F) {
                            float g1 = MathHelper.sin((m1 - 0.1F) * 1.3F);
                            float j1 = g1 * f1;
                            ms.translate((double) (j1 * 0.0F), (double) (j1 * 0.004F), (double) (j1 * 0.0F));
                        }

                        ms.translate(bl ? -0.1D : 0.1D, 0.0D, (double) f1 * 0.15D);
                        renderPlayerArm(mc, p, ms, buffer, light, equipProgress, swingProgress, arm);
                        ms.pop();
                        ms.translate(bl ? -0.5D : 0.5D, -0.45D, 0.1D);
                        ms.rotate(Vector3f.XP.rotation(0.3F));
                        if (bl) {
                            ms.rotate(Vector3f.ZN.rotation(-0.3F));
                            ms.rotate(Vector3f.YN.rotation(1.0F));
                        } else {
                            ms.rotate(Vector3f.ZP.rotation(-0.3F));
                            ms.rotate(Vector3f.YP.rotation(1.0F));
                        }

                        renderPlayerArm(mc, p, ms, buffer, light, equipProgress, swingProgress, arm.opposite());
                        if (bl) {
                            ms.rotate(Vector3f.YN.rotation(2.5F));
                        } else {
                            ms.rotate(Vector3f.YP.rotation(2.5F));
                        }

                        ms.translate(bl ? -0.65D : 0.65D, -0.35D, 0.27D);
                        if (f1 > 1.0F) {
                            f1 = 1.0F;
                        }

                        ms.pop();
                        if (HoldMyItemsClientConfig.MB3D_COMPAT.get()) {
                            ms.rotate(Vector3f.YP.rotationDegrees((float) (10 * l)));
                        }

                        ms.rotate(Vector3f.XN.rotationDegrees(75.0F));
                        ms.rotate(Vector3f.ZN.rotationDegrees((float) (-15 * l)));
                        ms.translate(0.8D * (double) l, (double) (0.0F - equipProgress * 0.3F), -0.1D);
                        if (f > 0.1F) {
                            float g1 = MathHelper.sin((m1 - 0.1F) * 1.3F);
                            float h1 = f1 - 0.1F;
                            float j1 = g1 * h1;
                            ms.translate((double) (j1 * 0.0F), (double) (j1 * 0.004F), (double) (j1 * 0.0F));
                        }

                        ms.push();
                        break;
                    }
                    case SPEAR: {
                        if (p.getHeldItemOffhand().isEmpty() && !crawling && !swimming && !climbing) {
                            ms.push();
                            ms.rotate(Vector3f.YP.rotationDegrees((float) (-25 * l)));
                            ms.translate(-0.15D * (double) l, 0.1D, 0.1D);
                            renderPlayerArm(mc, p, ms, buffer, light, equipProgress, swingProgress, arm.opposite());
                            ms.pop();
                        }

                        float f = useTime / 10.0F;
                        if (f > 1.0F) {
                            f = 1.0F;
                        }

                        if (f > 0.1F) {
                            float g = MathHelper.sin((useTime - 0.1F) * 1.3F);
                            float h = f - 0.1F;
                            float j = g * h;
                            ms.translate((double) (j * 0.0F), (double) (j * 0.004F), (double) (j * 0.0F));
                        }

                        ms.rotate(Vector3f.XP.rotationDegrees(45.0F));
                        ms.rotate(Vector3f.YP.rotationDegrees((float) (25 * l)));
                        ms.translate(0.2D * (double) l, 0.0D, 0.8D);
                        renderPlayerArm(mc, p, ms, buffer, light, equipProgress, swingProgress, arm);
                        ms.rotate(Vector3f.XP.rotationDegrees(135.0F));
                        ms.rotate(Vector3f.ZP.rotationDegrees((float) (-65 * l)));
                        ms.translate((double) (0.65F * (float) l), -1.0D, -0.6D);
                        break;
                    }
                    default:
                        break;
                }
            } else if (p.isSpinAttacking() && useAction == UseAction.SPEAR) {
                riptideCounter = (float) ((double) riptideCounter + 0.15D * tt);
                float dt = (float) stack.getUseDuration() - ((float) remaining - partialTicks + 1.0F);
                float f = dt / 10.0F;
                if (f > 1.0F) {
                    f = 1.0F;
                }

                if (f > 0.1F) {
                    float g = MathHelper.sin((dt - 0.1F) * 1.3F);
                    float h = f - 0.1F;
                    float j = g * h;
                    ms.translate((double) (j * 0.0F), (double) (j * 0.004F), (double) (j * 0.0F));
                }

                ms.rotate(Vector3f.XP.rotationDegrees(45.0F - riptideCounter * 2.0F));
                ms.rotate(Vector3f.YP.rotationDegrees((float) (25 * l)));
                ms.translate(0.2D * (double) l, 0.0D, 0.75D);
                ms.translate(0.0D, 0.0D, 0.01D * (double) MathHelper.sin(riptideCounter * 6.28F));
                renderPlayerArm(mc, p, ms, buffer, light, equipProgress, swingProgress, arm);
                ms.rotate(Vector3f.XP.rotationDegrees(135.0F));
                ms.rotate(Vector3f.ZP.rotationDegrees((float) (-65 * l)));
                ms.translate((double) (0.65F * (float) l), -1.0D, -0.6D);
            } else {
                riptideCounter = 0.0F;
                if (!lantern) {
                    if (useAction == UseAction.BLOCK) {
                        ms.translate(0.0D, -0.2D, 0.0D);
                    }
                } else {
                    ms.translate(0.1D * (double) l, 0.0D, -0.1D);
                    ms.rotate(Vector3f.XP.rotationDegrees(10.0F));
                }

                ms.translate((double) l, 0.0D - (double) equipProgress * 0.3D, 0.3D);
                ms.rotate(Vector3f.YP.rotationDegrees((float) (45 * l)));
                ms.rotate(Vector3f.ZP.rotationDegrees((float) (-40 * l)));
                ms.rotate(Vector3f.XP.rotationDegrees(30.0F));
                altSwing(ms, arm, swingProgress);
                ms.scale(0.9F, 0.9F, 0.9F);
                renderPlayerArm(mc, p, ms, buffer, light, 0.0F, 0.0F, arm);
            }

            ms.translate(-0.3D * (double) l, 0.65D, -0.1D);
            ms.rotate(Vector3f.YP.rotationDegrees((float) (-65 * l)));
            ms.rotate(Vector3f.XP.rotationDegrees(10.0F));
            if (blk instanceof CarpetBlock) {
                ms.translate(0.2D * (double) l, -0.1D, 0.0D);
            }

            boolean leaves = defState.isIn(BlockTags.LEAVES);
            boolean banner = defState.isIn(BlockTags.BANNERS);
            boolean combo = blk instanceof CarpetBlock;

            if (hasBlock && useAction != UseAction.EAT && !bucket) {
                String path = item.getRegistryName() == null ? "" : item.getRegistryName().getPath();
                if (path.contains("torch")) {
                    ms.scale(1.5F, 1.5F, 1.5F);
                    ms.rotate(Vector3f.YN.rotationDegrees((float) (25 * l)));
                    ms.rotate(Vector3f.XP.rotationDegrees(5.0F));
                    ms.rotate(Vector3f.ZP.rotationDegrees((float) (75 * l)));
                    ms.translate(0.2D * (double) l, 0.2D, 0.05D);
                } else if ((item == Items.STRING || item == Items.REDSTONE || item == Items.LEVER || item == Items.TRIPWIRE_HOOK
                        || blk instanceof PaneBlock || defState.isIn(BlockTags.RAILS) || defState.isIn(BlockTags.CLIMBABLE)
                        || blk instanceof DoorBlock) && !leaves && !combo && !banner) {
                    ms.translate(0.0D, 0.0D, -0.1D);
                    ms.rotate(Vector3f.YN.rotationDegrees((float) (5 * l)));
                    ms.rotate(Vector3f.XP.rotationDegrees(15.0F));
                    ms.rotate(Vector3f.ZP.rotationDegrees((float) (75 * l)));
                } else if (!lantern) {
                    ms.rotate(Vector3f.YN.rotationDegrees((float) (25 * l)));
                    ms.rotate(Vector3f.XP.rotationDegrees(5.0F));
                    ms.rotate(Vector3f.ZP.rotationDegrees((float) (75 * l)));
                    ms.translate(0.2D * (double) l, 0.2D, 0.05D);
                    if (banner) {
                        ms.translate(-0.2D * (double) l, 0.0D, 0.0D);
                        ms.scale(1.1F, 1.1F, 1.1F);
                    }
                } else {
                    float dt = (float) (HoldMyItems.deltaTime * HoldMyItemsClientConfig.ANIMATION_SPEED.get());
                    float yawDelta = p.prevRotationYawHead - p.rotationYawHead;
                    float pitchDelta = p.prevRotationPitch - p.rotationPitch;
                    swingVelocityY += yawDelta * 0.015F * dt;
                    swingVelocityY += swingProgress * 2.0F * dt;
                    swingVelocityX += pitchDelta * 0.015F * dt;
                    swingVelocityY -= 0.1F * swingAngleY * dt;
                    swingVelocityX -= 0.1F * swingAngleX * dt;
                    swingVelocityY = (float) ((double) swingVelocityY * Math.pow(0.88D, (double) dt));
                    swingVelocityX = (float) ((double) swingVelocityX * Math.pow(0.88D, (double) dt));
                    swingAngleY += swingVelocityY * dt;
                    swingAngleX += swingVelocityX * dt;
                    double currentSpeed = p.getMotion().length();
                    swingVelocityZ = (float) ((double) swingVelocityZ + (bl
                            ? (currentSpeed * -1.0D * 15.0D - (double) swingVelocityZ) * 0.1D * (double) dt
                            : (currentSpeed * 15.0D - (double) swingVelocityZ) * 0.1D * (double) dt));
                    if ((currentSpeed > 0.09D && p.isOnGround() || swimming || (climbing && !p.isOnGround())) && mc.gameSettings.viewBobbing) {
                        Random random = new Random();
                        boolean randomBoolean = random.nextBoolean();
                        swingVelocityY += (float) (randomBoolean ? -5.5D * currentSpeed * (double) dt : 5.5D * currentSpeed * (double) dt);
                    }

                    ms.translate(0.0D, 0.0D, -0.1D);
                    ms.rotate(Vector3f.YN.rotationDegrees((float) (35 * l) + swingAngleY));
                    ms.rotate(Vector3f.XP.rotationDegrees(15.0F + swingAngleX));
                    ms.rotate(Vector3f.ZP.rotationDegrees((float) (75 * l) + swingVelocityZ));
                    ms.translate(0.3D * (double) l, -0.35D, 0.0D);
                    ms.translate(0.0D, 0.0D, 0.1D);
                    ms.scale(1.5F, 1.5F, 1.5F);
                }
            } else {
                if (smallItem && !hoe && !HoldMyItemsClientConfig.MB3D_COMPAT.get()) {
                    ms.rotate(Vector3f.YN.rotationDegrees((float) (5 * l)));
                    ms.rotate(Vector3f.XP.rotationDegrees(15.0F));
                    ms.rotate(Vector3f.ZP.rotationDegrees((float) (75 * l)));
                    ms.translate(0.0D, -0.05D, -0.1D);
                    ms.scale(0.7F, 0.7F, 0.7F);

                    if (item == Items.FEATHER || item == Items.SLIME_BALL || item == Items.PUFFERFISH) {
                        vertVelocityYSlime = (float) ((double) vertVelocityYSlime + (double) swingProgress * 0.03D * HoldMyItems.deltaTime * HoldMyItemsClientConfig.ANIMATION_SPEED.get());
                        if ((p.getMotion().length() > 0.09D && p.isOnGround() || swimming || crawling || (climbing && !p.isOnGround())) && mc.gameSettings.viewBobbing) {
                            vertVelocityYSlime += (float) (-0.05D * p.getMotion().length() * HoldMyItems.deltaTime * HoldMyItemsClientConfig.ANIMATION_SPEED.get());
                        }

                        ms.scale(1.0F, 1.0F + vertAngleYSlime * -2.0F, 1.0F);
                    }
                } else if (useAction == UseAction.BLOCK) {
                    ms.rotate(Vector3f.ZP.rotationDegrees((float) (160 * l)));
                    ms.rotate(Vector3f.YP.rotationDegrees((float) (-60 * l)));
                    ms.rotate(Vector3f.XP.rotationDegrees(-70.0F));
                    ms.scale(0.75F, 0.75F, 0.75F);
                    ms.translate(0.15D * (double) l, bl ? 0.35D : 0.45D, bl ? -0.15D : -0.1D);
                    ms.translate(0.17D * (double) l, 0.0D, 0.3D);
                    ms.rotate(Vector3f.YP.rotationDegrees((float) (-90 * l)));
                } else if (useAction == UseAction.SPEAR) {
                    ms.rotate(Vector3f.YN.rotationDegrees((float) (75 * l)));
                    ms.rotate(Vector3f.XP.rotationDegrees(90.0F));
                    ms.rotate(Vector3f.ZP.rotationDegrees((float) (45 * l)));
                    ms.translate(-0.3F * (float) l, 0.0D, 0.0D);
                } else {
                    ms.rotate(Vector3f.YN.rotationDegrees((float) (75 * l)));
                    ms.rotate(Vector3f.XP.rotationDegrees(70.0F));
                    ms.rotate(Vector3f.ZP.rotationDegrees((float) (45 * l)));
                }

                if (useAction != UseAction.BLOCK) {
                    ms.scale(1.2F, 1.2F, 1.2F);
                }

                if (useAction == UseAction.BOW && !p.isHandActive()) {
                    ms.translate(-0.1D * (double) l, -0.2D, 0.0D);
                }
            }

            if (item instanceof BlockItem) {
                BlockItem blockItem = (BlockItem) item;
                boolean thin = item == Items.STRING || item == Items.REDSTONE || item == Items.LEVER || item == Items.TRIPWIRE_HOOK
                        || blk instanceof PaneBlock || defState.isIn(BlockTags.RAILS) || defState.isIn(BlockTags.CLIMBABLE)
                        || blk instanceof DoorBlock;
                if (((!bucket && useAction != UseAction.EAT && !banner && !thin) || leaves) && !combo) {
                    ms.push();
                    if (!bl2) {
                        ms.translate(-0.4D, 0.0D, 0.0D);
                    }

                    ms.scale(0.4F, 0.4F, 0.4F);
                    ms.translate(-0.9D * (double) l, -0.45D, -0.5D);
                    if (defState.isIn(BlockTags.BUTTONS)) {
                        ms.translate(0.2D * (double) l, -0.15D, -0.2D);
                    }

                    if (blk instanceof AbstractPressurePlateBlock) {
                        ms.translate(0.0D, 0.1D, 0.0D);
                    }

                    if (item == Items.SLIME_BLOCK || item == Items.HONEY_BLOCK || defState.isIn(BlockTags.FLOWERS)
                            || leaves || defState.isIn(BlockTags.SAPLINGS)) {
                        vertVelocityYSlime = (float) ((double) vertVelocityYSlime + (double) swingProgress * 0.03D * HoldMyItems.deltaTime * HoldMyItemsClientConfig.ANIMATION_SPEED.get());
                        if ((p.getMotion().length() > 0.09D && p.isOnGround() || swimming || crawling || (climbing && !p.isOnGround())) && mc.gameSettings.viewBobbing) {
                            vertVelocityYSlime += (float) (-0.05D * p.getMotion().length() * HoldMyItems.deltaTime * HoldMyItemsClientConfig.ANIMATION_SPEED.get());
                        }

                        ms.scale(1.0F, 1.0F + vertAngleYSlime * -2.0F, 1.0F);
                    }

                    BlockState blockState = blockItem.getBlock().getDefaultState();
                    if ((float) p.ticksExisted - prevAge >= 100.0F) {
                        repPower = !repPower;
                        prevAge = (float) p.ticksExisted;
                    }

                    Block b = blockItem.getBlock();
                    if ((b == Blocks.REPEATER || b == Blocks.COMPARATOR) && repPower) {
                        blockState = blockState.with(BlockStateProperties.POWERED, true);
                    }

                    if (b == Blocks.REDSTONE_TORCH && underwater) {
                        blockState = blockState.with(BlockStateProperties.LIT, false);
                    }

                    if ((b == Blocks.CAMPFIRE || b == Blocks.SOUL_CAMPFIRE) && underwater) {
                        blockState = blockState.with(BlockStateProperties.LIT, false);
                    }

                    if (b instanceof BedBlock) {
                        if (bl) {
                            ms.translate(0.9D, 0.0D, 0.8D);
                        }

                        ms.rotate(Vector3f.YP.rotationDegrees((float) (90 * l)));
                    }

                    mc.getBlockRendererDispatcher().renderBlock(blockState, ms, buffer, light, OverlayTexture.NO_OVERLAY, EmptyModelData.INSTANCE);
                    ms.pop();
                    blockRendered = true;
                }
            }

            if (!blockRendered) {
                if (toolLike || useAction == UseAction.BOW || attackDamage != 0.0F || useAction == UseAction.BLOCK
                        || sticks || fishing || shears) {
                    if (sword) {
                        ms.rotate(Vector3f.XP.rotationDegrees(-60.0F * swing));
                        ms.translate(0.0D, 0.1D * (double) swing, -0.1D * (double) swing);
                    }

                    if (shovel) {
                        ms.rotate(Vector3f.XP.rotationDegrees(-80.0F * swing_rot));
                        ms.rotate(Vector3f.XP.rotationDegrees(30.0F * swing));
                    } else if (useAction == UseAction.SPEAR) {
                        ms.rotate(Vector3f.XP.rotationDegrees(-40.0F * swing_rot));
                        ms.translate(0.0D, 0.1D * (double) swing_rot, -0.1D * (double) swing_rot);
                    } else if (useAction != UseAction.BLOCK) {
                        ms.rotate(Vector3f.XP.rotationDegrees(-25.0F * swing));
                        ms.translate(0.0D, 0.05D * (double) swing, -0.05D * (double) swing);
                    }
                }

                if (item == Items.NETHER_STAR || (item == Items.END_CRYSTAL && HoldMyItemsClientConfig.MB3D_COMPAT.get())) {
                    netherCounter = (float) ((double) netherCounter + 0.9D * tt);
                    ms.translate(0.0D, 0.25D + 0.02D * (double) MathHelper.sin(netherCounter * 0.1F), 0.0D);
                    ms.rotate(Vector3f.XP.rotationDegrees(3.0F * MathHelper.sin(netherCounter * 0.2F)));
                    ms.scale(1.0F + 0.01F * MathHelper.sin(netherCounter), 1.0F + 0.01F * MathHelper.sin(netherCounter), 1.0F + 0.01F * MathHelper.sin(netherCounter));
                } else {
                    netherCounter = 0.0F;
                }

                if (HoldMyItemsClientConfig.MB3D_COMPAT.get()) {
                    if (sword) {
                        ms.translate(0.0D, 0.2D, 0.0D);
                    }

                    if (item == Items.FEATHER || item == Items.SLIME_BALL || item == Items.PUFFERFISH) {
                        vertVelocityYSlime = (float) ((double) vertVelocityYSlime + (double) swingProgress * 0.03D * HoldMyItems.deltaTime * HoldMyItemsClientConfig.ANIMATION_SPEED.get());
                        if ((p.getMotion().length() > 0.09D && p.isOnGround() || swimming || crawling || (climbing && !p.isOnGround())) && mc.gameSettings.viewBobbing) {
                            vertVelocityYSlime += (float) (-0.05D * p.getMotion().length() * HoldMyItems.deltaTime * HoldMyItemsClientConfig.ANIMATION_SPEED.get());
                        }

                        ms.scale(1.0F, 1.0F + vertAngleYSlime * -2.0F, 1.0F);
                    }
                }

                if (shovel) {
                    ms.translate(0.07D * (double) l, 0.0D, 0.05D);
                    ms.rotate(Vector3f.YP.rotationDegrees((float) (90 * l)));
                    ms.rotate(Vector3f.XP.rotationDegrees(-15.0F));
                }

                mc.getFirstPersonRenderer().renderItemSide(p, stack,
                        bl2 ? ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND : ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND,
                        !bl2, ms, buffer, light);
            }
        }

        ms.pop();
        ms.pop();
        isAttacking = mc.gameSettings.keyBindAttack.isKeyDown();
        return true;
    }
}
