package dev.camerrron.maxenchant.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.camerrron.maxenchant.MaxEnchantAttachments;
import dev.camerrron.maxenchant.MaxEnchantMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

/**
 * Shows the target player's own recorded level in the enchanting table's offer preview,
 * instead of Penchant's bare stripped-name display - so what's shown before committing
 * matches what actually gets applied.
 *
 * Penchant's own client mixin (decompiled and confirmed against the real pinned
 * penchant-0.3.7+mc1.21.1.jar: archives.tater.penchant.mixin.client.leveling.
 * EnchantmentScreenMixin.noLevel) wraps this exact same call and, for any leveling-enabled
 * enchant, does NOT call its "original" at all - it just returns a bare name directly.
 * That's a real structural difference from the apply-time mixins: there, Penchant's wrap
 * always delegates downward (just with a forced argument), so being the LOWER-priority
 * wrap still gets us invoked every time. Here Penchant sometimes skips delegating entirely,
 * so a lower-priority wrap of ours could simply never fire. This mixin is deliberately
 * given a HIGHER priority than Penchant's (which has none set, so it defaults to 1000) -
 * we get first look at every call, and for the target player with a recorded level we
 * short-circuit with our own text before Penchant's strip-or-not logic ever runs. For
 * anyone else, or any enchant they haven't leveled, we call through to original exactly
 * as before, so Penchant's normal behavior is untouched.
 *
 * UNVERIFIED like the other mixins' priority reasoning - needs an actual client connect to
 * confirm the offer list really shows "Sharpness III" (or whatever's recorded) instead of
 * the bare "Sharpness" Penchant would otherwise show.
 */
@Environment(EnvType.CLIENT)
@Mixin(value = EnchantmentScreen.class, priority = 2000)
public class EnchantmentScreenClientMixin {

	@WrapOperation(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/enchantment/Enchantment;getName(Lnet/minecraft/registry/entry/RegistryEntry;I)Lnet/minecraft/text/Text;"
			)
	)
	private Text showRecordedLevel(RegistryEntry<Enchantment> enchantment, int level, Operation<Text> original) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null && client.player.getUuid().equals(MaxEnchantMod.TARGET_PLAYER)) {
			Map<RegistryEntry<Enchantment>, Integer> levels = client.player.getAttached(MaxEnchantAttachments.LEVELS);
			if (levels != null) {
				Integer recorded = levels.get(enchantment);
				if (recorded != null && recorded > 0) {
					return original.call(enchantment, recorded);
				}
			}
		}
		return original.call(enchantment, level);
	}
}
