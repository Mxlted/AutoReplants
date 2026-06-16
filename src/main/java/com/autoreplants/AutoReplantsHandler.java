package com.autoreplants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

public class AutoReplantsHandler {
    private static final int HOTBAR_SIZE = 9;
    private static final int REPLANT_DELAY_TICKS = 2;

    private static final Map<Block, Item> CROP_TO_SEED = Map.of(
        Blocks.WHEAT, Items.WHEAT_SEEDS,
        Blocks.CARROTS, Items.CARROT,
        Blocks.POTATOES, Items.POTATO,
        Blocks.BEETROOTS, Items.BEETROOT_SEEDS,
        Blocks.NETHER_WART, Items.NETHER_WART,
        Blocks.TORCHFLOWER_CROP, Items.TORCHFLOWER_SEEDS,
        Blocks.PITCHER_CROP, Items.PITCHER_POD
    );

    private static final Deque<PendingReplant> pendingReplants = new ArrayDeque<>();

    private static final class PendingReplant {
        private final ResourceKey<Level> dimension;
        private final BlockPos supportPos;
        private final Item seedItem;
        private int seedSlot;
        private int ticksRemaining = REPLANT_DELAY_TICKS;

        private PendingReplant(ResourceKey<Level> dimension, BlockPos supportPos, Item seedItem, int seedSlot) {
            this.dimension = dimension;
            this.supportPos = supportPos;
            this.seedItem = seedItem;
            this.seedSlot = seedSlot;
        }

        private boolean waitAnotherTick() {
            if (ticksRemaining <= 0) {
                return false;
            }

            ticksRemaining--;
            return true;
        }

        private boolean matches(ResourceKey<Level> dimension, BlockPos supportPos) {
            return this.dimension.equals(dimension) && this.supportPos.equals(supportPos);
        }
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.gameMode == null || client.level == null) {
                pendingReplants.clear();
                return;
            }
            processPendingReplants(client);
        });

        ClientPlayerBlockBreakEvents.AFTER.register((world, player, pos, state) -> {
            Block block = state.getBlock();
            Item seedItem = CROP_TO_SEED.get(block);

            if (seedItem == null) {
                return;
            }

            if (!isHoldingHoe(player)) {
                return;
            }

            int hotbarSlot = findSeedInHotbar(player, seedItem);
            if (hotbarSlot == -1) {
                return;
            }

            ResourceKey<Level> dimension = world.dimension();
            BlockPos supportPos = getSupportPos(pos, state);
            pendingReplants.removeIf(replant -> replant.matches(dimension, supportPos));
            pendingReplants.addLast(new PendingReplant(dimension, supportPos, seedItem, hotbarSlot));
        });
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

        for (int i = 0; i < replantCount; i++) {
            PendingReplant replant = pendingReplants.removeFirst();
            if (!isSameDimension(mc, replant)) {
                continue;
            }

            if (replant.waitAnotherTick()) {
                pendingReplants.addLast(replant);
            } else {
                doReplant(mc, replant);
            }
        }
    }

    private static void doReplant(Minecraft mc, PendingReplant replant) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.level == null) {
            return;
        }

        if (!isSameDimension(mc, replant) || !canAttemptReplant(mc.level, replant)) {
            return;
        }

        int seedSlot = replant.seedSlot;
        if (!isSeedInSlot(player, seedSlot, replant.seedItem)) {
            seedSlot = findSeedInHotbar(player, replant.seedItem);
            if (seedSlot == -1) {
                return;
            }
            replant.seedSlot = seedSlot;
        }

        var inventory = player.getInventory();
        int previousSlot = inventory.getSelectedSlot();
        boolean changedSlot = previousSlot != seedSlot;

        BlockHitResult hitResult = new BlockHitResult(
            Vec3.atCenterOf(replant.supportPos).add(0, 0.5, 0),
            Direction.UP,
            replant.supportPos,
            false
        );

        try {
            if (changedSlot) {
                selectHotbarSlot(player, seedSlot);
            }
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
        } finally {
            if (changedSlot) {
                selectHotbarSlot(player, previousSlot);
            }
        }
    }

    private static boolean isSameDimension(Minecraft mc, PendingReplant replant) {
        return mc.level != null && mc.level.dimension().equals(replant.dimension);
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
