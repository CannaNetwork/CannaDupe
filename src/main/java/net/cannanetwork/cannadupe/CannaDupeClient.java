package net.cannanetwork.cannadupe;

import net.fabricmc.api.ClientModInitializer;

public final class CannaDupeClient implements ClientModInitializer {
    public static final CannaDupeController CONTROLLER = new CannaDupeController();
    @Override public void onInitializeClient() { UpdateChecker.checkOnce(); }
}
