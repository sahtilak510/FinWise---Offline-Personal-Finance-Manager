package com.finance.service;

import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class LocalOpenCVPreprocessor implements OpenCVPreprocessor {

    private static final AtomicBoolean OPEN_CV_ATTEMPTED = new AtomicBoolean(false);
    private static final AtomicBoolean OPEN_CV_AVAILABLE = new AtomicBoolean(false);

    private static synchronized boolean tryLoadOpenCV() {
        if (OPEN_CV_ATTEMPTED.get()) {
            return OPEN_CV_AVAILABLE.get();
        }

        OPEN_CV_ATTEMPTED.set(true);

        try {
            Class.forName("nu.pattern.OpenCV", false, LocalOpenCVPreprocessor.class.getClassLoader());
            try {
                Method loadLocally = Class.forName("nu.pattern.OpenCV", false, LocalOpenCVPreprocessor.class.getClassLoader())
                        .getDeclaredMethod("loadLocally");
                loadLocally.invoke(null);
            } catch (NoSuchMethodException ignored) {
                // OpenCV library loader may already be bootstrapped by the dependency itself.
            }

            Class.forName("org.opencv.core.Core", false, LocalOpenCVPreprocessor.class.getClassLoader());
            OPEN_CV_AVAILABLE.set(true);
            return true;
        } catch (Exception e) {
            System.err.println("OpenCV not available for OCR preprocessing. Falling back to Java image preprocessing: " + e.getMessage());
            OPEN_CV_AVAILABLE.set(false);
            return false;
        }
    }

    @Override
    public byte[] preprocess(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return new byte[0];
        }

        if (!tryLoadOpenCV()) {
            return fallbackPreprocess(imageBytes);
        }

        try {
            Mat image = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_COLOR);
            if (image == null || image.empty()) {
                return fallbackPreprocess(imageBytes);
            }

            Mat gray = new Mat();
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);

            Mat blurred = new Mat();
            Imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 0, 0);

            Mat threshold = new Mat();
            Imgproc.adaptiveThreshold(blurred, threshold, 255,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY,
                    11,
                    2);

            Mat denoised = new Mat();
            Photo.fastNlMeansDenoising(threshold, denoised, 10f, 7, 21);

            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
            Mat morphed = new Mat();
            Imgproc.morphologyEx(denoised, morphed, Imgproc.MORPH_CLOSE, kernel);

            MatOfByte output = new MatOfByte();
            Imgcodecs.imencode(".png", morphed, output);
            return output.toArray();
        } catch (Exception e) {
            System.err.println("OpenCV preprocessing failed, using fallback: " + e.getMessage());
            return fallbackPreprocess(imageBytes);
        }
    }

    private byte[] fallbackPreprocess(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return new byte[0];
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                return imageBytes;
            }

            BufferedImage grayImage = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            grayImage.getGraphics().drawImage(image, 0, 0, null);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(grayImage, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return imageBytes;
        }
    }
}