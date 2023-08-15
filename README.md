# CoinOCR_AndroidDemo
## Duplicated from https://github.com/skc0833/CameraX_Sample (3f422c8)
<br/>

## Build
- Android Studo 에서 File / Open... 메뉴를 클릭 후, CoinOCR_AndroidDemo 폴더를 오픈한다.
- Sync Project with Gradle Files 클릭
- OpenCV 빌드 에러가 발생할 경우, Build / Clean Project 를 하게 되면 이 시점에 app\OpenCV 폴더를 내려받으면서 다시 Gradle Sync 하게 되면 성공함

## Etc
- git clone
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


## Reference

- git@github.com:googlesamples/mlkit.git <br>
Camera sample with ML (TextRecognitionProcessor)

- https://github.com/PaddlePaddle/PaddleOCR.git <br>
Load models/ch_PP-OCRv2 (det_db.nb, cls.nb, rec_crnn.nb) <br>
UI with Settings <br>

- https://github.com/PaddlePaddle/Paddle-Lite-Demo.git <br>
Write back to texture2D (glTexSubImage2D)
