package com.autoreplants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

public class AutoReplantsHandler {
    private static final int HOTBAR_SIZE = 9;
    private static final int MAX_PENDING_REPLANTS = 64;
    private static final int MAX_REPLANT_AGE = 40;
    private static final int MAX_ATTEMPTS = 3;
    private static final int RETRY_DELAY_TICKS = 5;
    private static final int MAX_ATTEMPTS_PER_TICK = 4;

    private static final Map<Block, Item> CROP_TO_SEED = Map.of(
        Blocks.WHEAT, Items.WHEAT_SEEDS,
        Blocks.CARROTS, Items.CARROT,
        Blocks.POTATOES, Items.POTATO,
        Blocks.BEETROOTS, Items.BEETROOT_SEEDS,
        Blocks.NETHER_WART, Items.NETHER_WART,
        Blocks.TORCHFLOWER_CROP, Items.TORCHFLOWER_SEEDS,
        Blocks.TORCHFLOWER, Items.TORCHFLOWER_SEEDS,
        Blocks.PITCHER_CROP, Items.PITCHER_POD
    );

    private static final Deque<PendingReplant> pendingReplants = new ArrayDeque<>();

    private static final class PendingReplant {
        private final Level world;
        private final BlockPos supportPos;
        private final Item seedItem;
        private int seedSlot;
        private int ticksRemaining;
        private int age;
        private int attempts;

        private PendingReplant(Level world, BlockPos supportPos, Item seedItem, int seedSlot) {
            this.world = world;
            this.supportPos = supportPos;
            this.seedItem = seedItem;
            this.seedSlot = seedSlot;
            this.ticksRemaining = SettingsStore.get().replantDelay;
        }

        private boolean waitAnotherTick() {
            if (ticksRemaining <= 0) {
                return false;
            }

            ticksRemaining--;
            return ticksRemaining > 0;
        }

