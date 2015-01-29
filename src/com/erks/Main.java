package com.erks;
        import java.awt.Transparency;
        import java.awt.image.*;
        import java.io.File;
        import javax.media.jai.*;

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

        private static void printInfo(PlanarImage pi) {
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

        private static Pixel16Bit[][] getPixelMatrix(final WritableRaster raster) {
            int width = raster.getWidth();
            int height = raster.getHeight();

            // 16-bit tiffs require 16-bit integers (short)
            short[] pixels = ((DataBufferUShort) raster.getDataBuffer()).getData();
            Pixel16Bit[][] pixelMatrix = new Pixel16Bit[height][width];

            // For R,G,B images (no alpha channel)
            final int pixelLength = 3;

            // Generate pixelMatrix
            for (int pixel = 0, row = 0, col = 0; pixel < pixels.length; pixel += pixelLength) {
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

        public static void main(String[] args)
        {
            // Open the image (using the name passed as a command line parameter)
            PlanarImage pi = JAI.create("fileload", args[0]);
            System.out.println(args[0]);

            printInfo(pi);

            // pixelMatrix is the image represented as a 2D array.
            // Each element is a Pixel16Bit, which can represent 16-bit r,g,b values
            Pixel16Bit[][] pixelMatrix = getPixelMatrix(pi.copyData());

        }
}
