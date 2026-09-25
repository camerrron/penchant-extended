package dev.camerrron.maxenchant;

import net.fabricmc.api.ClientModInitializer;

/**
 * No client-side setup needed beyond what mixin.client.EnchantmentScreenClientMixin does on
 * its own - this class exists because fabric.mod.json declares a client entrypoint, and
 * Fabric Loader requires one to actually exist at that class name.
 */
public final class MaxEnchantClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// No-op.
	}
}
