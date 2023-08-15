# CoinOCR_AndroidDemo
### Duplicated from https://github.com/skc0833/CameraX_Sample (3f422c8)
<br/>

## Build
- Android Studo 에서 File / Open... 메뉴를 클릭 후, CoinOCR_AndroidDemo 폴더를 오픈한다.
- Sync Project with Gradle Files 클릭
- OpenCV 빌드 에러가 발생할 경우, Build / Clean Project 를 하게 되면 이 시점에 app\OpenCV 폴더를 내려받으면서 다시 Gradle Sync 하게 되면 성공함

## Inference

<details>
<summary>init</summary>

```
// app/src/main/java/com/skc/coin_ocr/LivePreviewActivity.java
onResume() -> createCameraSource(selectedModel) ->
predictor.init(this,
               modelPath,  // "models/ch_PP-OCRv3_Student_99"
               labelPath,  // "labels/ppocr_keys_v1.txt"
               det_model,  // "det_ch_PP-OCRv3_Student_99.nb"
               rec_model,  // "rec_ch_PP-OCRv3_Student_99.nb"
               cls_model,  // "cls.nb"
               0,    // useOpencl
               1,    // cpuThreadNum
               "",   // cpuPowerMode, TODO: "LITE_POWER_FULL" 테스트 필요!
               960,  // detLongSize(ratio 를 맞춰서 resize 됨)
               0.1f  // scoreThreshold (TODO: 사용처 없음???)
               )
->
isLoaded = loadModel(appCtx, modelPath, det_model, ...)
loadLabel(appCtx, labelPath);

// app/src/main/java/com/skc/coin_ocr/ocr/Predictor.java
protected boolean loadModel(Context appCtx, String modelPath, ...)
-> paddlePredictor = new OCRPredictorNative(config);

// app/src/main/java/com/skc/coin_ocr/ocr/OCRPredictorNative.java
public OCRPredictorNative(Config config)
->
loadLibrary(); -> System.loadLibrary("Native");
nativePointer = init(config.detModelFilename, config.recModelFilename, ...)

// app/src/main/cpp/native.cpp
Java_com_skc_coin_1ocr_ocr_OCRPredictorNative_init(JNIEnv *env, jobject thiz, jstring j_det_model_path, ...)
```
</details>

<details>
<summary>input</summary>

```
// 시작시 or 모델 선택시 초기화
CameraSource(Activity activity, GraphicOverlay overlay)
-> processingRunnable = new FrameProcessingRunnable();

// app/src/main/java/com/skc/coin_ocr/CameraSourcePreview.java
startIfReady()
-> cameraSource.start();

// app/src/main/java/com/skc/coin_ocr/CameraSource.java
CameraSource.start() 에서
processingThread = new Thread(processingRunnable);
processingThread.start();

// 카메라 preview 콜백에서
CameraPreviewCallback.onPreviewFrame(byte[] data, Camera camera)
-> processingRunnable.setNextFrame(data, camera);
--> pendingFrameData = bytesToByteBuffer.get(data); 로 프레임 저장 후,
lock.notifyAll(); 로 processingRunnable 를 깨움

CameraSource$FrameProcessingRunnable.run() 쓰레드는 lock.wait(); 에서 깨어나
-> 
data = pendingFrameData; // 로컬 변수에 복사만 하고, setNextFrame() 의 lock 은 풀어준다!
frameProcessor.processByteBuffer(
                            data,
                            new FrameMetadata.Builder()
                                .setWidth(previewSize.getWidth())
                                .setHeight(previewSize.getHeight())
                                .setRotation(rotationDegrees)
                                .build(),
                            graphicOverlay);

참고로 StillImageActivity 에서는 imageProcessor.processBitmap(resizedBitmap, graphicOverlay); 로 직접 화면에 그림

// app/src/main/java/com/skc/coin_ocr/VisionProcessorBase.java
processByteBuffer(ByteBuffer data, final FrameMetadata frameMetadata, final GraphicOverlay graphicOverlay)
-> processLatestImage(graphicOverlay);

void processLatestImage(final GraphicOverlay graphicOverlay)
-> processImage(processingImage, processingMetaData, graphicOverlay);

processImage(ByteBuffer data, final FrameMetadata frameMetadata, 
             final GraphicOverlay graphicOverlay)
->
long frameStartMs = SystemClock.elapsedRealtime();
BitmapUtils.getBitmap(data, frameMetadata) 로 ImageFormat.NV21 를 Bitmap 로 변환 후,
1) requestDetectInImage(graphicOverlay,
            bitmap,
            /* shouldShowFps= */ true,
            frameStartMs);
2) processLatestImage(graphicOverlay); // 여기서 또 호출중

requestDetectInImage(final GraphicOverlay graphicOverlay, final Bitmap originalCameraImage, ...)
->
1) runModel(originalCameraImage)
2) graphicOverlay 에 CameraImageGraphic, predictor.predictResults(), InferenceInfoGraphic 추가 후, 
graphicOverlay.postInvalidate(); 로 화면 갱신

boolean runModel(Bitmap image)
->
predictor.setInputImage(image);
return predictor.runModel(1, 0, 1);

// app/src/main/java/com/skc/coin_ocr/ocr/Predictor.java
boolean runModel(int run_det, int run_cls, int run_rec)
->
1) ArrayList<OcrResultModel> results = paddlePredictor.runImage(inputImage, detLongSize, run_det, run_cls, run_rec);
2) results = postprocess(results);
predictResults = results;  // 바로 이후에 graphicOverlay 에 추가됨

// app/src/main/java/com/skc/coin_ocr/ocr/OCRPredictorNative.java
runImage(Bitmap originalImage, int max_size_len, int run_det, int run_cls, int run_rec)
->
1) float[] rawResults = forward(nativePointer, originalImage, max_size_len, run_det, run_cls, run_rec);
2) ArrayList<OcrResultModel> results = postprocess(rawResults);

// app/src/main/cpp/native.cpp
Java_com_skc_coin_1ocr_ocr_OCRPredictorNative_forward(
      JNIEnv *env, jobject thiz, 
      jlong java_pointer, jobject original_image, ...)
-> std::vector<ppredictor::OCRPredictResult> results =
     ppredictor->infer_ocr(origin, max_size_len, run_det, run_cls, run_rec);


```
</details>

