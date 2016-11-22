/*
 * This file ("MethodHandler.java") is part of the Actually Additions mod for Minecraft.
 * It is created and owned by Ellpeck and distributed
 * under the Actually Additions License to be found at
 * http://ellpeck.de/actaddlicense
 * View the source code at https://github.com/Ellpeck/ActuallyAdditions
 *
 * © 2015-2016 Ellpeck
 */

package de.ellpeck.actuallyadditions.mod.misc;

import de.ellpeck.actuallyadditions.api.ActuallyAdditionsAPI;
import de.ellpeck.actuallyadditions.api.booklet.IBookletChapter;
import de.ellpeck.actuallyadditions.api.booklet.IBookletEntry;
import de.ellpeck.actuallyadditions.api.booklet.IBookletPage;
import de.ellpeck.actuallyadditions.api.internal.IAtomicReconstructor;
import de.ellpeck.actuallyadditions.api.internal.IMethodHandler;
import de.ellpeck.actuallyadditions.api.recipe.CoffeeIngredient;
import de.ellpeck.actuallyadditions.api.recipe.LensConversionRecipe;
import de.ellpeck.actuallyadditions.mod.booklet.chapter.BookletChapter;
import de.ellpeck.actuallyadditions.mod.booklet.page.PageCrafting;
import de.ellpeck.actuallyadditions.mod.booklet.page.PageFurnace;
import de.ellpeck.actuallyadditions.mod.booklet.page.PagePicture;
import de.ellpeck.actuallyadditions.mod.booklet.page.PageTextOnly;
import de.ellpeck.actuallyadditions.mod.items.lens.LensRecipeHandler;
import de.ellpeck.actuallyadditions.mod.recipe.CrusherRecipeRegistry;
import de.ellpeck.actuallyadditions.mod.util.StackUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.List;

public class MethodHandler implements IMethodHandler{

    @Override
    public boolean addEffectToStack(ItemStack stack, CoffeeIngredient ingredient){
        boolean worked = false;
        if(ingredient != null){
            PotionEffect[] effects = ingredient.getEffects();
            if(effects != null && effects.length > 0){
                for(PotionEffect effect : effects){
                    PotionEffect effectHas = this.getSameEffectFromStack(stack, effect);
                    if(effectHas != null){
                        if(effectHas.getAmplifier() < ingredient.maxAmplifier-1){
                            this.addEffectProperties(stack, effect, false, true);
                            worked = true;
                        }
                    }
                    else{
                        this.addEffectToStack(stack, effect);
                        worked = true;
                    }
                }
            }
        }
        return worked;
    }

    @Override
    public PotionEffect getSameEffectFromStack(ItemStack stack, PotionEffect effect){
        PotionEffect[] effectsStack = this.getEffectsFromStack(stack);
        if(effectsStack != null && effectsStack.length > 0){
            for(PotionEffect effectStack : effectsStack){
                if(effect.getPotion() == effectStack.getPotion()){
                    return effectStack;
                }
            }
        }
        return null;
    }

    @Override
    public void addEffectProperties(ItemStack stack, PotionEffect effect, boolean addDur, boolean addAmp){
        PotionEffect[] effects = this.getEffectsFromStack(stack);
        stack.setTagCompound(new NBTTagCompound());
        for(int i = 0; i < effects.length; i++){
            if(effects[i].getPotion() == effect.getPotion()){
                effects[i] = new PotionEffect(effects[i].getPotion(), effects[i].getDuration()+(addDur ? effect.getDuration() : 0), effects[i].getAmplifier()+(addAmp ? (effect.getAmplifier() > 0 ? effect.getAmplifier() : 1) : 0));
            }
            this.addEffectToStack(stack, effects[i]);
        }
    }

    @Override
    public void addEffectToStack(ItemStack stack, PotionEffect effect){
        NBTTagCompound tag = stack.getTagCompound();
        if(tag == null){
            tag = new NBTTagCompound();
        }

        int prevCounter = tag.getInteger("Counter");
        NBTTagCompound compound = new NBTTagCompound();
        compound.setInteger("ID", Potion.getIdFromPotion(effect.getPotion()));
        compound.setInteger("Duration", effect.getDuration());
        compound.setInteger("Amplifier", effect.getAmplifier());

        int counter = prevCounter+1;
        tag.setTag(counter+"", compound);
        tag.setInteger("Counter", counter);

        stack.setTagCompound(tag);
    }

