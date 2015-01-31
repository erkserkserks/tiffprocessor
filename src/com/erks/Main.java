package com.erks;
        import java.awt.*;
        import java.awt.image.*;
        import java.io.File;
        import javax.media.jai.*;
        import javax.swing.JFrame;
        import javax.swing.JLabel;
        import javax.swing.ImageIcon;

// Represents one pixel with 16-bit r,g,b components. Each subpixel is actually represented a signed 32-bit integer,
// since there is no unsigned 16-bit Java type.
class Pixel16Bit {
    public final int r;
    public final int g;
    public final int b;
    public Pixel16Bit(int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }
}

class TiffProcessor {

        static final int numSubpixelLevels = 2 << 16;
        static final int numSubpixels = 3;

        private static void printInfo(final PlanarImage pi) {
            // Show the image dimensions and coordinates.
            System.out.print("Dimensions: ");
            System.out.print(pi.getWidth() + "x" + pi.getHeight() + " pixels");
            // Remember getMaxX and getMaxY return the coordinate of the next point!
            System.out.println(" (from " + pi.getMinX() + "," + pi.getMinY() + " to " +
                    (pi.getMaxX() - 1)+ "," +(pi.getMaxY() - 1) +")");
            if ((pi.getNumXTiles() != 1)||(pi.getNumYTiles() != 1)) // Is it tiled?
            {
                // Tiles number, dimensions and coordinates.
                System.out.print("Tiles: ");
                System.out.print(pi.getTileWidth()+"x"+pi.getTileHeight()+" pixels"+
                        " ("+pi.getNumXTiles()+"x"+pi.getNumYTiles()+" tiles)");
                System.out.print(" (from "+pi.getMinTileX()+","+pi.getMinTileY()+
                        " to "+pi.getMaxTileX()+","+pi.getMaxTileY()+")");
                System.out.println(" offset: "+pi.getTileGridXOffset()+","+
                        pi.getTileGridXOffset());
            }
            // Display info about the SampleModel of the image.
            SampleModel sm = pi.getSampleModel();
            System.out.println("Number of bands: "+sm.getNumBands());
            System.out.print("Data type: ");
            switch(sm.getDataType()) {
                case DataBuffer.TYPE_BYTE:
                    System.out.println("byte"); break;
                case DataBuffer.TYPE_SHORT:
                    System.out.println("short"); break;
                case DataBuffer.TYPE_USHORT:
                    System.out.println("ushort"); break;
                case DataBuffer.TYPE_INT:
                    System.out.println("int"); break;
                case DataBuffer.TYPE_FLOAT:
                    System.out.println("float"); break;
                case DataBuffer.TYPE_DOUBLE:
                    System.out.println("double"); break;
                case DataBuffer.TYPE_UNDEFINED:
                    System.out.println("undefined"); break;
            }
            // Display info about the ColorModel of the image.
            ColorModel cm = pi.getColorModel();
            if (cm != null)
            {
                System.out.println("Number of color components: "+ cm.getNumComponents());
                System.out.println("Bits per pixel: "+cm.getPixelSize());
                System.out.print("Transparency: ");
                switch(cm.getTransparency())
                {
                    case Transparency.OPAQUE:
                        System.out.println("opaque"); break;
                    case Transparency.BITMASK:
                        System.out.println("bitmask"); break;
                    case Transparency.TRANSLUCENT:
                        System.out.println("translucent"); break;
                }
            }
            else
                System.out.println("No color model.");
        }

        private static Pixel16Bit[][] getPixelMatrix(final BufferedImage img) {
            int width = img.getWidth();
            int height = img.getHeight();

            // 16-bit tiffs require 16-bit integers (short)
            short[] pixels = ((DataBufferUShort) img.getData().getDataBuffer()).getData();
            Pixel16Bit[][] pixelMatrix = new Pixel16Bit[height][width];


            // Generate pixelMatrix
            for (int pixel = 0, row = 0, col = 0; pixel < pixels.length; pixel += numSubpixels) {
                // Use 0xffff mask to treat short as ushort when converting to int
                pixelMatrix[row][col] = new Pixel16Bit(
                        pixels[pixel] & 0xffff,
                        pixels[pixel + 1] & 0xffff,
                        pixels[pixel + 2] & 0xffff);
                col++;
                if (col == width) {
                    col = 0;
                    row++;
                }
            }

            return pixelMatrix;
        }

