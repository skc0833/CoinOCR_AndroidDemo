package com.skc.coin_ocr.textdetector;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.util.Log;

//import com.google.mlkit.vision.text.Text;
import com.skc.coin_ocr.GraphicOverlay;
import com.skc.coin_ocr.ocr.OcrResultModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Graphic instance for rendering TextBlock position, size, and ID within an associated graphic
 * overlay view.
 */
public class TextGraphic extends GraphicOverlay.Graphic {

    private static final String TAG = "TextGraphic";
    private static final String TEXT_WITH_LANGUAGE_TAG_FORMAT = "%s:%s";

    private static final int TEXT_COLOR = Color.BLACK;
    private static final int MARKER_COLOR = Color.WHITE;
    private static final float TEXT_SIZE = 54.0f;
    private static final float STROKE_WIDTH = 4.0f;

    private final Paint rectPaint;
    private final Paint textPaint;
    private final Paint labelPaint;
    //private final Text text;
    private final boolean shouldGroupTextInBlocks;
    private final boolean showLanguageTag;
    private final boolean showConfidence;

    private ArrayList<OcrResultModel> ocrResults; // skc add

    TextGraphic(
        GraphicOverlay overlay,
        //Text text,
        boolean shouldGroupTextInBlocks,
        boolean showLanguageTag,
        boolean showConfidence) {
        super(overlay);

        //this.text = text;
        this.shouldGroupTextInBlocks = shouldGroupTextInBlocks;
        this.showLanguageTag = showLanguageTag;
        this.showConfidence = showConfidence;

        rectPaint = new Paint();
        rectPaint.setColor(MARKER_COLOR);
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(STROKE_WIDTH);

        textPaint = new Paint();
        textPaint.setColor(TEXT_COLOR);
        textPaint.setTextSize(TEXT_SIZE);

        labelPaint = new Paint();
        labelPaint.setColor(MARKER_COLOR);
        labelPaint.setStyle(Paint.Style.FILL);
        // Redraw the overlay, as this graphic has been added.
        postInvalidate();
    }

    public TextGraphic(
        GraphicOverlay overlay,
        ArrayList<OcrResultModel> ocrResults,
        boolean shouldGroupTextInBlocks,
        boolean showLanguageTag,
        boolean showConfidence) {
        super(overlay);

        this.ocrResults = ocrResults;
        this.shouldGroupTextInBlocks = shouldGroupTextInBlocks;
        this.showLanguageTag = showLanguageTag;
        this.showConfidence = showConfidence;

        rectPaint = new Paint();
        rectPaint.setColor(MARKER_COLOR);
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(STROKE_WIDTH);

        textPaint = new Paint();
        textPaint.setColor(TEXT_COLOR);
        textPaint.setTextSize(TEXT_SIZE);

        labelPaint = new Paint();
        labelPaint.setColor(MARKER_COLOR);
        labelPaint.setStyle(Paint.Style.FILL);
        // Redraw the overlay, as this graphic has been added.
        postInvalidate();
    }

    public TextGraphic(
        GraphicOverlay overlay,
        String text,
        boolean shouldGroupTextInBlocks,
        boolean showLanguageTag,
        boolean showConfidence) {
        super(overlay);

        this.shouldGroupTextInBlocks = shouldGroupTextInBlocks;
        this.showLanguageTag = showLanguageTag;
        this.showConfidence = showConfidence;

        rectPaint = new Paint();
        rectPaint.setColor(MARKER_COLOR);
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(STROKE_WIDTH);

        textPaint = new Paint();
        textPaint.setColor(TEXT_COLOR);
        textPaint.setTextSize(TEXT_SIZE);

        labelPaint = new Paint();
        labelPaint.setColor(MARKER_COLOR);
        labelPaint.setStyle(Paint.Style.FILL);
        // Redraw the overlay, as this graphic has been added.
        postInvalidate();
    }

    /** Draws the text block annotations for position, size, and raw value on the supplied canvas. */
    @Override
    public void draw(Canvas canvas) {
        int skc_det_idx = 0;
        for (OcrResultModel det : ocrResults) {
            List<Point> points = det.getPoints();
            Log.d("skc", "draw result -> points.size()=" + points.size() + ", det.getConfidence()=" + det.getConfidence());
            //if (points.size() == 0 || det.getConfidence() < 0.9f) {
            if (points.size() == 0 || det.getConfidence() < 0.9f) { // TODO: skc det.getConfidence() 값은 외부에서 설정 가능하게 하자!!!
                Log.e("skc", "Skip draw result -> points.size()=" + points.size() + ", det.getConfidence()=" + det.getConfidence());
                continue;
            }
            //float x = points.get(0).x;
            //float y = points.get(0).y;
            float x = translateX(points.get(0).x); // skc translateX() 해줘야 좌표가 맞음
            float y = translateY(points.get(0).y);
            Path path = new Path();
            path.moveTo(x, y);
            for (int i = points.size() - 1; i >= 0; i--) {
                Point p = points.get(i);
                path.lineTo(translateX(p.x), translateY(p.y));
            }
            Paint paintFillAlpha = new Paint();
            paintFillAlpha.setStyle(Paint.Style.FILL);
            paintFillAlpha.setColor(Color.parseColor("#3B85F5"));
            paintFillAlpha.setAlpha(50);
            Paint paint = new Paint();
            paint.setColor(Color.parseColor("#3B85F5"));
            paint.setStrokeWidth(5);
            paint.setStyle(Paint.Style.STROKE);

            canvas.drawPath(path, paint);
            canvas.drawPath(path, paintFillAlpha);

            // skc 찾은 object 별로 색상을 달리해 표시하게 함
            String skc_color = "#FF0000";
            //textPaint.setColor(Color.parseColor("#3B85F5"));
            if (skc_det_idx % 3 == 1) {
                skc_color = "#00FF00";
            } else if (skc_det_idx % 3 == 2) {
                skc_color = "#0000FF";
            }
            textPaint.setColor(Color.parseColor(skc_color));
            skc_det_idx++;

            canvas.drawText(String.format("%s (%.2f)", det.getLabel(), det.getConfidence())
                , x, y - STROKE_WIDTH, textPaint); // textPaint
        }
    }

    private void drawText(String text, RectF rect, float textHeight, Canvas canvas) {
        // If the image is flipped, the left will be translated to right, and the right to left.
        float x0 = translateX(rect.left);
        float x1 = translateX(rect.right);
        rect.left = min(x0, x1);
        rect.right = max(x0, x1);
        rect.top = translateY(rect.top);
        rect.bottom = translateY(rect.bottom);
        canvas.drawRect(rect, rectPaint);
        float textWidth = textPaint.measureText(text);
        canvas.drawRect(
            rect.left - STROKE_WIDTH,
            rect.top - textHeight,
            rect.left + textWidth + 2 * STROKE_WIDTH,
            rect.top,
            labelPaint);
        // Renders the text at the bottom of the box.
        canvas.drawText(text, rect.left, rect.top - STROKE_WIDTH, textPaint);
    }
}
