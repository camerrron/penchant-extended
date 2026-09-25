package dev.camerrron.maxenchant.mixin;

import archives.tater.penchant.component.EnchantmentProgress;
import dev.camerrron.maxenchant.MaxEnchantConfig;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides Penchant's own formula for how much progress is needed to grind an enchant up
 * from one level to the next, when config/config.json's "grindCosts" has an entry for that
 * enchantment + target level (see MaxEnchantConfig). Global - affects everyone's grind for
 * that enchant/level, not scoped to MaxEnchantMod.TARGET_PLAYER, matching the intent of a
 * balance knob rather than a personal perk.
 *
 * getMaxProgress(enchantment, currentLevel, maxDurability) is Penchant's own static method
 * (confirmed real via decompile of penchant-0.3.7+mc1.21.1.jar, same as
 * PenchantLevelUpMixin's target in this same class), called with currentLevel = the level
 * BEFORE the one being reached - so the config is keyed by currentLevel + 1, the actual
 * target level, for a more intuitive "cost to reach level N" reading.
 *
 * Simple @Inject + cancel rather than @WrapOperation, since Penchant's method body is a
 * single direct computation with nothing else worth preserving when overridden - if
 * unconfigured, nothing is cancelled and Penchant's original formula runs untouched.
 */
@Mixin(EnchantmentProgress.class)
public class PenchantMaxProgressMixin {

	@Inject(method = "getMaxProgress", at = @At("HEAD"), cancellable = true)
	private static void applyConfiguredGrindCost(
			RegistryEntry<Enchantment> enchantment,
			int currentLevel,
			int maxDurability,
			CallbackInfoReturnable<Integer> cir
	) {
		Integer override = MaxEnchantConfig.get().getGrindCostOverride(enchantment.getIdAsString(), currentLevel + 1);
		if (override != null) {
			cir.setReturnValue(override);
		}
	}
}
