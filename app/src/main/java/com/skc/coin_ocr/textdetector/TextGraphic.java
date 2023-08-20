package com.skc.coin_ocr.textdetector;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

//import com.google.mlkit.vision.text.Text;
import com.skc.coin_ocr.GraphicOverlay;
import com.skc.coin_ocr.ocr.OcrResultModel;
import com.skc.coin_ocr.ocr.OcrResultModelComparator;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.Collections;
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
    Mat detected_circles; // skc add

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
        Mat detected_circles,
        boolean shouldGroupTextInBlocks,
        boolean showLanguageTag,
        boolean showConfidence) {
        super(overlay);

        this.ocrResults = ocrResults;
        this.detected_circles = detected_circles;
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

    class OcrResultCoin {
        public String coin_price, coin_year;
        public int start_idx, box_cnt;
        public int price_box_idx;
    }

    public ArrayList<OcrResultCoin> coinList = null; // 외부에서 동전 리스트의 가격, 년도를 읽어서 처리!

    private ArrayList<OcrResultModel> setOcrResultsBBox() {
        ArrayList<OcrResultModel> ocrResultsNew = new ArrayList<>();
        for (OcrResultModel det : ocrResults) {
            List<Point> points = det.getPoints();
            Log.d("skc", "ocr result -> points.size()=" + points.size() + ", label=" + det.getLabel() + ", det.getConfidence()=" + det.getConfidence());
            if (points.size() == 0 || det.getConfidence() < 0.9f) { // TODO: skc det.getConfidence() 값은 외부에서 설정 가능하게 하자!!!
                Log.e("skc", "Skip draw result -> points.size()=" + points.size() + ", det.getConfidence()=" + det.getConfidence());
                det.bbox_rc = new Rect(-1, -1, -1, -1); // 제외된 box
                continue;
            }
            // det 마다 bbox rect 구하기
            Point min = new Point(points.get(0).x, points.get(0).y);
            Point max = new Point(points.get(0).x, points.get(0).y);
            for (Point p : points) {
                if (p.x < min.x) min.x = p.x;
                else if (p.x > max.x) max.x = p.x;
                if (p.y < min.y) min.y = p.y;
                else if (p.y > max.y) max.y = p.y;
            }
            det.bbox_rc = new Rect(min.x, min.y, max.x, max.y);
            ocrResultsNew.add(det);
        }
        return ocrResultsNew;
    }

    private void setCoinIndex(int max_width, int max_height) {
        if (ocrResults.size() == 0)
            return;
        ocrResults.get(0).coin_idx = 0; // 첫번째 박스는 0번 코인
        for (int i = 0; i < ocrResults.size(); ++i) {
            if (ocrResults.get(i).coin_idx >= 0) {
                //continue; // 이미 부모 동전이 설정된 박스임
            }
            Rect rcOrg = ocrResults.get(i).bbox_rc;
            // 현재 박스 크기의 2배 rect 정의
            Rect rcBigger = new Rect(max(0, rcOrg.left - rcOrg.width()),
                max(0, rcOrg.top - rcOrg.height()),
                min(max_width, rcOrg.right + rcOrg.width()),
                min(max_height, rcOrg.bottom + rcOrg.height()));
            for (int j = 0; j < ocrResults.size(); ++j) {
                if (ocrResults.get(j).coin_idx < 0) { // j != i &&
                    // 아직 부모가 할당되지 않은 박스이고
                    Rect rcOther = ocrResults.get(j).bbox_rc;
                    if (rcBigger.intersect(rcOther)) {
                        // 현재 박스 크기의 2배 rect 와 겹치는 부분이 있으면, 동일 동전으로 간주함
                        ocrResults.get(j).coin_idx = i; // 같은 부모 동전에 포함됨
                    }
                }
            }
        }
    }

    private ArrayList<OcrResultCoin> makeCoinList() {
        ArrayList<OcrResultCoin> coins = new ArrayList<>();
        OcrResultCoin coin = null;
        int prev_cidx = -1;
        for (int i = 0; i < ocrResults.size(); ++i) {
            int cidx = ocrResults.get(i).coin_idx;
            if (cidx < 0) {
                // 탐지된 객체가 동전에 속하지 않는 경우임(이런 경우는 없어야겠다!)
                Log.e("skc", "Coin det [" + i + "] has no coin!!!");
                assert false;
                continue;
            }
            if (prev_cidx != cidx) {
                prev_cidx = cidx;
                coin = new OcrResultCoin();
                coin.start_idx = cidx;
                coins.add(coin);
                if (cidx != coins.size() - 1) {
                    Log.e("skc", "cidx=" + cidx + ", size=" + coins.size());
                    assert false;
                }
            }
            coin.box_cnt++;
        }
        return coins;
    }

    private void setCoinYear(OcrResultCoin coin1, int largest_box_idx) {
        // 년도 -> 가격 제외한 나머지 박스들로 조합
        int min_box_left = -1;
        int largest_box_idx_in_coin = largest_box_idx - coin1.start_idx; // 동전내에서 가장 큰 박스 인덱스
        String tmp_coin_date = "";
        for (int k = 0; k < coin1.box_cnt; ++k) {
            // 1개의 동전내에서 년도 찾기(조합)
            if (k == largest_box_idx_in_coin)
                continue; // 가격 박스는 skip
            OcrResultModel year_det = ocrResults.get(coin1.start_idx + k);
            if (coin1.box_cnt == 2) {
                // 년도가 1개 박스인 경우(가격 1개 포함), 4자리 년도여야 함
                if (year_det.getLabel().length() == 4) {
                    coin1.coin_year = year_det.getLabel();
                } else {
                    Log.e("skc", "Coin [" + k + "] invalid date len = " + year_det.getLabel());
                    //assert false;
                }
            } else if (coin1.box_cnt == 3) {
                // 년도가 2개 박스인 경우, 박스 left 좌표 순서로 조합
                if (min_box_left < 0) {
                    min_box_left = year_det.bbox_rc.left;
                    tmp_coin_date = year_det.getLabel();
                } else {
                    // left 오름차순으로 정렬(다 합쳐서 4자리 년도여야 함)
                    if (year_det.bbox_rc.left < min_box_left) {
                        tmp_coin_date = year_det.getLabel() + tmp_coin_date;
                    } else {
                        tmp_coin_date = tmp_coin_date + year_det.getLabel();
                    }
                }
            } else {
                // TODO: 년도가 3개 이상으로 쪼개진 경우도 처리하자(left, date_str 같이 정렬 필요)
                Log.e("skc", "Coin [" + k + "] too many year boxes -> " + coin1.box_cnt);
                assert false;
            }
        }
        coin1.coin_year = tmp_coin_date; // 이전 동전의 년도 저장
    }

    private ArrayList<OcrResultCoin> getCoins(int max_width, int max_height) {
        // det 마다 똑바로 선 bbox rect 구하기
        ocrResults = setOcrResultsBBox(); // 유효한 박스들만 남김

        if (ocrResults.size() == 0) {
            ArrayList<OcrResultCoin> coins = new ArrayList<>();
            return coins;
        }

        // 각 박스마다 자기 부모 동전의 인덱스를 설정
        setCoinIndex(max_width, max_height);

        // 부모 동전 인덱스로 정렬
        Collections.sort(ocrResults, new OcrResultModelComparator());

        // 동전 배열 저장
        ArrayList<OcrResultCoin> coins = makeCoinList();

        // 같은 동전내에서 가격, 년도 찾기
        for (OcrResultCoin coin1 : coins) {
            // 동전마다 가장 큰 박스를 찾는다.
            int largest_box_idx = coin1.start_idx;
            Rect largest_box = ocrResults.get(coin1.start_idx).bbox_rc; // 시작시 첫박스를 최대박스로
            for (int i = coin1.start_idx; i < coin1.start_idx + coin1.box_cnt; ++i) {
                if (ocrResults.get(i).bbox_rc.height() > largest_box.height()) {
                    // 박스 높이로 가장 큰 박스 인덱스를 저장(ocrResults 기준)
                    largest_box_idx = i;
                }
            }
            coin1.price_box_idx = largest_box_idx;
            coin1.coin_price = ocrResults.get(largest_box_idx).getLabel();

            // 년도 -> 나머지 박스들로 조합
            setCoinYear(coin1, largest_box_idx);
        }
        return coins;
    }

    /** Draws the text block annotations for position, size, and raw value on the supplied canvas. */
    @Override
    public void draw(Canvas canvas) {
        if (!true) {
            // 검출한 원에 덧그리기
            Paint paintCircle = new Paint();
            //paintCircle.setColor(Color.parseColor("#ffff00"));
            paintCircle.setColor(Color.parseColor("#ffffff"));
            paintCircle.setStrokeWidth(10);
            paintCircle.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < detected_circles.cols(); i++) {
                double[] circle = detected_circles.get(0, i); // 검출된 원
                float centerX = translateX((float)circle[0]); // 원의 중심점 X좌표
                float centerY = translateY((float)circle[1]); //원의 중심점 Y좌표
                int radius = (int)Math.round(circle[2]); // 원의 반지름
                radius = (int)scale((float)radius);
                canvas.drawCircle(centerX, centerY, radius, paintCircle);
            }
        }

        if (true) {
            // 동전 영역 그려보기
            ArrayList<OcrResultCoin> coins = getCoins(canvas.getWidth(), canvas.getHeight());
            coinList = coins;
            for (OcrResultCoin coin1 : coins) {
                OcrResultModel det = ocrResults.get(coin1.price_box_idx);
                Rect rcPrice = det.bbox_rc;
                // 동전 박스가 너무 크게 그려져서 1/2 해줌
                Rect rcCoin = new Rect((int) translateX(max(0, rcPrice.left - rcPrice.width() / 2)),
                    (int) translateX(max(0, rcPrice.top - rcPrice.height() / 2)),
                    (int) translateY(min(canvas.getWidth(), rcPrice.right + rcPrice.width() / 2)),
                    (int) translateY(min(canvas.getHeight(), rcPrice.bottom + rcPrice.height() / 2)));

                Paint paintFillAlpha = new Paint();
                paintFillAlpha.setStyle(Paint.Style.FILL);
                paintFillAlpha.setColor(Color.parseColor("#FFFF00"));
                paintFillAlpha.setAlpha(50);
                Paint paint = new Paint();
                paint.setColor(Color.parseColor("#FFFF00"));
                paint.setStrokeWidth(5);
                if (!coin1.coin_price.isEmpty() && !coin1.coin_year.isEmpty()) {
                    // 가격, 년도 모두 인식시 분홍색 박스로 표시
                    paintFillAlpha.setColor(Color.parseColor("#FF00FF")); // FF00FF = magenta
                    paintFillAlpha.setAlpha(30);
                    paint.setColor(Color.parseColor("#FF00FF"));
                    paint.setStrokeWidth(10);
                }
                paint.setStyle(Paint.Style.STROKE);

                canvas.drawRect(rcCoin, paintFillAlpha);
                canvas.drawRect(rcCoin, paint);

                float x = rcPrice.left;
                float y = rcPrice.bottom;
                x += 10; y += 50;
                textPaint.setTextSize(TEXT_SIZE);
                textPaint.setColor(Color.parseColor("#FFFF00"));
                canvas.drawText(String.format("%s [%s]", coin1.coin_price, coin1.coin_year), x, y, textPaint);
            }
        }

        // 탐지 객체들 표시
        int skc_det_idx = 0;
        for (OcrResultModel det : ocrResults) {
            List<Point> points = det.getPoints();
            Log.d("skc", "draw result -> points.size()=" + points.size() + ", label=" + det.getLabel() + ", det.getConfidence()=" + det.getConfidence());
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

            textPaint.setTextSize(40.f);
            canvas.drawText(String.format("%s (%.2f)", det.getLabel(), det.getConfidence())
                , x, y - 5, textPaint);
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
