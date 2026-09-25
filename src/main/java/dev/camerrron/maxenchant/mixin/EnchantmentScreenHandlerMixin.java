package dev.camerrron.maxenchant.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.camerrron.maxenchant.MaxEnchantMod;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies the target player's own recorded level for an enchant at the table, instead of
 * Penchant's forced level-1 roll - not vanilla max, whatever level they've actually reached
 * on any item so far. If they've never leveled this enchant before, this is a no-op and
 * Penchant's normal level-1-then-grind behavior is untouched.
 *
 * Penchant's own EnchantmentMenuMixin wraps this exact call (ItemStack.addEnchantment,
 * inside method_17410 - unmapped by Yarn, referenced by its intermediary name here just
 * like Penchant's own compiled mixin does) and forces the rolled level down to 1, so the
 * player grinds it back up through use. Confirmed via decompile of Penchant's real class:
 * archives.tater.penchant.mixin.leveling.EnchantmentMenuMixin.levelOne - same class, same
 * method target, same @At target, same (ItemStack, RegistryEntry, int, Operation)
 * signature this mixin mirrors. Not reproduced here, just verified against.
 *
 * Since this mixin is applied TO EnchantmentScreenHandler itself, `this` inside it already
 * IS the open screen handler - no need to capture a player parameter/local separately, just
 * pull the player MaxEnchantMod tracked for this handler (see mixin.PlayerEntityMixin).
 *
 * UNVERIFIED, needs the local integration test described in mods/maxenchant/README.md:
 * whether this mixin actually gets the "final say" over Penchant's forced level=1 depends
 * on Mixin's WrapOperation chaining/priority ordering, not just method-target matching.
 * Penchant's own mixin declares no explicit @Mixin priority, so it runs at Mixin's default
 * of 1000. Lower priority wrappers sit CLOSER to the real vanilla call, so this mixin is
 * deliberately given a lower priority below - meaning Penchant's wrap calls us as its
 * "original", and we can override whatever level it passed us. If the integration test
 * shows the ordering is backwards, raise this mixin's priority above Penchant's instead of
 * lowering it further.
 *
 * Also charges extra XP levels when the recorded level exceeds what was actually rolled -
 * skipping the grind costs more than the normal 1/2/3-level table cost. Confirmed via
 * decompiling the real vanilla server jar (not guessed): EnchantmentScreenHandler's real
 * onButtonClick override (Yarn's own tiny mappings leave it unmapped as method_7604, but
 * Mojang's own official server mappings name it clickMenuButton) already deducts a small
 * fixed player.applyEnchantmentCosts(stack, buttonId+1) cost BEFORE reaching this exact
 * lambda - so our extra charge layers on top of that, inside the same lambda, rather than
 * needing a whole separate hook. Clamped to the player's current XP so it never goes
 * negative; the recorded level still always applies regardless of whether they can afford
 * the extra charge in full - the consistent-level guarantee is the priority, the cost is a
 * tax on top of it, not a hard gate.
 */
@Mixin(value = EnchantmentScreenHandler.class, priority = 500)
public class EnchantmentScreenHandlerMixin {

	private static final Logger MAXENCHANT_DEBUG_LOGGER = LoggerFactory.getLogger("maxenchant-debug");

	@WrapOperation(
			method = "method_17410",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/item/ItemStack;addEnchantment(Lnet/minecraft/registry/entry/RegistryEntry;I)V"
			)
	)
	private void applyRecordedLevel(
			ItemStack stack,
			RegistryEntry<Enchantment> enchantment,
			int rolledLevel,
			Operation<Void> original
	) {
		ServerPlayerEntity player = MaxEnchantMod.getTargetPlayerFor((ScreenHandler) (Object) this);
		MAXENCHANT_DEBUG_LOGGER.info(
				"applyRecordedLevel fired: enchantment={} rolledLevel={} trackedPlayer={}",
				enchantment.getIdAsString(), rolledLevel, player == null ? "null" : player.getGameProfile().getName()
		);
		if (player != null) {
			int recorded = MaxEnchantMod.getRecordedLevel(player, enchantment);
			MAXENCHANT_DEBUG_LOGGER.info("recorded level lookup = {}", recorded);
			if (recorded > 0) {
				int extraCost = recorded - rolledLevel;
				if (extraCost > 0) {
					player.addExperienceLevels(-Math.min(extraCost, player.experienceLevel));
				}
				original.call(stack, enchantment, recorded);
				return;
			}
		}
		original.call(stack, enchantment, rolledLevel);
	}
}