<details>
<summary>output</summary>

```
// app/src/main/java/com/skc/coin_ocr/GraphicOverlay.java
onDraw(Canvas canvas)
-> graphic.draw(canvas);

// app/src/main/java/com/skc/coin_ocr/textdetector/TextGraphic.java
void draw(Canvas canvas)

```
</details>

## Etc

<details>
<summary>git clone</summary>

- git clone 2 가지 방법
```
git clone https://github.com/skc0833/CoinOCR_AndroidDemo.git
-> 
// https://github.com/~ 주소는 id, password 인증은 더 이상 지원되지 않는다는 에러 발생함
remote: Support for password authentication was removed on August 13, 2021.
fatal: Authentication failed for 'https://github.com/skc0833/CoinOCR_AndroidDemo.git/'

git clone git@github.com:skc0833/CoinOCR_AndroidDemo.git
->
이렇게 내려받기 위해서는 github.com 에 해당 PC 의 SSH Key (public key) 등록이 필요함
```

- SSH Key 생성
```
이미 c/Users/<user>/.ssh/id_rsa.pub 파일이 존재할 경우, 아래 절차 필요없음
(e.g, c/Users/skc0833/.ssh/id_rsa.pub)

1) 윈도우 터미널 혹은 PowerShell 에서 ssh-keygen 입력
2) 디폴트로 엔터키만 치면 c/Users/<user>/.ssh/id_rsa, id_rsa.pub 파일이 생성됨

-> https://oingdaddy.tistory.com/453 참고
```

- SSH Key 등록
```
1) https://github.com/skc0833/CoinOCR_AndroidDemo 페이지의 우상단 원형 아이콘 클릭후, Settings 클릭
   (https://github.com/settings/keys 링크임)

2) 좌측 SSH and GPG keys 클릭 후, 우측 화면 우상단의 New SSH Key 버튼 클릭

3) 진입한 Add new SSH Key 화면에서 Title 은 현재 PC 를 구별할만한 값을 입력(e.g, skc0833_samsung_notebook)
Key type 은 디폴트 유지(Authentication Key), Key 항목에는 위에서 생성했던 id_rsa.pub 파일의 내용을 복사해 붙여넣는다.

4) 이후 git clone git@github.com:skc0833/CoinOCR_AndroidDemo.git 는 성공해야 함
```
</details>


## Reference

<details>
<summary>Reference</summary>

- git@github.com:googlesamples/mlkit.git <br>
Camera sample with ML (TextRecognitionProcessor)

- https://github.com/PaddlePaddle/PaddleOCR.git <br>
Load models/ch_PP-OCRv2 (det_db.nb, cls.nb, rec_crnn.nb) <br>
UI with Settings <br>

- https://github.com/PaddlePaddle/Paddle-Lite-Demo.git <br>
Write back to texture2D (glTexSubImage2D)
</details>
