package com.skc.coin_ocr;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import com.skc.coin_ocr.ocr.Predictor;
import com.skc.coin_ocr.preference.PreferenceUtils;
import com.skc.coin_ocr.textdetector.TextGraphic;

import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Abstract base class for vision frame processors. Subclasses need to implement {@link
 * #onSuccess(Object, GraphicOverlay)} to define what they want to with the detection results and
 * {@link #detectInImage(InputImage)} to specify the detector object.
 *
 * @param <T> The type of the detected feature.
 */
//public abstract class VisionProcessorBase<T> implements VisionImageProcessor {
public abstract class VisionProcessorBase implements VisionImageProcessor {

    private static final String TAG = "VisionProcessorBase";

    private final ActivityManager activityManager;
    private final Timer fpsTimer = new Timer();
    private final TemperatureMonitor temperatureMonitor;

    // Whether this processor is already shut down
    private boolean isShutdown;

    // Used to calculate latency, running in the same thread, no sync needed.
    private int numRuns = 0;
    private long totalFrameMs = 0;
    private long maxFrameMs = 0;
    private long minFrameMs = Long.MAX_VALUE;
    private long totalDetectorMs = 0;
    private long maxDetectorMs = 0;
    private long minDetectorMs = Long.MAX_VALUE;

    // Frame count that have been processed so far in an one second interval to calculate FPS.
    private int frameProcessedInOneSecondInterval = 0;
    private int framesPerSecond = 0;

    // To keep the latest images and its metadata.
    @GuardedBy("this")
    private ByteBuffer latestImage;

    @GuardedBy("this")
    private FrameMetadata latestImageMetaData;
    // To keep the images and metadata in process.
    @GuardedBy("this")
    private ByteBuffer processingImage;

    @GuardedBy("this")
    private FrameMetadata processingMetaData;

    protected Predictor predictor; // skc add

    protected VisionProcessorBase(Context context) {
        activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        fpsTimer.scheduleAtFixedRate(
            new TimerTask() {
                @Override
                public void run() {
                    framesPerSecond = frameProcessedInOneSecondInterval;
                    frameProcessedInOneSecondInterval = 0;
                }
            },
            /* delay= */ 0,
            /* period= */ 1000);
        temperatureMonitor = new TemperatureMonitor(context);
    }

    // -----------------Code for processing single still image----------------------------------------
    @Override
    public void processBitmap(Bitmap bitmap, final GraphicOverlay graphicOverlay) {
        long frameStartMs = SystemClock.elapsedRealtime();

        requestDetectInImage(
            graphicOverlay,
            bitmap,
            /* shouldShowFps= */ true,
            frameStartMs);
        //skc TODO: 아래 함수 호출은 불필요해 보임!!! StillImageActivity 에서 호출시 latestImage 는 항상 null ?
        processLatestImage(graphicOverlay);
    }

    // -----------------Code for processing live preview frame from Camera1 API-----------------------
    @Override
    public synchronized void processByteBuffer(
        ByteBuffer data, final FrameMetadata frameMetadata, final GraphicOverlay graphicOverlay) {
        latestImage = data;
        latestImageMetaData = frameMetadata;
        if (processingImage == null && processingMetaData == null) {
            processLatestImage(graphicOverlay);
        }
    }

    private synchronized void processLatestImage(final GraphicOverlay graphicOverlay) {
        processingImage = latestImage;
        processingMetaData = latestImageMetaData;
        latestImage = null;
        latestImageMetaData = null;
        if (processingImage != null && processingMetaData != null && !isShutdown) {
            processImage(processingImage, processingMetaData, graphicOverlay);
        }
    }

    private void processImage(
        ByteBuffer data, final FrameMetadata frameMetadata, final GraphicOverlay graphicOverlay) {
        long frameStartMs = SystemClock.elapsedRealtime();

        // If live viewport is on (that is the underneath surface view takes care of the camera preview
        // drawing), skip the unnecessary bitmap creation that used for the manual preview drawing.
        Bitmap bitmap =
            PreferenceUtils.isCameraLiveViewportEnabled(graphicOverlay.getContext())
                ? null
                : BitmapUtils.getBitmap(data, frameMetadata);

        requestDetectInImage(
            graphicOverlay,
            bitmap,
            /* shouldShowFps= */ true,
            frameStartMs);
        processLatestImage(graphicOverlay);
    }

    // -----------------Code for processing live preview frame from CameraX API-----------------------
    @Override
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @ExperimentalGetImage
    public void processImageProxy(ImageProxy image, GraphicOverlay graphicOverlay) {
        long frameStartMs = SystemClock.elapsedRealtime();
        if (isShutdown) {
            image.close();
            return;
        }

        Bitmap bitmap = null;
        if (!PreferenceUtils.isCameraLiveViewportEnabled(graphicOverlay.getContext())) {
            bitmap = BitmapUtils.getBitmap(image);
        }

        requestDetectInImage(
            graphicOverlay,
            /* originalCameraImage= */ bitmap,
            /* shouldShowFps= */ true,
            frameStartMs);
        image.close();
    }

    // -----------------Common processing logic-------------------------------------------------------
    public boolean runModel(Bitmap image) {
        // skc TODO: int run_det, int run_cls, int run_rec 인자는 설정에서 가져오자!
        predictor.setInputImage(image);
        return predictor.runModel(1, 0, 1);
    }

    private void requestDetectInImage(
        final GraphicOverlay graphicOverlay,
        @Nullable final Bitmap originalCameraImage,
        boolean shouldShowFps,
        long frameStartMs) {
        final long detectorStartMs = SystemClock.elapsedRealtime();
        if (runModel(originalCameraImage)) { // onSuccess
            long endMs = SystemClock.elapsedRealtime();
            long currentFrameLatencyMs = endMs - frameStartMs;
            long currentDetectorLatencyMs = endMs - detectorStartMs;
            if (numRuns >= 500) {
                resetLatencyStats();
            }
            numRuns++;
            frameProcessedInOneSecondInterval++;
            totalFrameMs += currentFrameLatencyMs;
            maxFrameMs = max(currentFrameLatencyMs, maxFrameMs);
            minFrameMs = min(currentFrameLatencyMs, minFrameMs);
            totalDetectorMs += currentDetectorLatencyMs;
            maxDetectorMs = max(currentDetectorLatencyMs, maxDetectorMs);
            minDetectorMs = min(currentDetectorLatencyMs, minDetectorMs);

            // Only log inference info once per second. When frameProcessedInOneSecondInterval is
            // equal to 1, it means this is the first frame processed during the current second.
            if (frameProcessedInOneSecondInterval == 1) {
                Log.d(TAG, "Num of Runs: " + numRuns);
                Log.d(
                    TAG,
                    "Frame latency: max="
                        + maxFrameMs
                        + ", min="
                        + minFrameMs
                        + ", avg="
                        + totalFrameMs / numRuns);
                Log.d(
                    TAG,
                    "Detector latency: max="
                        + maxDetectorMs
                        + ", min="
                        + minDetectorMs
                        + ", avg="
                        + totalDetectorMs / numRuns);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                activityManager.getMemoryInfo(mi);
                long availableMegs = mi.availMem / 0x100000L;
                Log.d(TAG, "Memory available in system: " + availableMegs + " MB");
                temperatureMonitor.logTemperature();
            }

            graphicOverlay.clear();
            if (originalCameraImage != null) {
                graphicOverlay.add(new CameraImageGraphic(graphicOverlay, originalCameraImage));
            }
            graphicOverlay.add(
                new TextGraphic(
                    graphicOverlay,
                    predictor.predictResults(),
                    predictor.det_circles,
                    false,
                    false,
                    false));

            if (!PreferenceUtils.shouldHideDetectionInfo(graphicOverlay.getContext())) {
                graphicOverlay.add(
                    new InferenceInfoGraphic(
                        graphicOverlay,
                        currentFrameLatencyMs,
                        currentDetectorLatencyMs,
                        shouldShowFps ? framesPerSecond : null));
            }
            graphicOverlay.postInvalidate();
        } else { // onFailure
            graphicOverlay.clear();
            graphicOverlay.postInvalidate();
        }
    }

    @Override
    public void stop() {
        isShutdown = true;
        resetLatencyStats();
        fpsTimer.cancel();
        temperatureMonitor.stop();
    }

    private void resetLatencyStats() {
        numRuns = 0;
        totalFrameMs = 0;
        maxFrameMs = 0;
        minFrameMs = Long.MAX_VALUE;
        totalDetectorMs = 0;
        maxDetectorMs = 0;
        minDetectorMs = Long.MAX_VALUE;
    }
}
