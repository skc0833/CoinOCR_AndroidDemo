package com.skc.coin_ocr.ocr;

import java.util.Comparator;

public class OcrResultModelComparator implements Comparator<OcrResultModel> {
    @Override
    public int compare(OcrResultModel f1, OcrResultModel f2) {
        return f1.coin_idx - f2.coin_idx;
    }
}
