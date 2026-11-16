# 입체 로디 표정

Figma `3:116`의 원본과 `6:103`의 표정 규칙을 2026-11-16에 다시 확인했다. 원본의 핑크 몸체, 라임 포인트, 작은 얼굴과 발, 크롬 리본을 유지한 정적 이미지다. 표정은 대화 상태가 바뀔 때 한 번 전환하며 반복 애니메이션을 사용하지 않는다.

| 상태 | 로컬 에셋 |
| --- | --- |
| 준비, 듣기, 생각하기, 응답 대기, 브리핑 선택 | `app/src/main/res/drawable-nodpi/roadie_original.png` |
| 말하기와 브리핑 음성 재생 | `app/src/main/res/drawable-nodpi/roadie_speaking.png` |
| 조용한 동행, 마이크 꺼짐, 오디오 안내 대기 | `app/src/main/res/drawable-nodpi/roadie_quiet.png` |
| 휴식 제안과 연결 오류 | `app/src/main/res/drawable-nodpi/roadie_rest.png` |

Figma의 Ready와 Listening은 같은 기본 표정이다. 상태 이름과 조작 버튼으로 이를 구분한다. 작은 창에서는 핵심 상태와 버튼을 우선하며 높이가 충분한 경우에 캐릭터를 표시한다. 밤에는 이미지 밝기를 줄인다. 원본 이미지는 보존했고 기존 캐릭터를 다른 캐릭터로 교체하지 않았다.

## 생성 방법과 프롬프트

내장 image_gen 도구로 원본 PNG를 편집 대상으로 제공했다. 생성된 이미지 3개는 저장소에 복사했다. 이미지 생성 API 키를 사용하거나 CLI fallback을 실행하지 않았다.

공통 프롬프트: "Use case: identity-preserve. Asset type: static 3D character expression for the native Roadie car companion app. The attached PNG is the EDIT TARGET, not just style inspiration. Preserve the original pear-shaped soft pink body silhouette, orientation, little round feet, single lime tuft at top-left, pink textured material, soft studio lighting, chrome ribbon below, cream background, exact centered framing, margins, scale, shadows and camera. Face remains very small, matte dark ink, embedded on upper body. Do not change the body, tuft, feet, chrome ribbon or backdrop. No additional limbs, eyebrows, objects, symbols, text, labels, watermark, collage or UI. One square image, one character, matching the original composition."

- Speaking: "Change ONLY the tiny black mouth into a small round open speaking mouth, like a subtle lowercase o. Keep both small black vertical eyes open and identical. The character is calmly speaking."
- Quiet: "Change ONLY the eyes and mouth: two tiny gently closed horizontal curved eyes and an extremely small relaxed smile. The character is quietly accompanying the driver, peaceful and awake-looking, without sleep symbols. Keep the body pink."
- Rest: "Change ONLY the eyes and mouth: two tiny soft closed curved eyes, a small reassuring warm smile distinct from the original open-eyed expression. The character gently suggests taking a safe break. No sad or alarmed expression. Keep the body pink."

브랜드 원본의 권리는 사용자가 제공한 Roadie 디자인의 권리자에게 있다. 생성 변형을 별도 오픈 라이선스로 재허가하지 않는다.
