package com.smartrecipe.recipe;

import net.minecraft.item.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks how many times each item has been crafted this session for sorting purposes.
 * Runs on the client thread only — not thread-safe.
 */
public class CraftCountTracker {

	private static final Map<Item, Integer> craftCounts = new HashMap<>();

	public static void increment(Item item, int amount) {
		craftCounts.merge(item, amount, Integer::sum);
	}

	public static int getCount(Item item) {
		return craftCounts.getOrDefault(item, 0);
	}

	public static void clear() {
		craftCounts.clear();
	}
}
