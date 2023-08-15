package com.skc.coin_ocr.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Size;
import org.opencv.core.Scalar;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;

public class Predictor {
    private static final String TAG = Predictor.class.getSimpleName();
    public boolean isLoaded = false;
    public int warmupIterNum = 1;
    public int inferIterNum = 1;
    public int cpuThreadNum = 4;
    public String cpuPowerMode = "LITE_POWER_HIGH";
    public String modelPath = "";
    public String modelName = "";
    protected OCRPredictorNative paddlePredictor = null;
    protected float inferenceTime = 0;
    // Only for object detection
    protected Vector<String> wordLabels = new Vector<String>();
    protected int detLongSize = 960;
    protected float scoreThreshold = 0.1f;
    protected Bitmap inputImage = null;
    protected Bitmap outputImage = null;
    protected volatile String outputResult = "";
    protected float postprocessTime = 0;

    protected ArrayList<OcrResultModel> predictResults;
    public Mat det_circles = new Mat(); //skc add
    public ArrayList<OcrResultModel> predictResults() {
        return predictResults;
    }

    public Predictor() {
    }

    public boolean init(Context appCtx, String modelPath, String labelPath,
                        String det_model, String rec_model, String cls_model,
                        int useOpencl, int cpuThreadNum, String cpuPowerMode) {
        isLoaded = loadModel(appCtx, modelPath, det_model, rec_model, cls_model,
            useOpencl, cpuThreadNum, cpuPowerMode);
        if (!isLoaded) {
            return false;
        }
        isLoaded = loadLabel(appCtx, labelPath);
        return isLoaded;
    }


    public boolean init(Context appCtx, String modelPath, String labelPath,
                        String det_model, String rec_model, String cls_model,
                        int useOpencl, int cpuThreadNum, String cpuPowerMode,
                        int detLongSize, float scoreThreshold) {
        boolean isLoaded = init(appCtx, modelPath, labelPath, det_model, rec_model, cls_model,
            useOpencl, cpuThreadNum, cpuPowerMode);
        if (!isLoaded) {
            return false;
        }
        this.detLongSize = detLongSize;
        this.scoreThreshold = scoreThreshold;
        return true;
    }

    protected boolean loadModel(Context appCtx, String modelPath, String det_model, String rec_model, String cls_model,
                                int useOpencl, int cpuThreadNum, String cpuPowerMode) {
        // Release model if exists
        releaseModel();

        // Load model
        if (modelPath.isEmpty()) {
            return false;
        }
        String realPath = modelPath;
        if (!modelPath.substring(0, 1).equals("/")) {
            // Read model files from custom path if the first character of mode path is '/'
            // otherwise copy model to cache from assets
            realPath = appCtx.getCacheDir() + "/" + modelPath;
            Utils.copyDirectoryFromAssets(appCtx, modelPath, realPath);
        }
        if (realPath.isEmpty()) {
            return false;
        }

        OCRPredictorNative.Config config = new OCRPredictorNative.Config();
        config.useOpencl = useOpencl;
        config.cpuThreadNum = cpuThreadNum;
        config.cpuPower = cpuPowerMode;
        config.detModelFilename = realPath + File.separator + det_model; // "det_db.nb"
        config.recModelFilename = realPath + File.separator + rec_model; // "rec_crnn.nb"
        config.clsModelFilename = realPath + File.separator + cls_model; // "cls.nb"
        Log.i("Predictor", "model path" + config.detModelFilename + " ; " + config.recModelFilename + ";" + config.clsModelFilename);
        paddlePredictor = new OCRPredictorNative(config);

        this.cpuThreadNum = cpuThreadNum;
        this.cpuPowerMode = cpuPowerMode;
        this.modelPath = realPath;
        this.modelName = realPath.substring(realPath.lastIndexOf("/") + 1);
        return true;
    }

    public void releaseModel() {
        if (paddlePredictor != null) {
            paddlePredictor.destory();
            paddlePredictor = null;
        }
        isLoaded = false;
        cpuThreadNum = 1;
        cpuPowerMode = "LITE_POWER_HIGH";
        modelPath = "";
        modelName = "";
    }

