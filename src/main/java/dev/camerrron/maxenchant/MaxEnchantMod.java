package dev.camerrron.maxenchant;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Keeps ONE player's own current level per enchantment consistent across every item they
 * enchant. Not "max on enchant" - whatever level they've reached anywhere (via Penchant's
 * normal grind, on any item) is the level every future enchant of theirs starts at,
 * regardless of that item's bookshelf-limited offered roll. See mixin.PenchantLevelUpMixin
 * for where a level increase gets recorded, and mixin.PenchantmentMenuMixin (plus the two
 * vanilla-path mixins kept for completeness) for where a recorded level gets applied.
 *
 * Fabric API has no generic "screen handler opened" event - confirmed by searching every
 * one of the 50 nested modules inside fabric-api-0.116.17+1.21.1.jar, nothing matches. So
 * tracking which open ScreenHandlers belong to the target player is done by
 * mixin.ServerPlayerEntityMixin hooking the real vanilla method directly and calling
 * trackIfTarget() below, rather than by a Fabric API convenience callback that doesn't
 * exist.
 */
public final class MaxEnchantMod implements ModInitializer {

	// ccamerron's real UUID, from the live server's ops.json - not a placeholder.
	public static final UUID TARGET_PLAYER = UUID.fromString("b6a8647f-a8da-411e-a5c7-ef20c6fdd532");

	// Maps an open ScreenHandler to the target player if they're the one who opened it - a
	// Map, not a Set, so the apply-mixins can pull the actual player back out and look up
	// their recorded level, not just get a yes/no.
	private static final Map<ScreenHandler, ServerPlayerEntity> TARGET_HANDLERS = new WeakHashMap<>();

	@Override
	public void onInitialize() {
		// Touch the attachment type so it registers at a predictable point, rather than
		// relying on whichever mixin happens to reference it first at runtime.
		Class<?> ignored = MaxEnchantAttachments.LEVELS.getClass();
		MaxEnchantConfig.get();

		// Debug-only: seed/set the target player's recorded levels directly, without
		// needing them to actually grind an enchant up first. Op-only (permission level 2).
		// Genuinely useful for testing the carry-across-items mechanic quickly, kept
		// deliberately rather than stripped out once things worked.
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				literal("maxenchant")
						.requires(source -> source.hasPermissionLevel(2))
						.then(literal("seed").executes(MaxEnchantMod::runSeedCommand))
						.then(literal("set")
								.then(argument("enchantment", StringArgumentType.word())
										.then(argument("level", integer(1))
												.executes(MaxEnchantMod::runSetCommand))))
						.then(literal("reload").executes(MaxEnchantMod::runReloadCommand))
		));
	}

	private static int runSeedCommand(CommandContext<ServerCommandSource> ctx) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			return 0;
		}
		var registry = ctx.getSource().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		RegistryEntry<Enchantment> sharpness = registry.getEntry(Enchantments.SHARPNESS).orElseThrow();
		RegistryEntry<Enchantment> unbreaking = registry.getEntry(Enchantments.UNBREAKING).orElseThrow();
		recordLevelIfHigher(player, sharpness, 3);
		recordLevelIfHigher(player, unbreaking, unbreaking.value().getMaxLevel());
		ctx.getSource().sendFeedback(
				() -> Text.literal("Seeded: Sharpness 3, Unbreaking " + unbreaking.value().getMaxLevel() + " (max)"),
				false
		);
		return 1;
	}

	private static int runSetCommand(CommandContext<ServerCommandSource> ctx) {
		ServerPlayerEntity player = ctx.getSource().getPlayer();
		if (player == null) {
			return 0;
		}
		String id = StringArgumentType.getString(ctx, "enchantment");
		int level = getInteger(ctx, "level");
		var registry = ctx.getSource().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		RegistryKey<Enchantment> key = RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of("minecraft", id));
		var entryOpt = registry.getEntry(key);
		if (entryOpt.isEmpty()) {
			ctx.getSource().sendError(Text.literal("No such enchantment: minecraft:" + id));
			return 0;
		}
		recordLevelIfHigher(player, entryOpt.get(), level);
		ctx.getSource().sendFeedback(() -> Text.literal("Recorded " + id + " " + level), false);
		return 1;
	}

	private static int runReloadCommand(CommandContext<ServerCommandSource> ctx) {
		MaxEnchantConfig.reload();
		ctx.getSource().sendFeedback(() -> Text.literal("maxenchant config reloaded"), false);
		return 1;
	}

	public static void trackIfTarget(ServerPlayerEntity player, ScreenHandler handler) {
		if (player.getUuid().equals(TARGET_PLAYER)) {
			TARGET_HANDLERS.put(handler, player);
		}
	}

	/** The target player if they're the one who opened this handler, else null. */
	public static ServerPlayerEntity getTargetPlayerFor(ScreenHandler handler) {
		return TARGET_HANDLERS.get(handler);
	}

	/** Current recorded level for this enchantment, or 0 if the player has never reached it. */
	public static int getRecordedLevel(ServerPlayerEntity player, RegistryEntry<Enchantment> enchantment) {
		Map<RegistryEntry<Enchantment>, Integer> levels = player.getAttached(MaxEnchantAttachments.LEVELS);
		if (levels == null) {
			return 0;
		}
		return levels.getOrDefault(enchantment, 0);
	}

	/** Records a new level for this enchantment if it's higher than what's already stored. */
	public static void recordLevelIfHigher(ServerPlayerEntity player, RegistryEntry<Enchantment> enchantment, int newLevel) {
		Map<RegistryEntry<Enchantment>, Integer> levels = player.getAttached(MaxEnchantAttachments.LEVELS);
		if (levels == null) {
			levels = new HashMap<>();
			player.setAttached(MaxEnchantAttachments.LEVELS, levels);
		}
		if (newLevel > levels.getOrDefault(enchantment, 0)) {
			levels.put(enchantment, newLevel);
		}
	}
}
