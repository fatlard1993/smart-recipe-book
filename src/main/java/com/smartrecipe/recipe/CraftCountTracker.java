package com.smartrecipe.recipe;

import net.minecraft.item.Item;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks how many times each item has been crafted for sorting purposes.
 */
public class CraftCountTracker {

	private static final Map<Item, Integer> craftCounts = new ConcurrentHashMap<>();

	/**
	 * Increment the craft count for an item
	 */
	public static void increment(Item item, int amount) {
		craftCounts.merge(item, amount, Integer::sum);
	}

	/**
	 * Get the craft count for an item
	 */
	public static int getCount(Item item) {
		return craftCounts.getOrDefault(item, 0);
	}

	/**
	 * Clear all craft counts
	 */
	public static void clear() {
		craftCounts.clear();
	}
}
