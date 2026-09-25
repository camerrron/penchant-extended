package dev.camerrron.maxenchant.mixin;

import archives.tater.penchant.menu.PenchantmentMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.camerrron.maxenchant.MaxEnchantConfig;
import dev.camerrron.maxenchant.MaxEnchantMod;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
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
 * with no `this`, so it can't be used to look up which player is enchanting at all.
 * @WrapOperation(method="handleEnchant", ...) found zero matching call sites ("Scanned 0
 * target(s)") - Mixin's lambda-following didn't reach two levels deep, only one.
 *
 * Fixed by wrapping one level higher instead: lambda$handleEnchant$0 IS an instance method
 * (has `this` = the PenchantmentMenu), and its own body is where the Consumer lambda gets
 * constructed and passed to PenchantmentHelper.updateEnchantments. Wrapping THAT call lets
 * this decorate the Consumer itself - run Penchant's original (sets level 1) first, then
 * immediately re-set the same enchantment to the recorded level if the target player has
 * one. `enchantment` is captured via @Local since it's a parameter of the enclosing lambda
 * method, not part of the wrapped call's own signature.
 *
 * Cost is read from MaxEnchantConfig.getApplyCost(enchantId, recorded) - the configured
 * total cost for that level, not a delta from the level Penchant would have rolled (always
 * 1 here). Unconfigured enchant/level pairs default to costing exactly the level number.
 */
@Mixin(value = PenchantmentMenu.class, priority = 500)
public class PenchantmentMenuMixin {

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
		int cost = MaxEnchantConfig.get().getApplyCost(enchantment.getIdAsString(), recorded);
		if (cost > 0) {
			player.addExperienceLevels(-Math.min(cost, player.experienceLevel));
		}
		Consumer<ItemEnchantmentsComponent.Builder> decorated = builder -> {
			originalConsumer.accept(builder);
			builder.set(enchantment, recorded);
		};
		return original.call(stack, decorated);
	}
}
