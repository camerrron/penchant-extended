package dev.camerrron.maxenchant.mixin;

import archives.tater.penchant.menu.PenchantmentMenu;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.camerrron.maxenchant.MaxEnchantConfig;
import dev.camerrron.maxenchant.MaxEnchantMod;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/**
 * REAL BUG, found via live testing after fixing the two ServerPlayerEntity mixin bugs:
 * with penchant:table_rework enabled (confirmed enabled on BOTH this local test server AND
 * the real production server.properties - never a local-only quirk), the enchanting table
 * isn't vanilla's EnchantmentScreenHandler. Penchant ships a fully independent menu class,
 * PenchantmentMenu, driven by a client network payload instead of vanilla's button-click
 * flow. EnchantmentScreenHandlerMixin's target - ItemStack.addEnchantment - is never called
 * by this path.
 *
 * SECOND real bug, found immediately after: the actual level-applying call -
 * ItemEnchantmentsComponent.Builder.set(enchantment, 1) - is not in handleEnchant's own
 * body at all. Decompiling penchant-0.3.7+mc1.21.1.jar WITHOUT lambda-inlining (CFR's
 * --decodelambdas false) to see the real compiled structure showed it's two lambdas deep:
 * handleEnchant calls access.execute(lambda$handleEnchant$0), which itself calls
 * PenchantmentHelper.updateEnchantments(stack, lambda$handleEnchant$1), and Builder.set is
 * only inside that innermost lambda$handleEnchant$1 - a private STATIC synthetic method
 * with no `this`. Fixed by wrapping one level higher: lambda$handleEnchant$0 IS an instance
 * method (has `this` = the PenchantmentMenu), so this decorates the Consumer it passes to
 * PenchantmentHelper.updateEnchantments instead.
 *
 * THIRD real bug, found after live-testing the cost/display: cost used to be charged as an
 * ADD-ON here (recorded - rolled, deducted separately from Penchant's own
 * applyEnchantmentCosts call) - correct arithmetic (base 1 + configured 10 = 11 actually
 * deducted) but invisible anywhere in the UI, since nothing displayed showed that combined
 * total. Fixed by moving the override upstream instead: handleEnchant's own
 * PenchantmentHelper.getXpLevelCost(enchantment) call (BEFORE the lambda even exists, in
 * handleEnchant's own body where `this.player` is directly in scope) is overridden to
 * return the configured total directly, so Penchant's own applyEnchantmentCosts call uses
 * the right number itself - no separate bolt-on deduction needed here anymore. The client
 * side has its own matching override in PenchantmentScreenMixin +
 * EnchantmentSlotWidgetMixin, at the exact call sites that build what's displayed, so both
 * sides compute from the same config value instead of two independently-drifting numbers.
 */
@Mixin(value = PenchantmentMenu.class, priority = 500)
public class PenchantmentMenuMixin {

	@Shadow
	@Final
	private PlayerEntity player;

	@ModifyExpressionValue(
			method = "handleEnchant",
			at = @At(
					value = "INVOKE",
					target = "Larchives/tater/penchant/util/PenchantmentHelper;getXpLevelCost(Lnet/minecraft/registry/entry/RegistryEntry;)I"
			)
	)
	private int applyConfiguredCost(int original, @Local(argsOnly = true) RegistryEntry<Enchantment> enchantment) {
		if (!(this.player instanceof ServerPlayerEntity serverPlayer) || !serverPlayer.getUuid().equals(MaxEnchantMod.TARGET_PLAYER)) {
			return original;
		}
		int recorded = MaxEnchantMod.getRecordedLevel(serverPlayer, enchantment);
		if (recorded <= 0) {
			return original;
		}
		return MaxEnchantConfig.get().getApplyCost(enchantment.getIdAsString(), recorded);
	}

	@WrapOperation(
			method = "lambda$handleEnchant$0",
			at = @At(
					value = "INVOKE",
					target = "Larchives/tater/penchant/util/PenchantmentHelper;updateEnchantments(Lnet/minecraft/item/ItemStack;Ljava/util/function/Consumer;)Lnet/minecraft/item/ItemStack;"
			)
	)
	private ItemStack applyRecordedLevel(
			ItemStack stack,
			Consumer<ItemEnchantmentsComponent.Builder> originalConsumer,
			Operation<ItemStack> original,
			@Local(argsOnly = true) RegistryEntry<Enchantment> enchantment
	) {
		ServerPlayerEntity player = MaxEnchantMod.getTargetPlayerFor((ScreenHandler) (Object) this);
		if (player == null) {
			return original.call(stack, originalConsumer);
		}
		int recorded = MaxEnchantMod.getRecordedLevel(player, enchantment);
		if (recorded <= 0) {
			return original.call(stack, originalConsumer);
		}
		Consumer<ItemEnchantmentsComponent.Builder> decorated = builder -> {
			originalConsumer.accept(builder);
			builder.set(enchantment, recorded);
		};
		return original.call(stack, decorated);
	}
}