        private boolean matches(Level world, BlockPos supportPos) {
            return this.world == world && this.supportPos.equals(supportPos);
        }
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!canReplant(client)) {
                pendingReplants.clear();
                return;
            }
            processPendingReplants(client);
        });

        ClientPlayerBlockBreakEvents.AFTER.register((world, player, pos, state) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != player || client.level != world || !canReplant(client)) {
                return;
            }
            Block block = state.getBlock();
            Item seedItem = CROP_TO_SEED.get(block);

            if (seedItem == null) {
                return;
            }

            if (SettingsStore.get().matureOnly && !isMatureCrop(state)) {
                return;
            }

            int hotbarSlot = findSeedInHotbar(player, seedItem);
            if (hotbarSlot == -1) {
                return;
            }

            BlockPos supportPos = getSupportPos(pos, state);
            pendingReplants.removeIf(replant -> replant.world != world || replant.matches(world, supportPos));
            if (pendingReplants.size() >= MAX_PENDING_REPLANTS) {
                pendingReplants.removeFirst();
            }
            pendingReplants.addLast(new PendingReplant(world, supportPos, seedItem, hotbarSlot));
        });
    }

    private static boolean canReplant(Minecraft client) {
        Settings settings = SettingsStore.get();
        LocalPlayer player = client.player;
        return settings.enabled && player != null && client.level != null && client.gameMode != null
            && player.connection != null && player.isAlive() && !client.gameMode.isSpectator()
            && client.mouseHandler.isMouseGrabbed() && !player.isUsingItem() && player.containerMenu == player.inventoryMenu
            && (!settings.requireHoe || isHoldingHoe(player))
            && (!settings.sneakBypass || !player.isShiftKeyDown());
    }

    private static boolean isMatureCrop(BlockState state) {
        if (state.is(Blocks.TORCHFLOWER)) {
            return true;
        }
        if (state.is(Blocks.TORCHFLOWER_CROP)) {
            return false;
        }
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (state.is(Blocks.NETHER_WART)) {
            return state.getValue(NetherWartBlock.AGE) == NetherWartBlock.MAX_AGE;
        }
        return state.is(Blocks.PITCHER_CROP)
            && state.getValue(PitcherCropBlock.AGE) == PitcherCropBlock.MAX_AGE;
    }

    private static BlockPos getSupportPos(BlockPos cropPos, BlockState cropState) {
        BlockPos supportPos = cropPos.below();
        if (cropState.hasProperty(PitcherCropBlock.HALF)
            && cropState.getValue(PitcherCropBlock.HALF) == DoubleBlockHalf.UPPER) {
            supportPos = supportPos.below();
        }
        return supportPos.immutable();
    }

    private static void processPendingReplants(Minecraft mc) {
        int replantCount = pendingReplants.size();
        int attemptsThisTick = 0;

        for (int i = 0; i < replantCount; i++) {
            PendingReplant replant = pendingReplants.removeFirst();
            if (mc.level != replant.world || ++replant.age > MAX_REPLANT_AGE) {
                continue;
            }

            if (replant.waitAnotherTick() || attemptsThisTick >= MAX_ATTEMPTS_PER_TICK) {
                pendingReplants.addLast(replant);
            } else {
                attemptsThisTick++;
                if (doReplant(mc, replant) && ++replant.attempts < MAX_ATTEMPTS) {
                    replant.ticksRemaining = RETRY_DELAY_TICKS;
                    pendingReplants.addLast(replant);
                }
            }
        }
    }

    private static boolean doReplant(Minecraft mc, PendingReplant replant) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.level == null) {
            return false;
        }

        if (mc.level != replant.world || !canAttemptReplant(mc.level, replant)
            || !player.isWithinBlockInteractionRange(replant.supportPos, 0.0)) {
            return false;
        }

        double top = mc.level.getBlockState(replant.supportPos)
            .getCollisionShape(mc.level, replant.supportPos).max(Direction.Axis.Y);
        Vec3 hitPos = Vec3.atBottomCenterOf(replant.supportPos).add(0, top, 0);
        BlockHitResult visibleHit = mc.level.clip(new ClipContext(
            player.getEyePosition(), hitPos.add(0, -0.01, 0),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
        ));
        if (visibleHit.getType() != HitResult.Type.BLOCK
            || !visibleHit.getBlockPos().equals(replant.supportPos) || visibleHit.getDirection() != Direction.UP) {
            return false;
        }

        int seedSlot = replant.seedSlot;
        if (!isSeedInSlot(player, seedSlot, replant.seedItem)) {
            seedSlot = findSeedInHotbar(player, replant.seedItem);
            if (seedSlot == -1) {
                return true;
            }
            replant.seedSlot = seedSlot;
        }

        var inventory = player.getInventory();
        int previousSlot = inventory.getSelectedSlot();
        boolean changedSlot = previousSlot != seedSlot;

        BlockHitResult hitResult = new BlockHitResult(
            hitPos,
            Direction.UP,
            replant.supportPos,
            false
        );

        try {
            if (changedSlot) {
                selectHotbarSlot(player, seedSlot);
            }
            return !mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult).consumesAction();
        } finally {
            if (changedSlot) {
                selectHotbarSlot(player, previousSlot);
            }
        }
    }

    private static boolean canAttemptReplant(Level level, PendingReplant replant) {
        BlockState supportState = level.getBlockState(replant.supportPos);
        if (!isValidSupport(supportState, replant.seedItem)) {
            return false;
        }

        if (!level.getBlockState(replant.supportPos.above()).isAir()) {
            return false;
        }

        return replant.seedItem != Items.PITCHER_POD
            || level.getBlockState(replant.supportPos.above(2)).isAir();
    }

    private static boolean isValidSupport(BlockState supportState, Item seedItem) {
        if (seedItem == Items.NETHER_WART) {
            return supportState.is(Blocks.SOUL_SAND);
        }

        return supportState.is(Blocks.FARMLAND);
    }

    private static void selectHotbarSlot(LocalPlayer player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private static boolean isHoldingHoe(LocalPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        return isHoe(mainHand) || isHoe(offHand);
    }

    private static boolean isHoe(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.is(ItemTags.HOES);
    }

    private static int findSeedInHotbar(LocalPlayer player, Item seedItem) {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (isSeedInSlot(player, i, seedItem)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isSeedInSlot(LocalPlayer player, int slot, Item seedItem) {
        if (slot < 0 || slot >= HOTBAR_SIZE) {
            return false;
        }

        ItemStack stack = player.getInventory().getItem(slot);
        return !stack.isEmpty() && stack.getItem() == seedItem;
    }
}
