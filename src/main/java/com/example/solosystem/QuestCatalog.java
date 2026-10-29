package com.example.solosystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;

/**
 * Every quest the game can hand out: 230+ built-in ones, plus quests created automatically for ores, logs, crops,
 * monsters and animals that other mods add.
 *
 * Balance: each quest has "sec" = about how many seconds one unit takes an average player. The target is
 * (time budget of the tier) / sec, so an Uncommon quest is always ~2 minutes of work, a Legendary one ~6-7 minutes,
 * whatever the activity. Rare things (diamonds, ghasts ...) simply get small numbers.
 */
public final class QuestCatalog {
	/** family: used to avoid two similar quests on the same day. minTier: hard-to-reach things only appear in higher tiers. */
	public record Def(String family, String kind, String id, String label, String filter, double sec,
					  int minTier, int min, int max, double scale) {}

	// family | kind | id | label | filter | seconds per unit | min tier | min target | max target | [scale]
	private static final String[] TABLE = {
		// ---------------- mining: ores
		"mining|block|ore_coal|Mine Coal Ore|minecraft:coal_ore,minecraft:deepslate_coal_ore|9|0|4|60",
		"mining|block|ore_copper|Mine Copper Ore|minecraft:copper_ore,minecraft:deepslate_copper_ore|12|0|4|60",
		"mining|block|ore_iron|Mine Iron Ore|minecraft:iron_ore,minecraft:deepslate_iron_ore|20|0|3|50",
		"mining|block|ore_gold|Mine Gold Ore|minecraft:gold_ore,minecraft:deepslate_gold_ore,minecraft:nether_gold_ore|40|1|2|30",
		"mining|block|ore_redstone|Mine Redstone Ore|minecraft:redstone_ore,minecraft:deepslate_redstone_ore|22|0|3|40",
		"mining|block|ore_lapis|Mine Lapis Ore|minecraft:lapis_ore,minecraft:deepslate_lapis_ore|35|1|2|30",
		"mining|block|ore_diamond|Mine Diamond Ore|minecraft:diamond_ore,minecraft:deepslate_diamond_ore|100|2|1|12",
		"mining|block|ore_emerald|Mine Emerald Ore|minecraft:emerald_ore,minecraft:deepslate_emerald_ore|260|3|1|5",
		"mining|block|ore_quartz|Mine Nether Quartz Ore|minecraft:nether_quartz_ore|11|2|4|40",
		"mining|block|ore_debris|Mine Ancient Debris|minecraft:ancient_debris|240|3|1|4",
		"mining|block|ore_any|Mine any ore|@ores|18|0|5|60",
		// ---------------- mining: stone and soil
		"mining|block|stone|Mine Stone|minecraft:stone|1.0|0|30|600",
		"mining|block|cobble|Mine Cobblestone|minecraft:cobblestone|1.0|0|30|600",
		"mining|block|deepslate|Mine Deepslate|minecraft:deepslate,minecraft:cobbled_deepslate|1.3|0|25|450",
		"mining|block|granite|Mine Granite|minecraft:granite|1.1|0|20|300",
		"mining|block|diorite|Mine Diorite|minecraft:diorite|1.1|0|20|300",
		"mining|block|andesite|Mine Andesite|minecraft:andesite|1.1|0|20|300",
		"mining|block|tuff|Mine Tuff|minecraft:tuff|1.2|0|20|300",
		"mining|block|dirt|Dig Dirt|minecraft:dirt,minecraft:grass_block,minecraft:coarse_dirt,minecraft:rooted_dirt,minecraft:podzol,minecraft:mycelium|0.7|0|30|700",
		"mining|block|sand|Dig Sand|minecraft:sand,minecraft:red_sand|0.8|0|25|500",
		"mining|block|gravel|Dig Gravel|minecraft:gravel|0.9|0|25|450",
		"mining|block|clay|Dig Clay|minecraft:clay|1.4|0|15|250",
		"mining|block|sandstone|Mine Sandstone|minecraft:sandstone,minecraft:red_sandstone|1.0|0|20|300",
		"mining|block|netherrack|Mine Netherrack|minecraft:netherrack|0.7|2|40|600",
		"mining|block|blackstone|Mine Blackstone|minecraft:blackstone|1.2|2|20|300",
		"mining|block|basalt|Mine Basalt|minecraft:basalt,minecraft:smooth_basalt|1.2|2|20|300",
		"mining|block|endstone|Mine End Stone|minecraft:end_stone|1.2|3|30|400",
		"mining|block|obsidian|Mine Obsidian|minecraft:obsidian|14|2|1|20",
		"mining|block|glowstone|Mine Glowstone|minecraft:glowstone|3|2|6|60",
		"mining|block|soulsand|Dig Soul Sand|minecraft:soul_sand,minecraft:soul_soil|1|2|20|300",
		"mining|block|stone_any|Mine any stone|@stone|1.0|0|40|700",
		// ---------------- wood
		"wood|block|log_oak|Chop Oak Logs|minecraft:oak_log|1.8|0|8|120",
		"wood|block|log_spruce|Chop Spruce Logs|minecraft:spruce_log|1.8|0|8|120",
		"wood|block|log_birch|Chop Birch Logs|minecraft:birch_log|1.8|0|8|120",
		"wood|block|log_jungle|Chop Jungle Logs|minecraft:jungle_log|2.2|0|8|100",
		"wood|block|log_acacia|Chop Acacia Logs|minecraft:acacia_log|2.0|0|8|100",
		"wood|block|log_darkoak|Chop Dark Oak Logs|minecraft:dark_oak_log|2.0|0|8|100",
		"wood|block|log_mangrove|Chop Mangrove Logs|minecraft:mangrove_log|2.5|0|8|90",
		"wood|block|log_cherry|Chop Cherry Logs|minecraft:cherry_log|2.2|0|8|100",
		"wood|block|log_paleoak|Chop Pale Oak Logs|minecraft:pale_oak_log|2.2|0|8|100",
		"wood|block|log_crimson|Chop Crimson Stems|minecraft:crimson_stem|2.5|2|8|90",
		"wood|block|log_warped|Chop Warped Stems|minecraft:warped_stem|2.5|2|8|90",
		"wood|block|log_any|Chop any logs|@logs|1.8|0|10|150",
		"wood|block|bamboo|Chop Bamboo|minecraft:bamboo|0.8|0|20|300",
		// ---------------- farming
		"farm|block|crop_wheat|Harvest Wheat|minecraft:wheat|3|0|6|80",
		"farm|block|crop_carrot|Harvest Carrots|minecraft:carrots|3|0|6|80",
		"farm|block|crop_potato|Harvest Potatoes|minecraft:potatoes|3|0|6|80",
		"farm|block|crop_beet|Harvest Beetroot|minecraft:beetroots|3.5|0|5|70",
		"farm|block|crop_wart|Harvest Nether Wart|minecraft:nether_wart|5|2|5|60",
		"farm|block|crop_melon|Harvest Melons|minecraft:melon|2.5|0|5|60",
		"farm|block|crop_pumpkin|Harvest Pumpkins|minecraft:pumpkin|2.5|0|5|60",
		"farm|block|crop_cane|Cut Sugar Cane|minecraft:sugar_cane|1.5|0|10|150",
		"farm|block|crop_cactus|Cut Cactus|minecraft:cactus|1.5|0|8|100",
		"farm|block|crop_berries|Pick Sweet Berries|minecraft:sweet_berry_bush|4|0|5|60",
		"farm|block|crop_cocoa|Harvest Cocoa|minecraft:cocoa|4|1|5|60",
		"farm|block|crop_kelp|Cut Kelp|minecraft:kelp,minecraft:kelp_plant|1.5|0|10|150",
		"farm|block|crop_mushroom|Gather Mushrooms|minecraft:brown_mushroom,minecraft:red_mushroom|6|0|3|40",
		"farm|block|crop_any|Harvest any crops|@crops|3|0|8|100",
		// ---------------- combat
		"combat|kill|k_zombie|Slay Zombies|minecraft:zombie,minecraft:zombie_villager,minecraft:husk|10|0|3|45",
		"combat|kill|k_skeleton|Slay Skeletons|minecraft:skeleton,minecraft:stray,minecraft:bogged|14|0|3|35",
		"combat|kill|k_creeper|Defeat Creepers|minecraft:creeper|20|0|2|25",
		"combat|kill|k_spider|Squash Spiders|minecraft:spider,minecraft:cave_spider|14|0|3|35",
		"combat|kill|k_enderman|Defeat Endermen|minecraft:enderman|45|1|1|15",
		"combat|kill|k_witch|Defeat Witches|minecraft:witch|110|2|1|6",
		"combat|kill|k_slime|Squish Slimes|minecraft:slime|35|1|2|20",
		"combat|kill|k_magma|Defeat Magma Cubes|minecraft:magma_cube|35|2|2|20",
		"combat|kill|k_blaze|Defeat Blazes|minecraft:blaze|40|2|1|15",
		"combat|kill|k_ghast|Defeat Ghasts|minecraft:ghast|150|3|1|5",
		"combat|kill|k_witherskel|Defeat Wither Skeletons|minecraft:wither_skeleton|85|3|1|8",
		"combat|kill|k_piglin|Defeat Piglins|minecraft:piglin,minecraft:piglin_brute,minecraft:zombified_piglin|35|2|2|20",
		"combat|kill|k_hoglin|Defeat Hoglins|minecraft:hoglin,minecraft:zoglin|55|2|1|8",
		"combat|kill|k_drowned|Defeat Drowned|minecraft:drowned|30|1|2|20",
		"combat|kill|k_phantom|Defeat Phantoms|minecraft:phantom|70|2|1|8",
		"combat|kill|k_illager|Defeat Illagers|minecraft:pillager,minecraft:vindicator|55|2|1|10",
		"combat|kill|k_guardian|Defeat Guardians|minecraft:guardian,minecraft:elder_guardian|60|2|1|8",
		"combat|kill|k_shulker|Defeat Shulkers|minecraft:shulker|190|3|1|4",
		"combat|kill|k_evoker|Defeat Evokers|minecraft:evoker|260|3|1|3",
		"combat|kill|k_ravager|Defeat Ravagers|minecraft:ravager|260|3|1|3",
		"combat|kill|k_silverfish|Squash Silverfish|minecraft:silverfish|45|2|2|15",
		"combat|kill|k_hostile|Defeat any monsters|@hostile|12|0|4|60",
		"combat|stat|m_damage|Deal damage|minecraft:damage_dealt|3|0|40|1500|0.1",
		// ---------------- hunting
		"hunt|kill|h_cow|Hunt Cows|minecraft:cow,minecraft:mooshroom|7|0|3|30",
		"hunt|kill|h_pig|Hunt Pigs|minecraft:pig|7|0|3|30",
		"hunt|kill|h_sheep|Hunt Sheep|minecraft:sheep|7|0|3|30",
		"hunt|kill|h_chicken|Hunt Chickens|minecraft:chicken|6|0|3|30",
		"hunt|kill|h_rabbit|Hunt Rabbits|minecraft:rabbit|14|0|2|15",
		"hunt|kill|h_fish|Catch fish by hand|minecraft:cod,minecraft:salmon,minecraft:tropical_fish|12|0|2|20",
		"hunt|kill|h_squid|Hunt Squid|minecraft:squid,minecraft:glow_squid|20|0|2|15",
		"hunt|kill|h_any|Hunt animals|@animals|8|0|4|40",
		// ---------------- crafting
		"craft|craft|c_planks|Craft Planks|@suffix:_planks|0.9|0|16|300",
		"craft|craft|c_sticks|Craft Sticks|minecraft:stick|0.7|0|16|300",
		"craft|craft|c_table|Craft Crafting Tables|minecraft:crafting_table|6|0|1|12",
		"craft|craft|c_torch|Craft Torches|minecraft:torch|3|0|8|160",
		"craft|craft|c_chest|Craft Chests & Barrels|minecraft:chest,minecraft:barrel|8|0|1|16",
		"craft|craft|c_furnace|Craft Furnaces|minecraft:furnace,minecraft:blast_furnace,minecraft:smoker|10|0|1|12",
		"craft|craft|c_bread|Bake Bread|minecraft:bread|9|0|3|40",
		"craft|craft|c_pick|Craft Pickaxes|@suffix:_pickaxe|22|0|1|10",
		"craft|craft|c_sword|Craft Swords|@suffix:_sword|22|0|1|10",
		"craft|craft|c_axe|Craft Axes|@suffix:_axe|22|0|1|10",
		"craft|craft|c_shovel|Craft Shovels|@suffix:_shovel|18|0|1|10",
		"craft|craft|c_hoe|Craft Hoes|@suffix:_hoe|18|0|1|10",
		"craft|craft|c_helmet|Craft Helmets|@suffix:_helmet|28|0|1|8",
		"craft|craft|c_chestplate|Craft Chestplates|@suffix:_chestplate|40|0|1|6",
		"craft|craft|c_leggings|Craft Leggings|@suffix:_leggings|36|0|1|6",
		"craft|craft|c_boots|Craft Boots|@suffix:_boots|28|0|1|8",
		"craft|craft|c_bow|Craft Bows|minecraft:bow,minecraft:crossbow|22|0|1|6",
		"craft|craft|c_arrow|Craft Arrows|minecraft:arrow|3|0|8|160",
		"craft|craft|c_shield|Craft Shields|minecraft:shield|22|0|1|6",
		"craft|craft|c_bucket|Craft Buckets|minecraft:bucket|18|0|1|8",
		"craft|craft|c_boat|Craft Boats|@suffix:_boat,@suffix:_raft|9|0|1|10",
		"craft|craft|c_cart|Craft Minecarts|minecraft:minecart|20|1|1|8",
		"craft|craft|c_rail|Craft Rails|minecraft:rail,minecraft:powered_rail,minecraft:detector_rail,minecraft:activator_rail|6|1|6|80",
		"craft|craft|c_bed|Craft Beds|@suffix:_bed|14|0|1|8",
		"craft|craft|c_door|Craft Doors|@suffix:_door|8|0|2|20",
		"craft|craft|c_ladder|Craft Ladders|minecraft:ladder|3|0|4|60",
		"craft|craft|c_fence|Craft Fences|@suffix:_fence,@suffix:_fence_gate|4|0|4|60",
		"craft|craft|c_paper|Craft Paper|minecraft:paper|5|0|6|80",
		"craft|craft|c_book|Craft Books|minecraft:book|14|1|2|20",
		"craft|craft|c_bookshelf|Craft Bookshelves|minecraft:bookshelf|40|2|1|6",
		"craft|craft|c_tnt|Craft TNT|minecraft:tnt|25|1|1|10",
		"craft|craft|c_piston|Craft Pistons|minecraft:piston,minecraft:sticky_piston|38|1|1|8",
		"craft|craft|c_repeater|Craft Repeaters & Comparators|minecraft:repeater,minecraft:comparator|35|2|1|8",
		"craft|craft|c_brewing|Craft Brewing Stands|minecraft:brewing_stand|55|2|1|4",
		"craft|craft|c_enchanting|Craft Enchanting Tables|minecraft:enchanting_table|140|3|1|2",
		"craft|craft|c_anvil|Craft Anvils|minecraft:anvil|100|3|1|3",
		"craft|craft|c_cake|Bake Cakes|minecraft:cake|75|2|1|4",
		"craft|craft|c_gapple|Craft Golden Apples|minecraft:golden_apple|110|3|1|4",
		"craft|craft|c_lantern|Craft Lanterns & Campfires|minecraft:lantern,minecraft:campfire,minecraft:soul_lantern|18|1|1|10",
		"craft|craft|c_bonemeal|Craft Bone Meal|minecraft:bone_meal|4|0|8|120",
		"craft|craft|c_bricks|Craft Bricks|minecraft:bricks,minecraft:stone_bricks|7|0|4|60",
		"craft|craft|c_bottle|Craft Glass Bottles|minecraft:glass_bottle|8|0|3|30",
		// ---------------- smelting and cooking
		"smelt|craft|s_iron|Smelt Iron Ingots|minecraft:iron_ingot|7|0|4|64",
		"smelt|craft|s_gold|Smelt Gold Ingots|minecraft:gold_ingot|14|1|2|40",
		"smelt|craft|s_copper|Smelt Copper Ingots|minecraft:copper_ingot|6|0|4|64",
		"smelt|craft|s_glass|Smelt Glass|minecraft:glass|4|0|6|96",
		"smelt|craft|s_stone|Smelt Stone|minecraft:stone,minecraft:smooth_stone|3|0|8|128",
		"smelt|craft|s_brick|Smelt Bricks|minecraft:brick|5|0|4|64",
		"smelt|craft|s_charcoal|Make Charcoal|minecraft:charcoal|5|0|4|64",
		"smelt|craft|s_beef|Cook Steak|minecraft:cooked_beef|6|0|4|48",
		"smelt|craft|s_pork|Cook Porkchops|minecraft:cooked_porkchop|6|0|4|48",
		"smelt|craft|s_chicken|Cook Chicken|minecraft:cooked_chicken|6|0|4|48",
		"smelt|craft|s_mutton|Cook Mutton|minecraft:cooked_mutton|6|0|4|48",
		"smelt|craft|s_fish|Cook Fish|minecraft:cooked_cod,minecraft:cooked_salmon|6|0|4|48",
		"smelt|craft|s_potato|Bake Potatoes|minecraft:baked_potato|5|0|4|48",
		"smelt|craft|s_kelp|Dry Kelp|minecraft:dried_kelp|3|0|6|64",
		"smelt|craft|s_cooked|Cook any food|@prefix:cooked_|6|0|6|64",
		"smelt|craft|s_terracotta|Smelt Terracotta|minecraft:terracotta|5|0|4|64",
		// ---------------- using items
		"use|use|u_torch|Place Torches|minecraft:torch|1.5|0|10|160",
		"use|use|u_cobble|Place Cobblestone|minecraft:cobblestone|1.0|0|30|400",
		"use|use|u_dirt|Place Dirt|minecraft:dirt|0.9|0|30|400",
		"use|use|u_planks|Place Planks|@suffix:_planks|1.1|0|25|350",
		"use|use|u_bread|Eat Bread|minecraft:bread|7|0|2|20",
		"use|use|u_steak|Eat Steak & Porkchops|minecraft:cooked_beef,minecraft:cooked_porkchop|7|0|2|20",
		"use|use|u_apple|Eat Apples|minecraft:apple|9|0|2|20",
		"use|use|u_food|Eat anything|@food|7|0|3|30",
		"use|use|u_bonemeal|Use Bone Meal|minecraft:bone_meal|3|0|6|100",
		"use|use|u_pearl|Throw Ender Pearls|minecraft:ender_pearl|35|2|1|8",
		"use|use|u_snowball|Throw Snowballs & Eggs|minecraft:snowball,minecraft:egg|2|0|10|120",
		"use|use|u_bow|Shoot Arrows|minecraft:bow,minecraft:crossbow|4|0|10|140",
		"use|use|u_rod|Cast the Fishing Rod|minecraft:fishing_rod|10|0|3|40",
		"use|use|u_shears|Use Shears|minecraft:shears|6|0|3|40",
		"use|use|u_bucket|Use Buckets|minecraft:bucket,minecraft:water_bucket,minecraft:lava_bucket|10|0|2|30",
		"use|use|u_flint|Light Fires|minecraft:flint_and_steel,minecraft:fire_charge|10|0|2|20",
		"use|use|u_rocket|Launch Fireworks|minecraft:firework_rocket|14|2|1|10",
		"use|use|u_trident|Throw Tridents|minecraft:trident|25|3|2|20",
		"use|use|u_lead|Use Leads & Name Tags|minecraft:lead,minecraft:name_tag|10|1|2|20",
		"use|use|u_hoe|Till Soil|@suffix:_hoe|3|0|8|120",
		"use|use|u_axe|Strip Logs|@suffix:_axe|3|0|8|120",
		"use|use|u_shovel|Flatten Paths|@suffix:_shovel|3|0|8|120",
		"use|use|u_potion|Drink Potions|minecraft:potion|20|2|1|8",
		"use|use|u_goldfood|Eat Golden Food|minecraft:golden_apple,minecraft:golden_carrot|20|2|1|8",
		// ---------------- collecting items
		"collect|pickup|p_cobble|Collect Cobblestone|minecraft:cobblestone|1.1|0|30|500",
		"collect|pickup|p_dirt|Collect Dirt|minecraft:dirt|1.0|0|30|500",
		"collect|pickup|p_sand|Collect Sand|minecraft:sand|1.0|0|25|400",
		"collect|pickup|p_gravel|Collect Gravel|minecraft:gravel|1.0|0|25|400",
		"collect|pickup|p_coal|Collect Coal|minecraft:coal|8|0|4|80",
		"collect|pickup|p_rawiron|Collect Raw Iron|minecraft:raw_iron|18|0|3|60",
		"collect|pickup|p_rawcopper|Collect Raw Copper|minecraft:raw_copper|10|0|4|80",
		"collect|pickup|p_rawgold|Collect Raw Gold|minecraft:raw_gold|35|1|2|40",
		"collect|pickup|p_redstone|Collect Redstone|minecraft:redstone|8|0|6|100",
		"collect|pickup|p_lapis|Collect Lapis Lazuli|minecraft:lapis_lazuli|10|1|6|80",
		"collect|pickup|p_diamond|Collect Diamonds|minecraft:diamond|100|2|1|12",
		"collect|pickup|p_emerald|Collect Emeralds|minecraft:emerald|240|3|1|6",
		"collect|pickup|p_quartz|Collect Nether Quartz|minecraft:quartz|10|2|6|60",
		"collect|pickup|p_bone|Collect Bones|minecraft:bone|14|0|3|40",
		"collect|pickup|p_flesh|Collect Rotten Flesh|minecraft:rotten_flesh|8|0|4|50",
		"collect|pickup|p_powder|Collect Gunpowder|minecraft:gunpowder|20|0|2|25",
		"collect|pickup|p_string|Collect String|minecraft:string|10|0|4|50",
		"collect|pickup|p_eye|Collect Spider Eyes|minecraft:spider_eye|24|1|2|15",
		"collect|pickup|p_feather|Collect Feathers|minecraft:feather|10|0|4|40",
		"collect|pickup|p_leather|Collect Leather|minecraft:leather|10|0|3|40",
		"collect|pickup|p_egg|Collect Eggs|minecraft:egg|18|0|3|24",
		"collect|pickup|p_wheat|Collect Wheat|minecraft:wheat|3|0|6|100",
		"collect|pickup|p_carrot|Collect Carrots|minecraft:carrot|3|0|6|100",
		"collect|pickup|p_potato|Collect Potatoes|minecraft:potato|3|0|6|100",
		"collect|pickup|p_seeds|Collect Seeds|minecraft:wheat_seeds,minecraft:beetroot_seeds,minecraft:melon_seeds,minecraft:pumpkin_seeds|4|0|6|100",
		"collect|pickup|p_apple|Collect Apples|minecraft:apple|40|0|1|10",
		"collect|pickup|p_stick|Collect Sticks|minecraft:stick|1.2|0|20|300",
		"collect|pickup|p_arrow|Collect Arrows|minecraft:arrow|10|0|3|40",
		"collect|pickup|p_pearl|Collect Ender Pearls|minecraft:ender_pearl|55|2|1|8",
		"collect|pickup|p_blazerod|Collect Blaze Rods|minecraft:blaze_rod|50|2|1|10",
		"collect|pickup|p_slime|Collect Slimeballs|minecraft:slime_ball|35|1|2|16",
		"collect|pickup|p_ink|Collect Ink Sacs|minecraft:ink_sac,minecraft:glow_ink_sac|18|0|2|20",
		"collect|pickup|p_glowdust|Collect Glowstone Dust|minecraft:glowstone_dust|5|2|6|80",
		"collect|pickup|p_flint|Collect Flint|minecraft:flint|6|0|4|50",
		"collect|pickup|p_clayball|Collect Clay Balls|minecraft:clay_ball|3|0|8|120",
		"collect|pickup|p_cane|Collect Sugar Cane|minecraft:sugar_cane|1.6|0|8|120",
		// ---------------- travel
		"travel|stat|t_walk|Travel on foot (blocks)|minecraft:walk_one_cm,minecraft:sprint_one_cm|0.22|0|120|4000|0.01",
		"travel|stat|t_sprint|Sprint (blocks)|minecraft:sprint_one_cm|0.2|0|120|3500|0.01",
		"travel|stat|t_swim|Swim (blocks)|minecraft:swim_one_cm,minecraft:walk_under_water_one_cm|0.45|0|40|1000|0.01",
		"travel|stat|t_climb|Climb (blocks)|minecraft:climb_one_cm|0.8|0|15|300|0.01",
		"travel|stat|t_boat|Sail a boat (blocks)|minecraft:boat_one_cm|0.15|1|120|3000|0.01",
		"travel|stat|t_cart|Ride a minecart (blocks)|minecraft:minecart_one_cm|0.1|1|150|4000|0.01",
		"travel|stat|t_horse|Ride a horse (blocks)|minecraft:horse_one_cm|0.1|1|150|4000|0.01",
		"travel|stat|t_fly|Glide with elytra (blocks)|minecraft:aviate_one_cm|0.06|3|300|6000|0.01",
		"travel|stat|t_sneak|Sneak (blocks)|minecraft:crouch_one_cm|0.7|0|25|400|0.01",
		"travel|stat|t_jump|Jump|minecraft:jump|0.8|0|30|600|1",
		// ---------------- villagers, stations and misc
		"misc|stat|m_fish|Catch fish with a rod|minecraft:fish_caught|20|0|2|25|1",
		"misc|stat|m_breed|Breed animals|minecraft:animals_bred|25|0|2|14|1",
		"social|stat|m_trade|Trade with villagers|minecraft:traded_with_villager|22|1|1|12|1",
		"social|stat|m_talk|Talk to villagers|minecraft:talked_to_villager|8|0|3|40|1",
		"misc|stat|m_enchant|Enchant items|minecraft:enchant_item|55|2|1|6|1",
		"misc|stat|m_chest|Open chests & barrels|minecraft:open_chest,minecraft:open_barrel|8|0|3|40|1",
		"misc|stat|m_anvil|Use an Anvil|minecraft:interact_with_anvil|40|2|1|5|1",
		"misc|stat|m_table|Use a Crafting Table|minecraft:interact_with_crafting_table|10|0|3|40|1",
		"misc|stat|m_furnace|Use Furnaces|minecraft:interact_with_furnace,minecraft:interact_with_blast_furnace,minecraft:interact_with_smoker|14|0|2|25|1",
		"misc|stat|m_grind|Use a Grindstone|minecraft:interact_with_grindstone|30|2|1|6|1",
		"misc|stat|m_stonecut|Use a Stonecutter|minecraft:interact_with_stonecutter|18|1|2|15|1",
		"misc|stat|m_smith|Use a Smithing Table|minecraft:interact_with_smithing_table|90|3|1|3|1",
		"misc|stat|m_brew|Use a Brewing Stand|minecraft:interact_with_brewingstand|50|2|1|6|1",
		"social|stat|m_bell|Ring a village bell|minecraft:bell_ring|10|1|1|6|1",
		"misc|xp|m_xp|Collect experience|-|3|0|20|500|1",
		"misc|active|m_time|Stay active (seconds)|-|1|0|60|400|1"
	};

