package dev.camerrron.maxenchant;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Reads the applyCosts table the server pushed via MaxEnchantAttachments.CONFIG_JSON -
 * NOT the client's own local config/maxenchant/config.json, which is a separate file on a
 * separate machine (confirmed empty on the local test client instance, Instances/
 * maxenchant-test/config/maxenchant/config.json) and has no reason to match the server's.
 */
public final class MaxEnchantClientConfig {

	private static final Gson GSON = new Gson();
	private static final Type APPLY_COSTS_TYPE = new TypeToken<Map<String, Map<Integer, Integer>>>() {}.getType();

	private MaxEnchantClientConfig() {
	}

	/** Same fallback as MaxEnchantConfig.getApplyCost - unconfigured falls back to the level itself. */
	public static int getApplyCost(String enchantmentId, int level) {
		String json = MinecraftClient.getInstance().player == null
				? null
				: MinecraftClient.getInstance().player.getAttached(MaxEnchantAttachments.CONFIG_JSON);
		if (json == null) {
			return level;
		}
		Map<String, Map<Integer, Integer>> applyCosts = GSON.fromJson(json, APPLY_COSTS_TYPE);
		if (applyCosts == null) {
			return level;
		}
		Map<Integer, Integer> perLevel = applyCosts.get(enchantmentId);
		if (perLevel != null && perLevel.containsKey(level)) {
			return perLevel.get(level);
		}
		return level;
	}
}
