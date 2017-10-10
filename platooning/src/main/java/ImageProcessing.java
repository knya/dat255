import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import com.sun.javafx.geom.Line2D;
import com.sun.javafx.geom.Point2D;
import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.MatOfPoint;

import static org.opencv.core.Core.*;
import static org.opencv.imgproc.Imgproc.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Johannes Edenholm
 * @author Rikard Teodorsson
 */
public class ImageProcessing {

    static {
        OpenCV.loadShared();
    }

    public static void main(String[] args) {
        ImageProcessing i = new ImageProcessing();
        try {
            ProcessedImage a = i.getProcessedImage("images/opencv-dots.jpg");
            System.out.println(a.getCenterX() + ", " + a.getCenterY() + ", " + a.getxOffset());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns a ProcessedImage containing the circle data
     *
     * @param path the path to the image file
     * @throws IOException if path is null or file not found
     */
    public ProcessedImage getProcessedImage(String path) throws IOException {
        float startTime = System.nanoTime();

        Mat frame = pathToMat(path);
        //imwrite("images/original.png", frame);

        Mat blurredImage = new Mat();
        Mat hsvImage = new Mat();
        Imgproc.blur(frame, blurredImage, new Size(7, 7));
        Imgproc.cvtColor(blurredImage, hsvImage, Imgproc.COLOR_BGR2HSV);

        Mat lowerRed = new Mat();
        Mat upperRed = new Mat();

        //specifies for color red search
        Core.inRange(hsvImage, new Scalar(0, 100, 100), new Scalar(5, 255, 255), lowerRed);
        Core.inRange(hsvImage, new Scalar(160, 100, 100), new Scalar(179, 255, 255), upperRed);

        Mat red_hue_image = new Mat();
        // Combines the two matrices
        addWeighted(lowerRed, 1.0, upperRed, 1.0, 0.0, red_hue_image);

        float endTime = System.nanoTime();
        float duration = (endTime - startTime);
        System.out.println("Duration for processing: " + duration / 1000000000 + "s");

        //imwrite("images/frame.png", frame);
        //imwrite("images/lowerRed.png", lowerRed);
        //imwrite("images/lowerRed.png", lowerRed);

        return contour(red_hue_image, frame);
    }

    /**
     * Draws black around the found red and returns the center circle
     *
     * @param mask  the red in the image
     * @param frame the source matrix
     */
    private ProcessedImage contour(Mat mask, Mat frame) {
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);

        return matrixToList(contours, frame);
    }

    /**
     * Converts a list of matrices to a list of circles
     *
     * @param circles the list of matrices to convert
     * @return an arraylist with the circles
     */
    private ProcessedImage matrixToList(List<MatOfPoint> circles, Mat src) {
        ArrayList<ProcessedImage> circleList = new ArrayList<ProcessedImage>();

        for (final MatOfPoint circle : circles) {
            Point center = new Point(circle.get(0, 0));

            if (circle.toArray().length < 5) {
                continue;
            }

            RotatedRect ellipse = fitEllipse(new MatOfPoint2f(circle.toArray()));

            if ((ellipse.angle >= 80 && ellipse.angle <= 110) || ellipse.size.area() <= 5) {
                continue;
            }

            double imageWidth = src.cols();
            // Calculate the center offset of each circle
            double offset = (center.x / imageWidth) * 200.0 - 100;
            RotatedRect copy = new RotatedRect(ellipse.center, ellipse.size, 0);

            double height, width;
            if (copy.size.height >= copy.size.width) {
                height = copy.size.height;
                width = copy.size.width;
            } else {
                height = copy.size.width;
                width = copy.size.height;
            }

            circleList.add(new ProcessedImage(ellipse.center.x, ellipse.center.y, offset, height, width));
        }

        System.out.println("Number of circles & ellipses found: " + circleList.size());

        return findCorrectCircle(circleList);
    }

    /**
     * Loops through all circles and checks if 3 of them are in a line
     *
     * @param circleList the list of circles to check
     * @return the center circle
     */
    private ProcessedImage findCorrectCircle(ArrayList<ProcessedImage> circleList) {
        for (int i = 0; i < circleList.size(); i++) {
            for (int j = 0; j < circleList.size(); j++) {
                for (int k = 0; k < circleList.size(); k++) {

                    if (i == j || j == k || i == k) {
                        continue;
                    }

                    ProcessedImage c1 = circleList.get(i);
                    ProcessedImage c2 = circleList.get(j);
                    ProcessedImage c3 = circleList.get(k);

                    if (lineIntersectsCircle(c1, c2, c3) && checkDistance(c1, c3)) {
                        return c2;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Checks that the distance from the first to last circle is not too long
     *
     * @param c1 the first circle
     * @param c3 the last circle
     * @return true if the distance is correct
     */
    private boolean checkDistance(ProcessedImage c1, ProcessedImage c3) {
        Point2D p1 = new Point2D((float) c1.getCenterX(), (float) c1.getCenterY());
        Point2D p2 = new Point2D((float) c3.getCenterX(), (float) c3.getCenterY());

        float distance = p1.distance(p2);

        return distance <= c1.getWidth() * 4;
    }

    /**
     * Checks if the center circle intersects the line from c1 to c3
     *
     * @param c1     the first circle
     * @param center the center circle
     * @param c3     the 3rd circle
     * @return true if the center circle is intersecting the line between c1 and c3
     */
    private boolean lineIntersectsCircle(ProcessedImage c1, ProcessedImage center, ProcessedImage c3) {
        double xOffset = center.getxOffset();

        if ((xOffset > c1.getxOffset() && xOffset > c3.getxOffset()) || (xOffset < c1.getxOffset() && xOffset < c3.getxOffset())) {
            return false;
        }

        final double circleRadius = center.getHeight() / 2;

        Line2D line = new Line2D((float) c1.getCenterX(), (float) c1.getCenterY(), (float) c3.getCenterX(), (float) c3.getCenterY());
        Point2D point = new Point2D((float) center.getCenterX(), (float) center.getCenterY());

        double distanceFromCircleToLine = line.ptSegDist(point);

        return distanceFromCircleToLine <= circleRadius;
    }

    /**
     * conversion method from image to mat
     *
     * @param in path to image
     * @return a matrix made from the image
     * @throws IOException if input is null
     */
    private Mat pathToMat(String in) throws IOException {
        BufferedImage imgBuffer;
        imgBuffer = ImageIO.read(new File(in));

        int curCVtype = CvType.CV_8UC4;
        boolean supportedType = true;

        switch (imgBuffer.getType()) {
            case BufferedImage.TYPE_3BYTE_BGR:
                curCVtype = CvType.CV_8UC3;
                break;
            case BufferedImage.TYPE_BYTE_GRAY:
            case BufferedImage.TYPE_BYTE_BINARY:
                curCVtype = CvType.CV_8UC1;
                break;
            case BufferedImage.TYPE_INT_BGR:
            case BufferedImage.TYPE_INT_RGB:
                curCVtype = CvType.CV_32SC3;
                break;
            case BufferedImage.TYPE_INT_ARGB:
            case BufferedImage.TYPE_INT_ARGB_PRE:
                curCVtype = CvType.CV_32SC4;
                break;
            case BufferedImage.TYPE_USHORT_GRAY:
                curCVtype = CvType.CV_16UC1;
                break;
            case BufferedImage.TYPE_4BYTE_ABGR:
            case BufferedImage.TYPE_4BYTE_ABGR_PRE:
                curCVtype = CvType.CV_8UC4;
                break;
            default:
                supportedType = false;
        }

        Mat img = new Mat(imgBuffer.getHeight(), imgBuffer.getWidth(), curCVtype);
        if (supportedType) {
            byte[] pixels = ((DataBufferByte) imgBuffer.getRaster().getDataBuffer()).getData();
            img.put(0, 0, pixels);
        }
        return img;
    }
}