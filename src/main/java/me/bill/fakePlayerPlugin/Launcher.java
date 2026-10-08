package me.bill.fakePlayerPlugin;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class Launcher {
    private static final Logger LOGGER = Logger.getLogger(Launcher.class.getName());

    private Launcher() {}

    public static void main(String[] args) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    desktop.browse(new java.net.URI("https://fpp.wtf"));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not open browser.", e);
        }
        System.exit(0);
    }
}
