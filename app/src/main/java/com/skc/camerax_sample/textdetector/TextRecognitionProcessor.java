package com.skc.camerax_sample.textdetector;

import android.content.Context;

import com.skc.camerax_sample.VisionProcessorBase;
import com.skc.camerax_sample.ocr.Predictor;
import com.skc.camerax_sample.preference.PreferenceUtils;

/** Processor for the text detector demo. */
//public class TextRecognitionProcessor extends VisionProcessorBase<Text> {
public class TextRecognitionProcessor extends VisionProcessorBase {

    private static final String TAG = "TextRecProcessor";

    //private final TextRecognizer textRecognizer;
    private final Boolean shouldGroupRecognizedTextInBlocks;
    private final Boolean showLanguageTag;
    private final boolean showConfidence;

    public TextRecognitionProcessor(Context context, Predictor predictor) {
        super(context);
        shouldGroupRecognizedTextInBlocks = PreferenceUtils.shouldGroupRecognizedTextInBlocks(context);
        showLanguageTag = PreferenceUtils.showLanguageTag(context);
        showConfidence = PreferenceUtils.shouldShowTextConfidence(context);
        //textRecognizer = TextRecognition.getClient(textRecognizerOptions);
        this.predictor = predictor;
    }

    @Override
    public void stop() {
        super.stop();
    }
}
