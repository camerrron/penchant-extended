package dev.camerrron.maxenchant.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.camerrron.maxenchant.MaxEnchantMod;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies the target player's own recorded level for an enchant at the anvil, instead of
 * Penchant's normal progress-summing logic - mirrors EnchantmentScreenHandlerMixin's
 * approach exactly (recorded level if they have one, untouched Penchant behavior if not).
 *
 * Target confirmed via decompile of Penchant's real class:
 * archives.tater.penchant.mixin.leveling.AnvilMenuMixin.sumProgress - it wraps this exact
 * call (ItemEnchantmentsComponent.Builder.set, inside AnvilScreenHandler's updateResult(),
 * i.e. method_24928 - AnvilScreenHandler inherits it from ForgingScreenHandler, confirmed
 * against the Yarn mappings directly rather than assumed) to decide how progress carries
 * over between the input item and the sacrificed book/item. That's the actual "level gets
 * written" moment at the anvil, same role as ItemStack.addEnchantment at the table.
 *
 * Penchant's updateResult() has no player parameter and doesn't capture one via @Local
 * either (confirmed - none of its three WrapOperations/one @Inject in that method touch a
 * player), which is exactly why MaxEnchantMod tracks target-player screen handlers via
 * mixin.PlayerEntityMixin hooking PlayerEntity.openHandledScreen directly, instead of
 * trying to pull a player out of anvil internals.
 *
 * Method target is "updateResult" (Yarn's real name), not the raw "method_24928" this
 * mixin's target was first written with - the first build attempt against real Minecraft
 * caught this: since this project compiles against NAMED (Yarn) mappings, Mixin's method
 * string needs to be in that same NAMED form whenever one exists, or its remapper can't
 * find it ("Cannot remap ... because it does not exist in any of the targets"). The
 * decompiled Penchant bytecode this mixin was checked against shows the raw intermediary
 * form only because that's what Mixin's own build-time remapping leaves in the SHIPPED
 * jar - not what Penchant's own source actually contains.
 *
 * Same UNVERIFIED priority-ordering caveat as the table mixin - confirm in the local
 * integration test before trusting this against Penchant's own sumProgress wrap on the
 * identical call.
 */
@Mixin(value = AnvilScreenHandler.class, priority = 500)
public class AnvilScreenHandlerMixin {

	@WrapOperation(
			method = "updateResult",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/component/type/ItemEnchantmentsComponent$Builder;set(Lnet/minecraft/registry/entry/RegistryEntry;I)V"
			)
	)
	private void applyRecordedLevel(
			ItemEnchantmentsComponent.Builder builder,
			RegistryEntry<Enchantment> enchantment,
			int level,
			Operation<Void> original
	) {
		ServerPlayerEntity player = MaxEnchantMod.getTargetPlayerFor((ScreenHandler) (Object) this);
		if (player != null) {
			int recorded = MaxEnchantMod.getRecordedLevel(player, enchantment);
			if (recorded > 0) {
				original.call(builder, enchantment, recorded);
				return;
			}
		}
		original.call(builder, enchantment, level);
	}
}