    protected boolean loadLabel(Context appCtx, String labelPath) {
        wordLabels.clear();
        wordLabels.add("black");
        // Load word labels from file
        try {
            InputStream assetsInputStream = appCtx.getAssets().open(labelPath);
            int available = assetsInputStream.available();
            byte[] lines = new byte[available];
            assetsInputStream.read(lines);
            assetsInputStream.close();
            String words = new String(lines);
            String[] contents = words.split("\n");
            for (String content : contents) {
                wordLabels.add(content);
            }
            wordLabels.add(" ");
            Log.i(TAG, "Word label size: " + wordLabels.size());
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
        return true;
    }

    public boolean runModel(int run_det, int run_cls, int run_rec) {
        if (inputImage == null || !isLoaded()) {
            return false;
        }

        // Warm up
        for (int i = 0; i < warmupIterNum; i++) {
            paddlePredictor.runImage(inputImage, detLongSize, run_det, run_cls, run_rec);
        }
        warmupIterNum = 0; // do not need warm
        // Run inference
        Date start = new Date();
        // skc TODO: 여기서 coin detection box 마다 처리해주자!!!
        // OcrResultModel.points 배열의 x, y 좌표만 원본 이미지에서 좌표로 변환해주면 될듯함
        ArrayList<OcrResultModel> results;
        if (true) {
            Date st = new Date();
            // Bitmap to Mat
            Mat src = new Mat();
            org.opencv.android.Utils.bitmapToMat(inputImage, src);

            // grayscale로 변환
            Mat graySrc = new Mat();
            src.copyTo(graySrc);
            Imgproc.cvtColor(graySrc, graySrc, Imgproc.COLOR_RGB2GRAY);

            // 좀 더 정확한 검출을 위해 잡음 제거를 위한 가우시안 블러처리
            Mat blurred = new Mat();
            Imgproc.GaussianBlur(graySrc, blurred, new Size(0.0, 0.0), 1.0);

            //skc 테스트로 가우시안 블러 이미지를 화면에 그려봄 -> setInputImage() 에서 image.copy() 하므로 화면에 안그려진다.
            //blurred.copyTo(src);
            //org.opencv.android.Utils.matToBitmap(src, inputImage);

            // 허프 원 변환을 통한 원 검출
            //Mat circles = new Mat();
            Mat circles = det_circles;
            Log.d(TAG, "skc >>> before Imgproc.HoughCircles()");
            Imgproc.HoughCircles(
                blurred,
                circles,
                Imgproc.HOUGH_GRADIENT, // 검출 방법
                1*2,    // dp, 원의 중심을 검출하는데 사용되는 누산 평면의 해상도(2 이면 입력이미지 해상도의 절반), 클수록 많이 검출됨
                50*4,   // 검출된 원 중심점들의 최소 거리, 겹치지 않을 경우, minRadius 의 2배 이상?(작을수록 많이 검출됨)
                100*3,  // Canny 에지 검출기의 높은 임계값(작을수록 많이 검출됨)
                35*1,   // 누적 배열에서 원 검출을 위한 임계값(작을수록 많이 검출됨)
                50*2,   // minRadius 원의 최소 반지름(작을수록 많이 검출됨)
                100*3   // maxRadius 원의 최대 반지름(클수록 많이 검출됨)
                );
            Date et = new Date();
            float circle_time = (et.getTime() - st.getTime());
            int circle_cnt = circles.cols();
            Log.d(TAG, "skc >>> after Imgproc.HoughCircles() circles.cols()=" + circle_cnt + " time -> " + circle_time);
            if (circle_cnt > 0) {
                // dp == 4 이면 매우 많이 찾으며 100ms 정도 소요됨.
                Log.d(TAG, "skc >>> CIRCLE found " + circle_cnt + " time -> " + circle_time);
            }

//            // 검출한 원에 덧그리기
//            for (int i = 0; i < circles.cols(); i++) {
//                double[] circle = circles.get(0, i); // 검출된 원
//                double centerX = circle[0]; // 원의 중심점 X좌표
//                double centerY = circle[1]; //원의 중심점 Y좌표
//                int radius = (int)Math.round(circle[2]); // 원의 반지름
//                org.opencv.core.Point center = new org.opencv.core.Point((int)Math.round(centerX), (int)Math.round(centerY));
//                Scalar centerColor = new Scalar(0.0, 0.0, 255.0);
//                Imgproc.circle(src, center, 3, centerColor, 3);
//                Scalar circleColor = new Scalar(255.0, 0.0, 255.0);
//                Imgproc.circle(src, center, radius, circleColor, 3);
//            }
//            org.opencv.android.Utils.matToBitmap(src, inputImage);

            results = paddlePredictor.runImage(inputImage, detLongSize, run_det, run_cls, run_rec);
        } else {
            results = paddlePredictor.runImage(inputImage, detLongSize, run_det, run_cls, run_rec);
        }
        Date end = new Date();
        inferenceTime = (end.getTime() - start.getTime()) / (float) inferIterNum;

        results = postprocess(results);
        Log.i(TAG, "[stat] Inference Time: " + inferenceTime + " ;Box Size " + results.size());
        predictResults = results;
        //drawResults(results);

        return true;
    }

    public boolean isLoaded() {
        return paddlePredictor != null && isLoaded;
    }

    public String modelPath() {
        return modelPath;
    }

    public String modelName() {
        return modelName;
    }

    public int cpuThreadNum() {
        return cpuThreadNum;
    }

    public String cpuPowerMode() {
        return cpuPowerMode;
    }

    public float inferenceTime() {
        return inferenceTime;
    }

    public Bitmap inputImage() {
        return inputImage;
    }

    public Bitmap outputImage() {
        return outputImage;
    }

    public String outputResult() {
        return outputResult;
    }

    public float postprocessTime() {
        return postprocessTime;
    }


    public void setInputImage(Bitmap image) {
        if (image == null) {
            return;
        }
        this.inputImage = image.copy(Bitmap.Config.ARGB_8888, true);
    }

    private ArrayList<OcrResultModel> postprocess(ArrayList<OcrResultModel> results) {
        for (OcrResultModel r : results) {
            StringBuffer word = new StringBuffer();
            for (int index : r.getWordIndex()) {
                if (index >= 0 && index < wordLabels.size()) {
                    word.append(wordLabels.get(index));
                } else {
                    Log.e(TAG, "Word index is not in label list:" + index);
                    word.append("×");
                }
            }
            r.setLabel(word.toString());
            r.setClsLabel(r.getClsIdx() == 1 ? "180" : "0");
        }
        return results;
    }

    private void drawResults(ArrayList<OcrResultModel> results) {
        StringBuffer outputResultSb = new StringBuffer("");
        for (int i = 0; i < results.size(); i++) {
            OcrResultModel result = results.get(i);
            StringBuilder sb = new StringBuilder("");
            if(result.getPoints().size()>0){
                sb.append("Det: ");
                for (Point p : result.getPoints()) {
                    sb.append("(").append(p.x).append(",").append(p.y).append(") ");
                }
            }
            if(result.getLabel().length() > 0){
                sb.append("\n Rec: ").append(result.getLabel());
                sb.append(",").append(result.getConfidence());
            }
            if(result.getClsIdx()!=-1){
                sb.append(" Cls: ").append(result.getClsLabel());
                sb.append(",").append(result.getClsConfidence());
            }
            Log.i(TAG, sb.toString()); // show LOG in Logcat panel
            outputResultSb.append(i + 1).append(": ").append(sb.toString()).append("\n");
        }
        outputResult = outputResultSb.toString();
        outputImage = inputImage;
        Canvas canvas = new Canvas(outputImage);
        Paint paintFillAlpha = new Paint();
        paintFillAlpha.setStyle(Paint.Style.FILL);
        paintFillAlpha.setColor(Color.parseColor("#3B85F5"));
        paintFillAlpha.setAlpha(50);

        Paint paint = new Paint();
        paint.setColor(Color.parseColor("#3B85F5"));
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.STROKE);

        for (OcrResultModel result : results) {
            Path path = new Path();
            List<Point> points = result.getPoints();
            if(points.size()==0){
                continue;
            }
            path.moveTo(points.get(0).x, points.get(0).y);
            for (int i = points.size() - 1; i >= 0; i--) {
                Point p = points.get(i);
                path.lineTo(p.x, p.y);
            }
            canvas.drawPath(path, paint);
            canvas.drawPath(path, paintFillAlpha);
        }
    }
}
