package dev.camerrron.maxenchant.mixin.client;

import archives.tater.penchant.client.gui.widget.EnchantmentSlotWidget;
import com.llamalad7.mixinextras.sugar.Local;
import dev.camerrron.maxenchant.MaxEnchantAttachments;
import dev.camerrron.maxenchant.MaxEnchantClientConfig;
import dev.camerrron.maxenchant.MaxEnchantMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

/**
 * REAL BUG, found by actually looking at the table: Penchant's table_rework offer list
 * doesn't show a level number for ANY enchant, ever - confirmed by decompiling the real
 * widget class, its label comes straight from Enchantment.description() with no level
 * argument. This isn't a wrong-target bug like the server-side ones, it's a genuinely new
 * UI element.
 *
 * SECOND bug in the first attempt at fixing this: replacing `costText` wholesale with a
 * plain gold number dropped Penchant's own book-cost figure and its hasEnoughXp/hasEnough
 * Books-driven coloring entirely - the real widget joins book cost + xp cost together and
 * colors each independently (gold/red per whether the player can afford each one).
 * Overriding the SOURCE value (`xpCost`, the local Penchant computes once and reuses for
 * both costText and the hover tooltip) instead of patching each downstream text field
 * separately means Penchant's own existing join/color logic keeps working correctly and
 * automatically reflects the right number everywhere it's used - no separate tooltip fix
 * needed either.
 *
 * THIRD bug behind the "took 11, showed something else" report: the color/afford check
 * (`hasEnoughXp`) is computed in a DIFFERENT method entirely - PenchantmentScreen.init(),
 * before this widget is even constructed - using Penchant's unmodified base cost. See
 * PenchantmentScreenMixin for that half of the fix; this class only affects what's DRAWN,
 * not whether the slot is enabled/colored as affordable.
 *
 * Roman numerals via Text.translatable("enchantment.level." + N), the same pattern vanilla
 * Enchantment.getName(entry, level) itself uses (confirmed via decompiling the real
 * vanilla server jar) - not a hand-rolled roman-numeral converter.
 */
@Environment(EnvType.CLIENT)
@Mixin(EnchantmentSlotWidget.class)
public abstract class EnchantmentSlotWidgetMixin {

	@Shadow
	@Final
	private RegistryEntry<Enchantment> enchantment;

	@Shadow
	@Final
	@Mutable
	private Text text;

	@ModifyVariable(
			method = "<init>(IILnet/minecraft/registry/entry/RegistryEntry;Ljava/util/List;ZZZZZZZZ)V",
			at = @At("STORE"),
			name = "xpCost"
	)
	private int applyConfiguredCost(int xpCost, @Local(argsOnly = true) RegistryEntry<Enchantment> enchantment) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || !client.player.getUuid().equals(MaxEnchantMod.TARGET_PLAYER)) {
			return xpCost;
		}
		Map<RegistryEntry<Enchantment>, Integer> levels = client.player.getAttached(MaxEnchantAttachments.LEVELS);
		if (levels == null) {
			return xpCost;
		}
		Integer recorded = levels.get(enchantment);
		if (recorded == null || recorded <= 0) {
			return xpCost;
		}
		return MaxEnchantClientConfig.getApplyCost(enchantment.getIdAsString(), recorded);
	}

	@Inject(
			method = "<init>(IILnet/minecraft/registry/entry/RegistryEntry;Ljava/util/List;ZZZZZZZZ)V",
			at = @At("TAIL")
	)
	private void showRecordedLevel(
			int x, int y, RegistryEntry<Enchantment> enchantment, List<RegistryEntry<Enchantment>> incompatible,
			boolean remove, boolean showXpCost, boolean showBookCost, boolean canUse,
			boolean hasIngredient, boolean hasEnoughBooks, boolean hasEnoughXp, boolean isUnlocked,
			CallbackInfo ci
	) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || !client.player.getUuid().equals(MaxEnchantMod.TARGET_PLAYER)) {
			return;
		}
		Map<RegistryEntry<Enchantment>, Integer> levels = client.player.getAttached(MaxEnchantAttachments.LEVELS);
		if (levels == null) {
			return;
		}
		Integer recorded = levels.get(this.enchantment);
		if (recorded == null || recorded <= 0 || this.text == null) {
			return;
		}
		this.text = this.text.copy()
				.append(Text.literal(" "))
				.append(Text.translatable("enchantment.level." + recorded));
	}
}
