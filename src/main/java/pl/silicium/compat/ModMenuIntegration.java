//#if !SODIUM_CONFIG_API
package pl.silicium.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu "Configure" button opens the Silicium settings screen. Only loaded when Mod Menu is installed. */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SiliciumConfigScreen::new;
	}
}
//#endif