    @Override
    public PotionEffect[] getEffectsFromStack(ItemStack stack){
        ArrayList<PotionEffect> effects = new ArrayList<PotionEffect>();
        NBTTagCompound tag = stack.getTagCompound();
        if(tag != null){
            int counter = tag.getInteger("Counter");
            while(counter > 0){
                NBTTagCompound compound = (NBTTagCompound)tag.getTag(counter+"");
                PotionEffect effect = new PotionEffect(Potion.getPotionById(compound.getInteger("ID")), compound.getInteger("Duration"), compound.getByte("Amplifier"));
                effects.add(effect);
                counter--;
            }
        }
        return effects.size() > 0 ? effects.toArray(new PotionEffect[effects.size()]) : null;
    }

    @Override
    public boolean invokeConversionLens(IBlockState hitState, BlockPos hitBlock, IAtomicReconstructor tile){
        if(hitBlock != null && !hitState.getBlock().isAir(hitState, tile.getWorldObject(), hitBlock)){
            int range = 2;

            //Converting the Blocks
            for(int reachX = -range; reachX < range+1; reachX++){
                for(int reachZ = -range; reachZ < range+1; reachZ++){
                    for(int reachY = -range; reachY < range+1; reachY++){
                        BlockPos pos = new BlockPos(hitBlock.getX()+reachX, hitBlock.getY()+reachY, hitBlock.getZ()+reachZ);
                        if(!tile.getWorldObject().isAirBlock(pos)){
                            IBlockState state = tile.getWorldObject().getBlockState(pos);
                            List<LensConversionRecipe> recipes = LensRecipeHandler.getRecipesFor(new ItemStack(state.getBlock(), 1, state.getBlock().getMetaFromState(state)));
                            for(LensConversionRecipe recipe : recipes){
                                if(recipe != null && recipe.type == tile.getLens() && tile.getEnergy() >= recipe.energyUse){
                                    ItemStack output = recipe.outputStack;
                                    if(StackUtil.isValid(output)){
                                        tile.getWorldObject().playEvent(2001, pos, Block.getStateId(tile.getWorldObject().getBlockState(pos)));

                                        if(output.getItem() instanceof ItemBlock){
                                            tile.getWorldObject().setBlockState(pos, Block.getBlockFromItem(output.getItem()).getStateFromMeta(output.getItemDamage()), 2);
                                        }
                                        else{
                                            EntityItem item = new EntityItem(tile.getWorldObject(), pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5, output.copy());
                                            tile.getWorldObject().spawnEntityInWorld(item);
                                            tile.getWorldObject().setBlockToAir(pos);
                                        }

                                        tile.extractEnergy(recipe.energyUse);
                                        break;
                                    }
                                }
                            }
                        }
                        else{
                            if(tile.getWorldObject() instanceof WorldServer){
                                ((WorldServer)tile.getWorldObject()).spawnParticle(EnumParticleTypes.END_ROD, false, pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5, 1, 0, 0, 0, 0D);
                            }
                        }
                    }
                }
            }

            //Converting the Items
            ArrayList<EntityItem> items = (ArrayList<EntityItem>)tile.getWorldObject().getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(hitBlock.getX()-range, hitBlock.getY()-range, hitBlock.getZ()-range, hitBlock.getX()+range, hitBlock.getY()+range, hitBlock.getZ()+range));
            for(EntityItem item : items){
                ItemStack stack = item.getEntityItem();
                if(!item.isDead && StackUtil.isValid(stack)){
                    List<LensConversionRecipe> recipes = LensRecipeHandler.getRecipesFor(stack);
                    for(LensConversionRecipe recipe : recipes){
                        if(recipe != null && recipe.type == tile.getLens()){
                            int itemsPossible = Math.min(tile.getEnergy()/recipe.energyUse, StackUtil.getStackSize(stack));

                            if(itemsPossible > 0){
                                item.setDead();

                                if(StackUtil.getStackSize(stack)-itemsPossible > 0){
                                    ItemStack stackCopy = stack.copy();
                                    stackCopy = StackUtil.addStackSize(stackCopy, -itemsPossible);

                                    EntityItem inputLeft = new EntityItem(tile.getWorldObject(), item.posX, item.posY, item.posZ, stackCopy);
                                    tile.getWorldObject().spawnEntityInWorld(inputLeft);
                                }

                                ItemStack outputCopy = recipe.outputStack.copy();
                                outputCopy = StackUtil.setStackSize(outputCopy, itemsPossible);

                                EntityItem newItem = new EntityItem(tile.getWorldObject(), item.posX, item.posY, item.posZ, outputCopy);
                                tile.getWorldObject().spawnEntityInWorld(newItem);

                                tile.extractEnergy(recipe.energyUse*itemsPossible);
                                break;
                            }
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean addCrusherRecipes(List<ItemStack> inputs, List<ItemStack> outputOnes, int outputOneAmounts, List<ItemStack> outputTwos, int outputTwoAmounts, int outputTwoChance){
        boolean hasWorkedOnce = false;
        for(ItemStack input : inputs){
            if(StackUtil.isValid(input) && CrusherRecipeRegistry.getRecipeFromInput(input) == null){
                for(ItemStack outputOne : outputOnes){
                    if(StackUtil.isValid(outputOne) && !CrusherRecipeRegistry.hasBlacklistedOutput(outputOne)){
                        ItemStack outputOneCopy = outputOne.copy();
                        outputOneCopy = StackUtil.setStackSize(outputOneCopy, outputOneAmounts);

                        if(outputTwos == null || outputTwos.isEmpty()){
                            ActuallyAdditionsAPI.addCrusherRecipe(input, outputOneCopy, StackUtil.getNull(), 0);
                            hasWorkedOnce = true;
                        }
                        else{
                            for(ItemStack outputTwo : outputTwos){
                                if(StackUtil.isValid(outputTwo) && !CrusherRecipeRegistry.hasBlacklistedOutput(outputTwo)){
                                    ItemStack outputTwoCopy = outputTwo.copy();
                                    outputTwoCopy = StackUtil.setStackSize(outputTwoCopy, outputTwoAmounts);

                                    ActuallyAdditionsAPI.addCrusherRecipe(input, outputOneCopy, outputTwoCopy, outputTwoChance);
                                    hasWorkedOnce = true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return hasWorkedOnce;
    }

    @Override
    public IBookletPage generateTextPage(int id){
        return this.generateTextPage(id, 0);
    }

    @Override
    public IBookletPage generatePicturePage(int id, ResourceLocation resLoc, int textStartY){
        return this.generatePicturePage(id, resLoc, textStartY, 0);
    }

    @Override
    public IBookletPage generateCraftingPage(int id, IRecipe... recipes){
        return this.generateCraftingPage(id, 0, recipes);
    }

    @Override
    public IBookletPage generateFurnacePage(int id, ItemStack input, ItemStack result){
        return this.generateFurnacePage(id, input, result, 0);
    }

    @Override
    public IBookletChapter generateBookletChapter(String identifier, IBookletEntry entry, ItemStack displayStack, IBookletPage... pages){
        return this.generateBookletChapter(identifier, entry, displayStack, 0, pages);
    }

    @Override
    public IBookletPage generateTextPage(int id, int priority){
        return new PageTextOnly(id, priority);
    }

    @Override
    public IBookletPage generatePicturePage(int id, ResourceLocation resLoc, int textStartY, int priority){
        return new PagePicture(id, resLoc, textStartY, priority);
    }

    @Override
    public IBookletPage generateCraftingPage(int id, int priority, IRecipe... recipes){
        return new PageCrafting(id, priority, recipes);
    }

    @Override
    public IBookletPage generateFurnacePage(int id, ItemStack input, ItemStack result, int priority){
        return new PageFurnace(id, result, priority);
    }

    @Override
    public IBookletChapter generateBookletChapter(String identifier, IBookletEntry entry, ItemStack displayStack, int priority, IBookletPage... pages){
        return new BookletChapter(identifier, entry, displayStack, priority, pages);
    }
}
