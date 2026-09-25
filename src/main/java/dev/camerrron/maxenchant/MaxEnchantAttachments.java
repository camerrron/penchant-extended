package dev.camerrron.maxenchant;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks each player's own current level per enchantment, independent of any single item.
 * Player-scoped, not item-scoped, so it survives an item breaking - that's the whole point:
 * the level is the player's, not the tool's.
 *
 * ENTRY_CODEC/ENTRY_PACKET_CODEC are the same RegistryEntry<Enchantment> codecs Penchant's
 * own EnchantmentProgress component uses internally (decompiled and confirmed against
 * penchant-0.3.7+mc1.21.1.jar) - not our own invention, just reusing real, already-proven
 * codecs rather than writing new ones.
 *
 * syncWith(...) + targetOnly() means only the owning player's own client receives their
 * level map - needed so mixin.client.EnchantmentScreenClientMixin can show the recorded
 * level in the table's offer preview before the player commits to enchanting.
 */
public final class MaxEnchantAttachments {

	private MaxEnchantAttachments() {
	}

	private static final PacketCodec<RegistryByteBuf, Map<RegistryEntry<Enchantment>, Integer>> STREAM_CODEC =
			PacketCodecs.map(HashMap::new, Enchantment.ENTRY_PACKET_CODEC, PacketCodecs.VAR_INT);

	public static final AttachmentType<Map<RegistryEntry<Enchantment>, Integer>> LEVELS =
			AttachmentRegistry.create(
					Identifier.of("maxenchant", "levels"),
					builder -> builder
							.persistent(Codec.unboundedMap(Enchantment.ENTRY_CODEC, Codec.INT))
							.syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			);
}
