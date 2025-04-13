package com.voltmaister.utils;

import net.runelite.client.util.ImageUtil;

import java.awt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.image.BufferedImage;

public class LoadIcon {
    private static final Logger log = LoggerFactory.getLogger(LoadIcon.class);

    public static BufferedImage loadIcon() { // made static and public
        try {
            return ImageUtil.loadImageResource(LoadIcon.class, "/Collection_log.png");
        } catch (Exception e) {
            log.warn("Using fallback icon", e);
            BufferedImage fallback = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = fallback.createGraphics();
            g.setColor(Color.RED);
            g.fillRect(0, 0, 16, 16);
            g.dispose();
            return fallback;
        }
    }
}
