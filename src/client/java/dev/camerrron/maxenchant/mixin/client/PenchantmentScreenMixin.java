package dev.camerrron.maxenchant.mixin.client;

import archives.tater.penchant.client.gui.screen.PenchantmentScreen;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.camerrron.maxenchant.MaxEnchantAttachments;
import dev.camerrron.maxenchant.MaxEnchantClientConfig;
import dev.camerrron.maxenchant.MaxEnchantMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

/**
 * Fixes the slot's afford-check/color - `hasEnoughXp` (which drives whether the slot is
 * enabled and whether its cost text renders gold or red) is computed HERE, in init(),
 * BEFORE EnchantmentSlotWidget is even constructed - using Penchant's unmodified base
 * PenchantmentHelper.getXpLevelCost(enchantment), confirmed via the real decompiled
 * source (only one call site in this method, in the enchanting - not disenchanting -
 * branch). EnchantmentSlotWidgetMixin's own override of the same call inside the widget's
 * constructor only affects what gets DRAWN, not this boolean, which is why the display
 * numbers changing didn't fix the color being wrong - two different call sites computing
 * the same logical value independently, only one of which was overridden.
 *
 * Same override as PenchantmentMenuMixin's server-side one and
 * EnchantmentSlotWidgetMixin's - all three read from the identical MaxEnchantConfig value,
 * so the deducted cost, the displayed cost, and the afford-check all agree.
 */
@Environment(EnvType.CLIENT)
@Mixin(PenchantmentScreen.class)
public abstract class PenchantmentScreenMixin {

	@ModifyExpressionValue(
			method = "init",
			at = @At(
					value = "INVOKE",
					target = "Larchives/tater/penchant/util/PenchantmentHelper;getXpLevelCost(Lnet/minecraft/registry/entry/RegistryEntry;)I"
			)
	)
	private int applyConfiguredCost(int original, @Local RegistryEntry<Enchantment> enchantment) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || !client.player.getUuid().equals(MaxEnchantMod.TARGET_PLAYER)) {
			return original;
		}
		Map<RegistryEntry<Enchantment>, Integer> levels = client.player.getAttached(MaxEnchantAttachments.LEVELS);
		if (levels == null) {
			return original;
		}
		Integer recorded = levels.get(enchantment);
		if (recorded == null || recorded <= 0) {
			return original;
		}
		return MaxEnchantClientConfig.getApplyCost(enchantment.getIdAsString(), recorded);
	}
}
