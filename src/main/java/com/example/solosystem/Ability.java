package com.example.solosystem;

/** A sub-ability of a stat that unlocks once the stat reaches {@code need}. */
public record Ability(String name, String desc, int need) {}
