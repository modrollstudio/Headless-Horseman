package studio.modroll.horseman.uishots;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Saves the last rendered frame at a fixed 1920x1080. */
final class Camera {

    static final int WIDTH = 1920;
    static final int HEIGHT = 1080;

    private final Path directory;

    Camera(Path directory) {
        this.directory = directory;
    }

    /** Tiling window managers resize the window; forcing the size back every tick keeps the frames 1920x1080. */
    static void forceSize() {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        if (window.getWidth() == WIDTH && window.getHeight() == HEIGHT && window.getScreenWidth() == WIDTH) {
            return;
        }
        for (String field : new String[] {"width", "framebufferWidth"}) {
            Fields.set(Window.class, window, field, WIDTH);
        }
        for (String field : new String[] {"height", "framebufferHeight"}) {
            Fields.set(Window.class, window, field, HEIGHT);
        }
        minecraft.resizeDisplay();
    }

    void save(String name) {
        Path file = directory.resolve(name + ".png");
        try (NativeImage image =
                Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            Files.createDirectories(directory);
            image.writeToFile(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + file, e);
        }
    }
}
