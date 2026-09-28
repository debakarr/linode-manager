import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Redact rectangular regions of the documentation screenshots.
 *
 * Reads a spec file of "image x y w h" lines (one region per line, '#' for
 * comments) and fills each region with an opaque neutral box, in place.
 *
 * Deliberately dumb: no OCR, no heuristics. The regions are chosen by looking
 * at the screenshots, so that nothing sensitive is left to a guess.
 *
 * Usage:  java Redact <spec-file> [--check]
 *         --check only reports which areas would change (no writes).
 */
public final class Redact {

    private static final Color BOX = new Color(0x38, 0x3D, 0x43);   // neutral slate
    private static final Color EDGE = new Color(0x4A, 0x50, 0x57);

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: java Redact <spec-file> [--check]");
            System.exit(2);
        }
        boolean check = args.length > 1 && args[1].equals("--check");
        Path spec = Path.of(args[0]);
        List<String> lines = Files.readAllLines(spec);

        String current = null;
        BufferedImage img = null;
        int applied = 0, skipped = 0;
        List<String> touched = new ArrayList<>();

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\s+");
            if (p.length == 1) {
                flush(img, current, check);
                current = p[0];
                img = ImageIO.read(new File(current));
                if (img == null) throw new IllegalStateException("not an image: " + current);
                touched.add(current);
                continue;
            }
            if (p.length != 4) throw new IllegalArgumentException("bad spec line: " + line);
            if (img == null) throw new IllegalStateException("region before any image name");
            int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]);
            int w = Integer.parseInt(p[2]), h = Integer.parseInt(p[3]);
            if (x < 0 || y < 0 || x + w > img.getWidth() || y + h > img.getHeight()) {
                System.err.printf("  !! out of bounds %s +(%d,%d %dx%d) image %dx%d%n",
                        current, x, y, w, h, img.getWidth(), img.getHeight());
                skipped++;
                continue;
            }
            Graphics2D g = img.createGraphics();
            g.setColor(BOX);
            g.fillRect(x, y, w, h);
            g.setColor(EDGE);
            g.drawRect(x, y, w, h);
            g.dispose();
            applied++;
        }
        flush(img, current, check);

        System.out.printf("%d regions applied, %d skipped, %d images%n", applied, skipped, touched.size());
        if (check) System.out.println("(check mode: nothing written)");
    }

    private static void flush(BufferedImage img, String path, boolean check) throws Exception {
        if (img == null || path == null) return;
        if (!check) ImageIO.write(img, "png", new File(path));
        else System.out.println("  would write " + path);
    }
}
