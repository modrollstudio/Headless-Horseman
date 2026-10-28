package studio.modroll.horseman.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import studio.modroll.horseman.CritfallHorseman;
import studio.modroll.horseman.entity.HorsemanEntities;

/** Items and the creative tab contents, in registration order. */
public final class HorsemanItems {

    public static final ResourceLocation TAB = CritfallHorseman.id(CritfallHorseman.MOD_ID);

    public static final SwordItem EXECUTIONERS_SWORD = new SwordItem(
            Tiers.IRON,
            new Item.Properties()
                    .rarity(Rarity.EPIC)
                    .fireResistant()
                    .attributes(SwordItem.createAttributes(Tiers.IRON, 6, -3.2F)));

    public static final HorsemanHeadItem HORSEMAN_HEAD = new HorsemanHeadItem(
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

    /** Kept out of the creative tab: it only works while tied to a living Horseman. */
    public static final BurningHeadItem BURNING_HEAD = new BurningHeadItem(
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

    public static final SpawnEggItem HORSEMAN_SPAWN_EGG =
            new SpawnEggItem(HorsemanEntities.HORSEMAN, 0x3A2A22, 0xF08A24, new Item.Properties());

    public static final SpawnEggItem STEED_SPAWN_EGG =
            new SpawnEggItem(HorsemanEntities.STEED, 0x141218, 0x6E6C72, new Item.Properties());

    private static final List<Item> TAB_ITEMS = new ArrayList<>();

    private HorsemanItems() {}

    public static void register(BiConsumer<ResourceLocation, Item> registrar) {
        add(registrar, "executioners_sword", EXECUTIONERS_SWORD);
        add(registrar, "horseman_head", HORSEMAN_HEAD);
        registrar.accept(CritfallHorseman.id("burning_head"), BURNING_HEAD);
        add(registrar, "horseman_spawn_egg", HORSEMAN_SPAWN_EGG);
        add(registrar, "gallows_steed_spawn_egg", STEED_SPAWN_EGG);
    }

    private static void add(BiConsumer<ResourceLocation, Item> registrar, String name, Item item) {
        registrar.accept(CritfallHorseman.id(name), item);
        TAB_ITEMS.add(item);
    }

    public static Component tabTitle() {
        return Component.translatable("itemGroup.critfall_horseman");
    }

    public static ItemStack tabIcon() {
        return new ItemStack(HORSEMAN_HEAD);
    }

    public static void fillTab(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        TAB_ITEMS.forEach(output::accept);
    }
}
