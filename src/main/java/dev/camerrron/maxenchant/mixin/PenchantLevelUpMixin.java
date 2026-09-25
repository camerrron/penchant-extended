package dev.camerrron.maxenchant.mixin;

import archives.tater.penchant.component.EnchantmentProgress;
import com.llamalad7.mixinextras.sugar.Local;
import dev.camerrron.maxenchant.MaxEnchantMod;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records a player's new level whenever Penchant itself levels an enchant up on any item
 * they're holding - this is the "player grinds it up, we remember it" half of the design.
 *
 * Target is EnchantmentProgress.updateEnchantmentsForStack, Penchant's own method (not
 * vanilla), decompiled and confirmed real. Since it's Penchant's own source, not a vanilla
 * class, its method/field names ARE its real names directly - no Yarn-remapping layer
 * applies to another mod's own code the way it did for the vanilla method-name bug caught
 * building the first two mixins.
 *
 * @At("TAIL") only fires on the path that reaches the end of the method - Penchant's real
 * source has an early `return;` right after `if (!EnchantmentProgress.updateEnchantments(...))`,
 * so TAIL naturally only fires when a level actually changed, not on every progress tick.
 * Reads every enchant+level on the resulting builder rather than diffing old vs new -
 * MaxEnchantMod.recordLevelIfHigher() is already a no-op for anything not actually higher,
 * so this is simpler and just as correct.
 */
@Mixin(EnchantmentProgress.class)
public class PenchantLevelUpMixin {

	@Inject(method = "updateEnchantmentsForStack", at = @At("TAIL"))
	private static void recordLevels(
			EnchantmentProgress.Mutable progress,
			ItemEnchantmentsComponent enchantments,
			ItemStack stack,
			LivingEntity user,
			CallbackInfo ci,
			@Local(ordinal = 0) ItemEnchantmentsComponent.Builder newEnchantments
	) {
		if (!(user instanceof ServerPlayerEntity player)) {
			return;
		}
		for (RegistryEntry<Enchantment> enchantment : newEnchantments.getEnchantments()) {
			MaxEnchantMod.recordLevelIfHigher(player, enchantment, newEnchantments.getLevel(enchantment));
		}
	}
}