	private static List<Def> available;

	private QuestCatalog() {}

	public static int builtInCount() { return TABLE.length; }

	private static Def parse(String line) {
		String[] p = line.split("\\|", -1);
		double scale = p.length > 9 ? Double.parseDouble(p[9]) : 1.0;
		return new Def(p[0], p[1], p[2], p[3], p[4], Double.parseDouble(p[5]), Integer.parseInt(p[6]), Integer.parseInt(p[7]), Integer.parseInt(p[8]), scale);
	}

	/** Every quest that can actually be done in this game (built-in ones whose things exist + quests for other mods). */
	public static synchronized List<Def> available() {
		if (available != null) return available;
		List<Def> all = new ArrayList<>();
		for (String line : TABLE) all.add(parse(line));
		all.addAll(forMods());
		List<Def> ok = new ArrayList<>();
		for (Def d : all) if (QuestTracker.valid(d)) ok.add(d);
		available = ok;
		return ok;
	}

	// ---------------------------------------------------------------- quests created for other mods

	private static String pretty(String base) {
		StringBuilder sb = new StringBuilder();
		for (String w : base.split("_")) {
			if (w.isEmpty()) continue;
			if (sb.length() > 0) sb.append(' ');
			sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return sb.toString();
	}

	private static String strip(String path, String... suffixes) {
		String b = path;
		for (String pre : new String[]{"deepslate_", "nether_", "end_", "stone_", "netherrack_", "stripped_"}) if (b.startsWith(pre) && b.length() > pre.length()) b = b.substring(pre.length());
		for (String s : suffixes) if (b.endsWith(s) && b.length() > s.length()) b = b.substring(0, b.length() - s.length());
		return b;
	}

	private static void group(Map<String, List<String>> map, String key, String id) {
		map.computeIfAbsent(key, k -> new ArrayList<>()).add(id);
	}

	/** Ores, logs, crops, monsters and animals added by other mods become quests automatically. */
	private static List<Def> forMods() {
		List<Def> out = new ArrayList<>();
		Map<String, List<String>> ores = new LinkedHashMap<>(), logs = new LinkedHashMap<>(), crops = new LinkedHashMap<>();
		try {
			for (Block b : BuiltInRegistries.BLOCK) {
				String id = BuiltInRegistries.BLOCK.getKey(b).toString();
				if (id.startsWith("minecraft:") || id.startsWith("solosystem:")) continue;
				int c = id.indexOf(':');
				String ns = id.substring(0, c), path = id.substring(c + 1);
				if (QuestTracker.isOre(b)) group(ores, ns + "_" + strip(path, "_ore", "_ores"), id);
				else if (b.defaultBlockState().is(BlockTags.LOGS)) group(logs, ns + "_" + strip(path, "_log", "_stem", "_wood"), id);
				else if (b instanceof CropBlock) group(crops, ns + "_" + path, id);
			}
		} catch (Exception ignored) {}

		for (Map.Entry<String, List<String>> e : ores.entrySet())
			out.add(new Def("mining", "block", "mod_ore_" + e.getKey(), "Mine " + pretty(e.getKey().substring(e.getKey().indexOf('_') + 1)) + " Ore",
				String.join(",", e.getValue()), 22, 1, 3, 40, 1.0));
		for (Map.Entry<String, List<String>> e : logs.entrySet())
			out.add(new Def("wood", "block", "mod_log_" + e.getKey(), "Chop " + pretty(e.getKey().substring(e.getKey().indexOf('_') + 1)) + " Logs",
				String.join(",", e.getValue()), 2.2, 0, 8, 120, 1.0));
		for (Map.Entry<String, List<String>> e : crops.entrySet())
			out.add(new Def("farm", "block", "mod_crop_" + e.getKey(), "Harvest " + pretty(e.getKey().substring(e.getKey().indexOf('_') + 1)),
				String.join(",", e.getValue()), 4, 0, 5, 70, 1.0));

		try {
			for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
				String id = BuiltInRegistries.ENTITY_TYPE.getKey(t).toString();
				if (id.startsWith("minecraft:")) continue;
				String name = pretty(id.substring(id.indexOf(':') + 1));
				if (t.getCategory() == MobCategory.MONSTER)
					out.add(new Def("combat", "kill", "mod_mob_" + id.replace(':', '_'), "Defeat " + name, id, 40, 1, 2, 25, 1.0));
				else if (t.getCategory() == MobCategory.CREATURE)
					out.add(new Def("hunt", "kill", "mod_animal_" + id.replace(':', '_'), "Hunt " + name, id, 12, 0, 3, 30, 1.0));
			}
		} catch (Exception ignored) {}

		if (out.size() > 220) out = new ArrayList<>(out.subList(0, 220)); // very large modpacks: keep it sane
		return out;
	}
}
