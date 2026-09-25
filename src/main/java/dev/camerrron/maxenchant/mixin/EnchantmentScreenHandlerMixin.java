package dev.camerrron.maxenchant.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.camerrron.maxenchant.MaxEnchantConfig;
import dev.camerrron.maxenchant.MaxEnchantMod;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies the target player's own recorded level for an enchant at vanilla's own
 * EnchantmentScreenHandler, the same way PenchantmentMenuMixin does for Penchant's
 * table_rework replacement screen. Kept for correctness if table_rework is ever disabled,
 * but confirmed on the real server that table_rework IS enabled, which means
 * PenchantmentMenuMixin is the one that actually matters in practice - this mixin's target,
 * ItemStack.addEnchantment via method_17410, is very likely dead code in the real
 * deployment. See PenchantmentMenuMixin's doc comment for the full story of finding that
 * out and MaxEnchantMod's isTarget/isTargetHandler tracking either mixin relies on.
 *
 * Cost is read from MaxEnchantConfig.getApplyCost(enchantId, recorded) - the configured
 * total cost for that level, not a delta from what was actually rolled. Unconfigured
 * enchant/level pairs default to costing exactly the level number.
 */
@Mixin(value = EnchantmentScreenHandler.class, priority = 500)
public class EnchantmentScreenHandlerMixin {

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
		if (player != null) {
			int recorded = MaxEnchantMod.getRecordedLevel(player, enchantment);
			if (recorded > 0) {
				int cost = MaxEnchantConfig.get().getApplyCost(enchantment.getIdAsString(), recorded);
				if (cost > 0) {
					player.addExperienceLevels(-Math.min(cost, player.experienceLevel));
				}
				original.call(stack, enchantment, recorded);
				return;
			}
		}
		original.call(stack, enchantment, rolledLevel);
	}
}
