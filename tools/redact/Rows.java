import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/**
 * Report the rows in a y-range that contain text ("ink"), so redaction boxes
 * can be aligned to real pixels instead of eyeballed coordinates.
 *
 * A row counts as ink when at least `minInk` pixels differ appreciably from
 * that row's dominant (background) colour — which works for both the light
 * and dark themes.
 */
public class Rows {
    public static void main(String[] a) throws Exception {
        BufferedImage img = ImageIO.read(new File(a[0]));
        int y0 = Integer.parseInt(a[1]), y1 = Integer.parseInt(a[2]);
        int x0 = a.length > 3 ? Integer.parseInt(a[3]) : 0;
        int x1 = a.length > 4 ? Integer.parseInt(a[4]) : img.getWidth() - 1;
        int minInk = a.length > 5 ? Integer.parseInt(a[5]) : 6;

        List<int[]> bands = new ArrayList<>();
        int start = -1;
        for (int y = y0; y <= y1 && y < img.getHeight(); y++) {
            Map<Integer,Integer> hist = new HashMap<>();
            for (int x = x0; x <= x1 && x < img.getWidth(); x++) {
                int c = img.getRGB(x, y) & 0xFFFFFF;
                hist.merge(c, 1, Integer::sum);
            }
            int bg = Collections.max(hist.entrySet(), Map.Entry.comparingByValue()).getKey();
            int ink = 0;
            for (Map.Entry<Integer,Integer> e : hist.entrySet()) {
                int c = e.getKey();
                int dr = Math.abs(((c>>16)&255) - ((bg>>16)&255));
                int dg = Math.abs(((c>>8)&255) - ((bg>>8)&255));
                int db = Math.abs((c&255) - (bg&255));
                if (dr + dg + db > 60) ink += e.getValue();
            }
            if (ink >= minInk) {
                if (start < 0) start = y;
            } else if (start >= 0) {
                bands.add(new int[]{start, y - 1});
                start = -1;
            }
        }
        if (start >= 0) bands.add(new int[]{start, y1});
        for (int[] b : bands) System.out.printf("  y %4d .. %4d   (h=%d)%n", b[0], b[1], b[1]-b[0]+1);
    }
}
