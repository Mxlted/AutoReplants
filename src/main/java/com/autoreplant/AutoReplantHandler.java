package com.autoreplant;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AutoReplantHandler {
    private static final Map<Block, Item> CROP_TO_SEED = new HashMap<>();
    private static final Set<Item> ALL_HOES = Set.of(
        Items.WOODEN_HOE,
        Items.STONE_HOE,
        Items.IRON_HOE,
        Items.GOLDEN_HOE,
        Items.DIAMOND_HOE,
        Items.NETHERITE_HOE
    );

    private static final int REPLANT_DELAY_TICKS = 2;
    private static final Queue<PendingReplant> pendingReplants = new ConcurrentLinkedQueue<>();

    private record PendingReplant(BlockPos pos, int seedSlot, int ticksRemaining) {}

    static {
        CROP_TO_SEED.put(Blocks.WHEAT, Items.WHEAT_SEEDS);
        CROP_TO_SEED.put(Blocks.CARROTS, Items.CARROT);
        CROP_TO_SEED.put(Blocks.POTATOES, Items.POTATO);
        CROP_TO_SEED.put(Blocks.BEETROOTS, Items.BEETROOT_SEEDS);
        CROP_TO_SEED.put(Blocks.NETHER_WART, Items.NETHER_WART);
        CROP_TO_SEED.put(Blocks.TORCHFLOWER_CROP, Items.TORCHFLOWER_SEEDS);
        CROP_TO_SEED.put(Blocks.PITCHER_CROP, Items.PITCHER_POD);
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.gameMode == null) {
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

            pendingReplants.add(new PendingReplant(pos.immutable(), hotbarSlot, REPLANT_DELAY_TICKS));
        });
    }

    private static void processPendingReplants(Minecraft mc) {
        Queue<PendingReplant> stillPending = new ConcurrentLinkedQueue<>();

        while (!pendingReplants.isEmpty()) {
            PendingReplant replant = pendingReplants.poll();
            if (replant == null) break;

            if (replant.ticksRemaining() > 0) {
                stillPending.add(new PendingReplant(replant.pos(), replant.seedSlot(), replant.ticksRemaining() - 1));
            } else {
                doReplant(mc, replant.pos(), replant.seedSlot());
            }
        }

        pendingReplants.addAll(stillPending);
    }

    private static void doReplant(Minecraft mc, BlockPos pos, int seedSlot) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            return;
        }

        int previousSlot = player.getInventory().getSelectedSlot();

        player.getInventory().setSelectedSlot(seedSlot);

        BlockHitResult hitResult = new BlockHitResult(
            Vec3.atCenterOf(pos).add(0, -0.5, 0),
            Direction.UP,
            pos,
            false
        );

        mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);

        player.getInventory().setSelectedSlot(previousSlot);
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
        return ALL_HOES.contains(stack.getItem());
    }

    private static int findSeedInHotbar(LocalPlayer player, Item seedItem) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == seedItem) {
                return i;
            }
        }
        return -1;
    }
}
