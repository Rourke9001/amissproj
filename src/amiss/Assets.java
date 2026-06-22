package amiss;

import java.net.URL;
import javax.swing.ImageIcon;

/**
 * Loads bundled image assets from the classpath so they work both from
 * {@code build/classes} and from inside {@code AmissProj.jar}.
 *
 * <p>Assets live under {@code src/amiss/resources/} (packaged to
 * {@code /amiss/resources/} on the classpath). Replacing the placeholder art is
 * just a matter of dropping new PNGs in there with the same names and rebuilding;
 * see {@code scripts/gen-placeholders.ps1}.
 */
public final class Assets {

    private Assets() {
    }

    /**
     * Returns an {@link ImageIcon} for {@code /amiss/resources/<name>}, or
     * {@code null} if the resource is missing. Passing the result straight to
     * {@link javax.swing.JLabel#setIcon} is safe: a {@code null} icon simply
     * clears it, so a missing asset degrades gracefully instead of throwing.
     *
     * @param name path relative to {@code /amiss/resources/}, e.g. {@code "board.png"}
     *             or {@code "screens/Bank.png"}
     */
    public static ImageIcon icon(String name) {
        URL url = Assets.class.getResource("/amiss/resources/" + name);
        if (url == null) {
            System.err.println("Asset missing on classpath: /amiss/resources/" + name);
            return null;
        }
        return new ImageIcon(url);
    }
}
