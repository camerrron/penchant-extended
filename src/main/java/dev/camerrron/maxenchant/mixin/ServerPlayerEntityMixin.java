package dev.camerrron.maxenchant.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.camerrron.maxenchant.MaxEnchantMod;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

/**
 * Feeds MaxEnchantMod's target-player tracking. Fabric API has no "screen handler opened"
 * event (see MaxEnchantMod's doc comment for how that was confirmed), so this hooks the
 * real vanilla method that opens one directly.
 *
 * REAL BUG #1, found via live testing: this mixin originally targeted PlayerEntity (the
 * openHandledScreen method's declaring class), which never fired - decompiling the real
 * vanilla server jar showed ServerPlayerEntity has its OWN full override of
 * openHandledScreen (does not call super at all, sets currentScreenHandler itself), so
 * PlayerEntity's implementation is dead code for every actual player on a dedicated
 * server. A clean boot with no Mixin apply failure never caught this - targeting
 * PlayerEntity.openHandledScreen is completely legitimate, Mixin has no way to know the
 * override makes it unreachable. Only an unconditional debug log inside the injected code,
 * confirmed to never fire despite the player visibly opening and using the real screen,
 * proved it.
 *
 * REAL BUG #2, found immediately after fixing #1: retargeting to ServerPlayerEntity and
 * @Shadow-ing currentScreenHandler (declared on the PARENT PlayerEntity, not
 * ServerPlayerEntity itself) crashed the server outright at mixin-apply time - "@Shadow
 * field field_7512 was not located in the target class net.minecraft.class_3222". Mixin's
 * @Shadow does not resolve inherited fields across the class boundary by default. Fixed by
 * not shadowing the field at all - the decompiled override assigns a local variable to
 * that field right before returning, so @Local captures that same value directly instead.
 *
 * The real override has 3 return statements: two early-out empty-returns (null factory,
 * null menu) before the ScreenHandler local is even declared, and the real success path
 * at the end where it's declared and assigned. @Local can only resolve a local that's
 * actually in scope at the injection point, so this targets ordinal=2 (the third/final
 * RETURN specifically) rather than every RETURN in the method - matches the only case
 * that actually matters anyway, since the early-outs mean no real screen opened.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

	@Inject(method = "openHandledScreen", at = @At(value = "RETURN", ordinal = 2))
	private void trackOpenedHandler(
			NamedScreenHandlerFactory factory,
			CallbackInfoReturnable<OptionalInt> cir,
			@Local ScreenHandler openedHandler
	) {
		MaxEnchantMod.trackIfTarget((ServerPlayerEntity) (Object) this, openedHandler);
	}
}
