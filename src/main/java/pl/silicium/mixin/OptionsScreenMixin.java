//#if !SODIUM_CONFIG_API
package pl.silicium.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
//#if MC >= 1.21
import net.minecraft.client.gui.screens.options.OptionsScreen;
//#else
import net.minecraft.client.gui.screens.OptionsScreen;
//#endif
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.silicium.compat.SiliciumConfigScreen;

/** "Silicium..." button in the top-left corner of the Options menu (versions without the Sodium tab). */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
	protected OptionsScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void silicium$addButton(CallbackInfo ci) {
		Screen self = this;
		addRenderableWidget(Button.builder(Component.literal("Silicium..."),
				b -> Minecraft.getInstance().setScreen(new SiliciumConfigScreen(self))).bounds(5, 5, 80, 20).build());
	}
}
//#endif
