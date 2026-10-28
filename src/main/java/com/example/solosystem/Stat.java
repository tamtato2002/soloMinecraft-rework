package com.example.solosystem;

public enum Stat {
	STRENGTH("strength", "Strength", "Close combat & breaking things", new Ability[]{
		new Ability("Physical Damage", "+0.3 melee damage / point (all weapons & fists)", 0),
		new Ability("Block Break Speed", "Break blocks faster, even bare-handed", 20),
		new Ability("Knockback Resistance", "Hard to push around when hit", 45),
		new Ability("Armor Piercing", "Your hits and arrows ignore mob armor and shields", 55),
		new Ability("Plunder", "Slain mobs drop ALL gear and items, durability kept", 75)}),
	AGILITY("agility", "Agility", "Speed, dodging and parkour", new Ability[]{
		new Ability("Movement & Sprint Speed", "Faster walking, sprinting and swimming", 0),
		new Ability("Jump Height & Parkour", "Jump higher (fences, 2-block walls)", 15),
		new Ability("Dodge Chance", "% chance to dodge attacks: MISS!", 35),
		new Ability("Fall Damage Reduction", "Land softly from great heights", 60)}),
	ENDURANCE("endurance", "Endurance", "Toughness, health and stamina", new Ability[]{
		new Ability("Max HP & Regeneration", "+1 max HP / point, faster natural healing", 0),
		new Ability("Hunger Efficiency", "Hunger drains much slower", 15),
		new Ability("Armor & Toughness Bonus", "Natural defense without armor", 35),
		new Ability("Environmental Resistance", "Longer breath, resist fire / lava / poison", 60)}),
	INTELLIGENCE("intelligence", "Intelligence", "Knowledge and enchanting", new Ability[]{
		new Ability("XP Gain Multiplier", "+1% EXP gained / point", 0),
		new Ability("Enchanting & Crafting Luck", "Crafted gear may be Masterwork / Legendary", 20),
		new Ability("Dominion", "Attacker may turn ally for a while, then dies (cooldown)", 40),
		new Ability("Mirror Ward", "Reflects spells, poisons & effects back at the attacker", 60)}),
	SENSE("sense", "Sense", "Crits, detection and awareness", new Ability[]{
		new Ability("Critical Hit", "Crit chance and crit damage", 0),
		new Ability("Sixth Sense", "Pickaxe: see all ores. Weapon: see enemies, names & HP", 20),
		new Ability("Dungeon & Secret Perception", "Sixth Sense also reveals chests, traps, spawners", 40),
		new Ability("Dodge Awareness", "Better chance to dodge arrows", 60)});

	public final String id, label, blurb;
	public final Ability[] abilities;

	Stat(String id, String label, String blurb, Ability[] abilities) {
		this.id = id; this.label = label; this.blurb = blurb; this.abilities = abilities;
	}

	public String id() { return id; }

	public static Stat byId(String s) {
		for (Stat st : values()) if (st.id.equals(s)) return st;
		return null;
	}
}
