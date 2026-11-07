package pl.silicium;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * Developer self-test for Minecraft versions without Fabric's client gametest API.
 * Enabled only with -Dsilicium.selftest=true (Gradle task runSelfTest): once the title screen is up,
 * forces every mixin target class to load (Mixin audit - fails if any injection doesn't match the game),
 * logs the result and closes the game.
 */
final class SelfTest {
	private static int ticks;
	//#if !SODIUM_CONFIG_API
	private static pl.silicium.compat.SiliciumConfigScreen screen;
	//#endif

	private SelfTest() {
	}

	static void registerIfEnabled() {
		if (!Boolean.getBoolean("silicium.selftest")) return;
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			ticks++;
			try {
				if (ticks == 40) {
					MixinEnvironment.getCurrentEnvironment().audit();
					//#if !SODIUM_CONFIG_API
					screen = new pl.silicium.compat.SiliciumConfigScreen(null);
					mc.setScreen(screen);
					//#endif
				}
				//#if !SODIUM_CONFIG_API
				// render every settings page for a few frames (catches GUI API problems in this version)
				if (ticks > 40 && ticks < 40 + 5 * screen.pageCount() && (ticks - 40) % 5 == 0) {
					screen.showPage((ticks - 40) / 5);
				}
				if (ticks < 40 + 5 * screen.pageCount() + 5) return;
				//#else
				if (ticks < 40) return;
				//#endif
				Silicium.LOGGER.info("SILICIUM SELF-TEST PASSED");
			} catch (Throwable t) {
				Silicium.LOGGER.error("SILICIUM SELF-TEST FAILED", t);
			}
			mc.stop();
		});
	}
}
