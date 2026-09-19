package com.secret.autoenchanter;

import net.earthcomputer.clientcommands.Configs;

public final class EngineBridge {
    private EngineBridge() {}

    public static void lockBookshelves(int shelves) {
        Configs.setMinEnchantBookshelves(shelves);
        Configs.setMaxEnchantBookshelves(shelves);
    }
}
