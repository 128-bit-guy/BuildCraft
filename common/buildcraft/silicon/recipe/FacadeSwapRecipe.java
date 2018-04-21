/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.recipe;

import buildcraft.api.items.BCStackHelper;
import com.google.common.collect.Lists;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import net.minecraftforge.common.ForgeHooks;

import buildcraft.lib.recipe.ChangingItemStack;
import buildcraft.lib.recipe.IRecipeViewable;

import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.item.ItemPluggableFacade;
import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeInstance;
import buildcraft.silicon.plug.FacadeStateManager;

import java.util.List;

public enum FacadeSwapRecipe implements IRecipe, IRecipeViewable.IViewableGrid {
    INSTANCE;

    private static final int TIME_GAP = 500;

    private static final ChangingItemStack[] INPUTS = { null };
    private static ChangingItemStack OUTPUTS;

    public static void genRecipes() {
        List<ItemStack> list1 = Lists.newArrayList();
        List<ItemStack> list2 = Lists.newArrayList();
        for (FacadeBlockStateInfo info : FacadeStateManager.validFacadeStates.values()) {
            if (info.isVisible) {
                ItemStack stack = createFacade(info, false);
                ItemStack stackHollow = createFacade(info, true);
                list1.add(stack);
                list1.add(stackHollow);
                list2.add(stackHollow);
                list2.add(stack);
            }
        }
        INPUTS[0] = new ChangingItemStack(list1);
        INPUTS[0].setTimeGap(TIME_GAP);

        OUTPUTS = new ChangingItemStack(list2);
        OUTPUTS.setTimeGap(TIME_GAP);
    }

    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        return getCraftingResult(inv) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        ItemStack stackIn = null;
        for (int s = 0; s < inv.getSizeInventory(); s++) {
            ItemStack stack = inv.getStackInSlot(s);
            if (stack != null) {
                if (stackIn == null) {
                    stackIn = stack;
                } else {
                    return null;
                }
            }
        }
        if (!BCStackHelper.isEmpty(stackIn) && stackIn.getItem() instanceof ItemPluggableFacade) {
            FacadeInstance states = ItemPluggableFacade.getStates(stackIn);
            states = states.withSwappedIsHollow();
            return BCSiliconItems.plugFacade.createItemStack(states);
        }
        return null;
    }

    @Override
    public int getRecipeSize() {
        return 1;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return null;
    }

    @Override
    public ItemStack[] getRemainingItems(InventoryCrafting inv) {
        return ForgeHooks.defaultRecipeGetRemainingItems(inv);
    }

    @Override
    public ChangingItemStack[] getRecipeInputs() {
        if (INPUTS[0] == null) {
            genRecipes();
        }
        return INPUTS;
    }

    @Override
    public ChangingItemStack getRecipeOutputs() {
        if (OUTPUTS == null) {
            genRecipes();
        }
        return OUTPUTS;
    }

    private static ItemStack createFacade(FacadeBlockStateInfo info, boolean isHollow) {
        FacadeInstance state = FacadeInstance.createSingle(info, isHollow);
        return BCSiliconItems.plugFacade.createItemStack(state);
    }

    @Override
    public int getRecipeWidth() {
        return 1;
    }

    @Override
    public int getRecipeHeight() {
        return 1;
    }
}