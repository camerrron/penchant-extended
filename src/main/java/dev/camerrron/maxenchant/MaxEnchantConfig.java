package dev.camerrron.maxenchant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * config/maxenchant/config.json - two independently-optional per-enchantment, per-level
 * tables. Neither is required to have every enchantment/level filled in; anything missing
 * falls back to a sane default (see getApplyCost/getGrindCostOverride below) rather than
 * erroring, so the file can start small and grow.
 *
 * "applyCosts": what it costs in XP levels to instantly apply a recorded level to a new
 * item via the carry-over mechanic (PenchantmentMenuMixin) - not the delta from a rolled
 * level like the first version of this mod did, the literal total cost for that level.
 * Not configured for a given enchant/level -> falls back to costing exactly the level
 * number itself (Sharpness 3 costs 3, Sharpness 1 costs 1) - a reasonable default that
 * scales without requiring every enchantment spelled out by hand.
 *
 * "grindCosts": overrides Penchant's OWN formula for how much progress is needed to grind
 * an enchant up from one level to the next (EnchantmentProgress.getMaxProgress, wrapped by
 * PenchantMaxProgressMixin). Keyed by the TARGET level being reached. Not configured for a
 * given enchant/level -> Penchant's original formula is left completely untouched. This is
 * a global balance knob, not scoped to MaxEnchantMod.TARGET_PLAYER - it changes the grind
 * for everyone, same as changing Penchant's own settings would.
 */
public final class MaxEnchantConfig {

	private static final Logger LOGGER = LoggerFactory.getLogger("maxenchant-config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static MaxEnchantConfig instance;

	private final Map<String, Map<Integer, Integer>> applyCosts;
	private final Map<String, Map<Integer, Integer>> grindCosts;

	private MaxEnchantConfig(Map<String, Map<Integer, Integer>> applyCosts, Map<String, Map<Integer, Integer>> grindCosts) {
		this.applyCosts = applyCosts;
		this.grindCosts = grindCosts;
	}

	public static MaxEnchantConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	/** Reloads from disk - useful for testing config changes without restarting the server. */
	public static MaxEnchantConfig reload() {
		instance = load();
		return instance;
	}

	private static MaxEnchantConfig load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("maxenchant").resolve("config.json");
		if (!Files.exists(path)) {
			MaxEnchantConfig defaults = new MaxEnchantConfig(new HashMap<>(), new HashMap<>());
			defaults.writeTo(path);
			return defaults;
		}
		try {
			String json = Files.readString(path, StandardCharsets.UTF_8);
			Raw raw = GSON.fromJson(json, Raw.class);
			if (raw == null) {
				raw = new Raw();
			}
			return new MaxEnchantConfig(
					raw.applyCosts == null ? new HashMap<>() : raw.applyCosts,
					raw.grindCosts == null ? new HashMap<>() : raw.grindCosts
			);
		} catch (IOException | JsonSyntaxException e) {
			LOGGER.error("Failed to read {} - using defaults (nothing configured) until this is fixed", path, e);
			return new MaxEnchantConfig(new HashMap<>(), new HashMap<>());
		}
	}

	private void writeTo(Path path) {
		try {
			Files.createDirectories(path.getParent());
			Raw raw = new Raw();
			raw.applyCosts = this.applyCosts;
			raw.grindCosts = this.grindCosts;
			Files.writeString(path, GSON.toJson(raw), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Failed to write default config to {}", path, e);
		}
	}

	/** XP-level cost to instantly apply this level via the carry-over mechanic. */
	public int getApplyCost(String enchantmentId, int level) {
		Map<Integer, Integer> perLevel = applyCosts.get(enchantmentId);
		if (perLevel != null && perLevel.containsKey(level)) {
			return perLevel.get(level);
		}
		return level;
	}

	/** Configured grind-progress override to reach this level, or null to use Penchant's own formula. */
	public Integer getGrindCostOverride(String enchantmentId, int targetLevel) {
		Map<Integer, Integer> perLevel = grindCosts.get(enchantmentId);
		if (perLevel != null) {
			return perLevel.get(targetLevel);
		}
		return null;
	}

	private static final class Raw {
		Map<String, Map<Integer, Integer>> applyCosts;
		Map<String, Map<Integer, Integer>> grindCosts;
	}
}