        private static int findThreshold(final BufferedImage img) {
            short[] pixels = ((DataBufferUShort) img.getData().getDataBuffer()).getData();

            if (pixels.length > Integer.MAX_VALUE) {
                throw new UnsupportedOperationException("Number of pixels too large for histogram");
            }

            // A histogram with Integer.MAX_VALUE elements, each element can hold no greater than Integer.MAX_VALUE pixels
            int histogram[] = new int[numSubpixelLevels];

            // Populate histogram
            for (int pixel = 0; pixel < pixels.length; pixel += numSubpixels) {
                // Quick gray scale conversion
                int gray = ((pixels[pixel] & 0xffff) + (pixels[pixel + 1] & 0xffff) + (pixels[pixel + 2] & 0xffff)) / 3;
                histogram[gray]++;
            }

            float sum = 0;
            for (int i = 0; i < numSubpixelLevels; i++) {
                sum += i * histogram[i];
            }

            float sumB = 0;
            int wB = 0;
            int wF = 0;

            float varMax = 0;
            int threshold = 0;

            for (int t=0 ; t<256 ; t++) {
                wB += histogram[t];               // Weight Background
                if (wB == 0) continue;

                wF = pixels.length - wB;                 // Weight Foreground
                if (wF == 0) break;

                sumB += (float) (t * histogram[t]);

                float mB = sumB / wB;            // Mean Background
                float mF = (sum - sumB) / wF;    // Mean Foreground

                // Calculate Between Class Variance
                float varBetween = (float)wB * (float)wF * (mB - mF) * (mB - mF);

                // Check if new maximum found
                if (varBetween > varMax) {
                    varMax = varBetween;
                    threshold = t;
                }
            }

            return threshold;
        }

        public static BufferedImage binarize(final BufferedImage img, int threshold) {
            short[] pixels = ((DataBufferUShort) img.getData().getDataBuffer()).getData();

            // Create new binary image to be filled in
            BufferedImage binaryImage = new BufferedImage(img.getWidth(), img.getHeight(),
                    BufferedImage.TYPE_BYTE_BINARY);
           // byte[] binaryPixels = ((DataBufferByte) binaryImage.getRaster().getDataBuffer()).getData();
            //int [] binaryPixels = binaryImage.getRaster().getPixels(0,0,binaryImage.getWidth(), binaryImage.getHeight(),(int[])(null));
/*
            // Populate binary image according to grayscale threshold
            for (int pixel = 0, bPixel = 0; (pixel < pixels.length )&& (bPixel <binaryPixels.length); pixel += numSubpixels, bPixel++) {
                // Quick gray scale conversion
                int gray = ((pixels[pixel] & 0xffff) + (pixels[pixel + 1] & 0xffff) + (pixels[pixel + 2] & 0xffff)) / 3;
                //if (gray > 3000) {
                    binaryPixels[bPixel] = 1;
                //}
            }
*/
            //for (int bPixel = 0; bPixel <binaryPixels.length; bPixel++) {
                //binaryPixels[bPixel] = 0xF;

            //}

            for(int i = 0; i < binaryImage.getWidth(); i++) {
                for (int j = 0; j < binaryImage.getHeight(); j++) {
                    int rgb = img.getRGB(i, j);
                    // Quick gray scale conversion
                    int gray = ((rgb >> 16) & 0xff) + ((rgb >> 8) & 0xff) + ((rgb & 0xff)) / 3;
                    if (gray > 30) {
                        binaryImage.setRGB(i, j, 0xFFFF);
                    }
                }
            }
            return binaryImage;
        }

        public static void main(String[] args)
        {
            // Open the image (using the name passed as a command line parameter)
            PlanarImage pi = JAI.create("fileload", args[0]);
            System.out.println(args[0]);

            printInfo(pi);
            // TODO: Error if >3 color channels or >16-bit bit depth
            // pixelMatrix is the image represented as a 2D array.
            // Each element is a Pixel16Bit, which can represent 16-bit r,g,b values
            Pixel16Bit[][] pixelMatrix = getPixelMatrix(pi.getAsBufferedImage());
            int threshold = findThreshold(pi.getAsBufferedImage());
            System.out.println("Binarization threshold: " + threshold);

            JFrame frame = new JFrame();
            // Set window size of 2/3 of screen resolution
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int windowHeight = screenSize.height * 2 / 3;
            int windowWidth = screenSize.width * 2 / 3;
            frame.setPreferredSize(new Dimension(windowWidth, windowHeight));

            //BufferedImage img = pi.getAsBufferedImage();
            BufferedImage img = binarize(pi.getAsBufferedImage(), threshold);
            int imgHeight = img.getHeight();
            int imgWidth = img.getWidth();

            double scaleFactor = 1;
            double scaleFactorHeight = 1;
            double scaleFactorWidth = 1;
            if (imgHeight > windowHeight) {
                scaleFactorHeight = (double)imgHeight / windowHeight;
            }
            if (imgWidth > windowWidth) {
                scaleFactorWidth = (double)imgWidth / windowWidth;
            }
            scaleFactor = Math.max(scaleFactorHeight, scaleFactorWidth);


            frame.getContentPane().setLayout(new FlowLayout());
            frame.getContentPane().add(new JLabel(new ImageIcon(img.getScaledInstance((int)(imgWidth/scaleFactor),
                    (int)(imgHeight/scaleFactor), Image.SCALE_SMOOTH))));
            frame.pack();
            frame.setVisible(true);
        }
}
