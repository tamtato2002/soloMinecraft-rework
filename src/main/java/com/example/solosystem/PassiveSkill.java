package com.example.solosystem;

public enum PassiveSkill {
	// ---- COMMON
	LIGHT_FEET("light_feet", "Light Feet", "Step up full blocks", SkillRarity.COMMON, 1),
	SWIFT_STEP("swift_step", "Swift Step", "+5% move speed / rank", SkillRarity.COMMON, 3),
	HUNTERS_INSTINCT("hunters_instinct", "Hunter's Instinct", "+0.5 attack damage / rank", SkillRarity.COMMON, 3),
	THICK_SKIN("thick_skin", "Thick Skin", "+1 armor toughness / rank", SkillRarity.COMMON, 3),
	QUICK_HANDS("quick_hands", "Quick Hands", "+0.5 mining efficiency / rank", SkillRarity.COMMON, 3),
	// ---- UNCOMMON
	GILLS("gills", "Gills", "More air + faster swimming", SkillRarity.UNCOMMON, 3),
	FEATHER_FALL("feather_fall", "Feather Fall", "-25% fall damage / rank", SkillRarity.UNCOMMON, 3),
	SPRINGS("springs", "Spring Legs", "Higher jump", SkillRarity.UNCOMMON, 3),
	MAGNET("magnet", "Loot Magnet", "Pull nearby items (3/5/8 blocks)", SkillRarity.UNCOMMON, 3),
	ANGLERS_LUCK("anglers_luck", "Angler's Luck", "+0.5 luck / rank (better fishing)", SkillRarity.UNCOMMON, 3),
	HEARTY("hearty", "Hearty", "+2 max HP / rank", SkillRarity.UNCOMMON, 3),
	// ---- RARE
	MINERS_HANDS("miners_hands", "Miner's Hands", "+1 mining efficiency / rank", SkillRarity.RARE, 3),
	CAT_EYES("cat_eyes", "Cat's Eyes", "Permanent night vision", SkillRarity.RARE, 1),
	IRON_SKIN("iron_skin", "Iron Skin", "+2 armor / rank", SkillRarity.RARE, 3),
	BERSERKER("berserker", "Berserker's Might", "+1 attack damage / rank", SkillRarity.RARE, 3),
	IRON_STOMACH("iron_stomach", "Iron Stomach", "Slowly restores hunger", SkillRarity.RARE, 2),
	SAGES_MIND("sages_mind", "Sage's Mind", "+5% EXP gain / rank", SkillRarity.RARE, 3),
	TREASURE_EYE("treasure_eye", "Treasure Eye", "+4 effective Sense for loot / rank", SkillRarity.RARE, 3),
	// ---- EPIC
	REGEN("regen", "Natural Healing", "Passive regeneration", SkillRarity.EPIC, 2),
	HEAT_WARD("heat_ward", "Heat Ward", "Permanent fire resistance", SkillRarity.EPIC, 1),
	LUCKY("lucky", "Fortune's Favor", "+1 luck / rank", SkillRarity.EPIC, 3),
	TITAN_FRAME("titan_frame", "Titan Frame", "+4 max HP, +1 toughness / rank", SkillRarity.EPIC, 3),
	WINDWALKER("windwalker", "Windwalker", "+8% move speed / rank", SkillRarity.EPIC, 3),
	// ---- LEGENDARY
	SOVEREIGN_REACH("sovereign_reach", "Sovereign's Reach", "+2 block reach, +1.5 entity reach", SkillRarity.LEGENDARY, 1),
	MONARCH_DOMAIN("monarch_domain", "Monarch's Domain", "+10% EXP gain / rank", SkillRarity.LEGENDARY, 3),
	MONARCH_AEGIS("monarch_aegis", "Monarch's Aegis", "+5 armor, +2 toughness / rank", SkillRarity.LEGENDARY, 2),
	// ---- NEW high-tier skills
	VAMPIRIC_TOUCH("vampiric_touch", "Vampiric Touch", "Heal 4% of damage dealt / rank", SkillRarity.EPIC, 3),
	EXECUTIONER("executioner", "Executioner", "+15% damage vs foes under 30% HP / rank", SkillRarity.EPIC, 3),
	STONESKIN("stoneskin", "Stoneskin", "-5% damage taken / rank", SkillRarity.EPIC, 3),
	SECOND_WIND("second_wind", "Second Wind", "Burst regen below 25% HP (cooldown)", SkillRarity.EPIC, 2),
	UNDYING_WILL("undying_will", "Undying Will", "Survive a fatal blow (10 min cooldown)", SkillRarity.LEGENDARY, 1),
	SHADOW_HASTE("shadow_haste", "Shadow Haste", "+15% move speed, +10% attack speed / rank", SkillRarity.LEGENDARY, 2),
	WORLD_BREAKER("world_breaker", "World Breaker", "+25% break speed, +2 efficiency / rank", SkillRarity.LEGENDARY, 2),
	SOVEREIGNS_FORTUNE("sovereigns_fortune", "Sovereign's Fortune", "+50% skill find chance / rank", SkillRarity.LEGENDARY, 2);

	public final String id, displayName, desc;
	public final SkillRarity rarity;
	public final int maxRank;

	PassiveSkill(String id, String displayName, String desc, SkillRarity rarity, int maxRank) {
		this.id = id; this.displayName = displayName; this.desc = desc;
		this.rarity = rarity; this.maxRank = maxRank;
	}

	public String id() { return id; }

	public static PassiveSkill byId(String s) {
		for (PassiveSkill p : values()) if (p.id.equals(s)) return p;
		return null;
	}
}
