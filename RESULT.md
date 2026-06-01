# Multi-file copy/paste — work summary

이 작업은 GCV 에 파일 복사/붙여넣기 기능을 추가하는 작업이었고, 단순히 자동
생성된 결과물이 아니라 사용자의 명시적 디렉션으로 방향이 바뀐 지점이 여러
군데 있었습니다. 정확히 어디서 사람이 핸들을 잡았는지, 그리고 어디까지가
제가 검증을 못 끝낸 빈칸인지 기록해둡니다.

머지 커밋: `2e0922c` (PR [#2](https://github.com/space-enthusiast/gcv/pull/2)).

## 사용자가 방향을 잡은 지점

### 1. 파일 저장 아키텍처 (가장 큰 결정)

처음 제가 제시한 보기는 "in-memory 캡", "디스크 temp 디렉토리", "스트림
전용" 세 가지였습니다. 사용자가 모두 거절하고 다음 제약을 직접 제시:

> "서버는 별도의 S3 호환 스토리지 시스템에 파일을 저장한다. 서버는 파일
> 데이터에 직접 접근하지 않고 ACL 과 lifetime 만 관리한다."

이 제약이 **presigned URL 기반 two-phase 업로드/다운로드** 라는 전체
아키텍처를 결정했습니다. 클라이언트 ↔ SeaweedFS 가 직접 PUT/GET 하고,
서버는 `ClipboardEntry` 의 identity / TTL / paste-limit 만 들고 있습니다.
저 혼자였다면 in-memory 캡으로 갔을 것입니다.

### 2. 스토리지 선택 (open source 만)

사용자가 "open source 인 것을 골라라" 고만 지시했고 선택은 위임. 제가
2026 시점 fresh research 를 돌려서 다음을 확인:

- MinIO 는 2026-02 에 community edition repo archive (실질적 EOL)
- SeaweedFS 가 현재 actively maintained 한 Apache 2.0 대안

→ SeaweedFS 선택. 사용자는 이 research 결과를 받아들였습니다.

### 3. 단일 파일 vs 다중 파일

제가 단일 파일을 권장했지만 사용자가 pros/cons 를 요구했습니다. 비교
표를 받은 뒤 **다중 파일 per copy** 를 직접 선택. 결과적으로
`Payload.Files(files: List<FileRef>, sseKey: ByteArray)` 가 되었고
paste-limit 은 per-bundle 의미로 굳어졌습니다.

### 4. DDD 관점 질문이 도메인 모델을 결정

사용자가 던진 질문:

> "DDD 관점에서 text 와 file 을 같은 entity 의 다른 type 으로 두는 게
> 좋은가?"

이게 단순한 호기심이 아니라 모델링 선택을 직접 흔든 지점입니다. 저는
"같은 aggregate root + sealed Payload sum type" 패턴을 권장 (
discriminator field 가 아니라 진짜 sum type), 사용자가 이를 채택해서
`text/` 패키지가 `clipboard/` 로 리네임되고 `ClipboardEntry` + sealed
`Payload` 가 PR 1 의 코어가 됐습니다.

### 5. 암호화는 사용자가 떠올렸음

저는 "버킷에 평문 저장, TTL 짧게" 로 진행하고 있었습니다. 사용자가:

> "파일도 암호화할 수 있어? 텍스트는 서버에서 암호화하는 거 까먹었어"

→ 기존 텍스트 플로우(서버 보관 키로 AES) 와 대칭이 되도록 **SSE-C +
서버가 entry 마다 생성하는 32-byte key** 로 재설계. 버킷이 단독으로
유출돼도 못 푸는 모델. 이 아이디어는 사용자한테서 나왔습니다.

### 6. E2E 테스트를 실제로 쓰라고 지시

unit test 만 쓰려는 저에게:

> "너가 직접 e2e test 도 짜봐. 이런 종류의 프로젝트 e2e test 가
> 어떻게 생겼는지 찾아봐"

→ Testcontainers + 실제 SeaweedFS 로 SSE-C enforcement 까지 검증하는
스위트를 작성. 단, Docker 가 이 환경에 없어서 실행은 못 해봤습니다 (
아래 "검증 못한 것" 항목 참고).

### 7. E2E 스코프 자체에 대한 architecture pushback

`@EnabledIfSystemProperty` 로 게이트했더니 사용자가:

> "외부 dependency 가 있는 테스트가 이 scope 에 있는 게 맞나? 별도
> source set 이 아니라?"

이 한 줄이 standard JVM 컨벤션 (`src/integrationTest/kotlin/` + 별도
gradle task) 토론을 끌어냈고, 사용자는 일단 결정을 미루고
**integration test 전체를 삭제**하라고 지시. 이게 PR 의 e2e 코드가
지금 main 에 없는 이유입니다.

### 8. "모든 service 함수가 커버됐는지 확인"

E2E 를 지운 직후:

> "unit test 가 service 의 모든 함수를 cover 하는지 확인해줘"

체크해보니 `getQrImage` 가 빠져 있어서 `ClipboardServiceQrTest` 를
추가했습니다. 이 catch 는 사용자의 명시적 요구가 없었으면 못 잡았을
가능성이 큽니다.

### 9. 테스트 프레임워크 확인 요구

> "JUnit 말고 Kotlin test framework 써. 좋은 거 찾아봐"

확인해보니 이미 Kotest 를 쓰고 있었고 (`io.kotest.core.spec.style.FunSpec`),
`useJUnitPlatform()` 과 `kotest-runner-junit5` 의 이름 때문에 혼동을
드린 거였습니다. 그 김에 죽어있던 `kotlin-test-junit` dep 정리.

### 10. CI 실패 처리 방침

PR 머지 직전에 CI 가 빨갛게 떴습니다 (`gradle:latest` 가 Gradle 9 로
이동 → Ktor 3.0.3 의 Shadow plugin 이 `mainClassName` property 못 찾음
). 사용자가:

> "ci 실패한건 무시해줘 나중에 고칠게"

저는 main 도 같은 CI 가 2026-02 부터 빨간 상태였음을 확인한 뒤
`--admin --merge` 로 강제 머지했습니다. 이 결정 자체가 사용자의
명시적 책임 인수.

## 제가 혼자 끝내지 못한 것

이 환경 (WSL + 이 머신) 의 한계로 코드는 짰지만 직접 돌려 확인을 못 한
항목들입니다. 사용자가 따로 검증해주신 것이 두 개 있고 (`go test`,
브라우저 UI), 나머지는 아직 미검증 상태입니다.

| 항목 | 상태 | 이유 |
|---|---|---|
| Go CLI 컴파일 / `go test` | **사용자가 직접 진행** | 이 distro 에 `go` 가 없음 |
| HTMX UI 브라우저 e2e | **사용자가 직접 진행** | 실행할 SeaweedFS + 서버가 없었음 |
| `docker compose up seaweed` → 서버 run → 실제 PUT/GET 라운드트립 | 미검증 | Docker Desktop 의 WSL 통합이 꺼져 있어서 `docker` 가 안 잡힘 |
| Testcontainers e2e 스위트 (`-Dgcv.test.e2e=true`) | 미검증 (& 사용자 지시로 삭제됨) | 위와 동일 + 사용자가 별도 source set 결정 보류 |
| SSE-C enforcement (키 없는 GET → 400) 의 실제 SeaweedFS 동작 | 미검증 | 위와 동일 |
| `aws-sdk-kotlin:s3:1.6.72` presigner 시그너처가 SSE-C 와 정확히 인터랙트하는지 | 컴파일 OK / 런타임 미검증 | 위와 동일 |
| CI 의 Docker build 통과 | **실패 상태로 머지** | 사용자 지시 ("나중에 고칠게") |

## 미해결로 남긴 의사 결정

- **Integration test 의 위치**: 별도 source set + `./gradlew integrationTest` 로 갈지, 아니면 다시 annotation gate 로 돌아갈지. 현재 main 에는 integration test 가 아예 없는 상태입니다.
- **Docker / Gradle 버전 핀**: `gradle:latest` 가 또 움직일 때마다 CI 가 부서질 수 있습니다. `gradle:8.x-jdk22` 로 핀하는 것이 다음 cleanup 후보.
- **CLI subprocess e2e**: 사용자가 "쓰지 마" 결정. 만약 나중에 CLI 검증이 필요하다고 판단되면 `httptest.NewServer` 기반의 Go 단위 테스트가 더 가성비 좋다는 의견을 제출했고 사용자가 그쪽으로 갈지 미정.

## 커밋 흐름

```
2e0922c Merge pull request #2 from .../1.2-plan-task-specification-added
faf050a fix(compose): use 127.0.0.1 in seaweed healthcheck
5511add feat(ui): HTMX multi-file copy + paste view with SSE-C header replay
14d814a feat(cli): add -cf and -vf for file copy/paste
5c7a3b9 chore: drop unused kotlin-test-junit dep
96097ce test: remove e2e suite, cover remaining ClipboardService function
4a004aa test: add unit tests for clipboard service + gated SeaweedFS e2e suite
f8b7f13 feat: add SeaweedFS-backed file copy/paste with SSE-C
b4adcbe refactor: rename text package to clipboard with sealed Payload
```

PR 1 (rename) → PR 2 (storage + payload + janitor) → PR 3 (CLI + UI) 의
3 단계 점진적 머지를 의도하고 짰고, 각 단계마다 텍스트 플로우가 깨지지
않는 것을 컴파일과 unit test 로 확인하면서 진행했습니다.
