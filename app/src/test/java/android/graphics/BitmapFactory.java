package android.graphics;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStream;

/** Header-only JVM image adapter. A pixel decode is an error in the PDF assembly tests. */
public final class BitmapFactory {
    public static final class Options {
        public boolean inJustDecodeBounds;
        public int outWidth = -1;
        public int outHeight = -1;
        public Bitmap.Config inPreferredConfig;
    }

    public static Bitmap decodeFile(String path, Options options) {
        try (InputStream input = new FileInputStream(path)) { return decodeStream(input, null, options); }
        catch (java.io.IOException error) { return null; }
    }

    public static Bitmap decodeStream(InputStream input, Rect padding, Options options) {
        if (!options.inJustDecodeBounds) throw new AssertionError("PDF must not decode pixels");
        try {
            DataInputStream data = new DataInputStream(input);
            if (data.readUnsignedShort() != 0xffd8) return null;
            while (true) {
                if (data.readUnsignedByte() != 0xff) return null;
                int marker;
                do { marker = data.readUnsignedByte(); } while (marker == 0xff);
                int length = data.readUnsignedShort();
                if (marker == 0xc0 || marker == 0xc2) {
                    data.readUnsignedByte();
                    options.outHeight = data.readUnsignedShort();
                    options.outWidth = data.readUnsignedShort();
                    return null;
                }
                if (length < 2) return null;
                for (int left = length - 2; left > 0; left--) data.readUnsignedByte();
            }
        } catch (java.io.IOException error) {
            options.outWidth = options.outHeight = -1;
        }
        return null;
    }
}
