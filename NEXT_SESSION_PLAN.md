# Next Session Execution Plan: ISL/ASL Sign Language Model & Content Review

**Repository**: [`C:\work\sign-setu`](file:///C:/work/sign-setu)

---

## 1. Environment & Tooling Quick-Start

Use the pinned environments for any tooling, ML, or kinematics execution:

```powershell
Set-Location "C:\work\sign-setu"

$env:SIGNSETU_MP = "C:\Users\Yash\dev\venvs\signsetu-mp\Scripts\python.exe"     # MediaPipe, OpenCV, kinematics
$env:SIGNSETU_TOOLS = "C:\Users\Yash\dev\venvs\signsetu-tools\Scripts\python.exe" # Manifest, build_signs, resvg
$env:SIGNSETU_DATA = "C:\Users\Yash\dev\ml-data"                                # Datasets root
```

---

## 2. Completed Milestones
- [x] **Devanagari Varnamala Support**: Full Swar and Vyanjan kinematics, conjunct aksharas (`क्ष`, `त्र`, `ज्ञ`), aspirates, long vowels, and retroflex modifiers.
- [x] **Dynamic 2.5D Edge Outlining**: Contrast edge outlines (`contactStroke`) for overlapping/touching hands across Kotlin and TypeScript rendering engines.
- [x] **Stage Spelled Index & Akshara Tile Sync**: Clean `spelledIndex` extraction handling boundary clamping and unit test verification.
- [x] **Full Android & Portal Build Verification**:
  - `RigFixtureTest` and all 264 Android unit tests pass cleanly.
  - All 1,217 Portal test fixtures pass.
  - `assembleStandardDebug` produces verified native C++ CMake and DEX binaries.
  - `test_retarget.py` (12 tests) and `test_sbom.py` (2 tests) verified.
- [x] **ASL Fallback & Boundary Audit**:
  - Architecture aligned per Decision D-39 (`asl_signs.json` 250 retargeted signs + one-handed ASL alphabet / digits).
  - Clean fallbacks in `forSignLanguage(SignLanguage.ASL)` with signed question regeneration.
  - All 26 one-handed ASL letters verified within viewport bounds across both Child and Adult rigs in `audit.test.ts`.

---

## 3. Deaf Reviewer Backlog Items (Human Feedback / Content Sign-Off)
Recorded during the visual audit:
1. **Letter Q Pose Alignment (ISL)**: `build_signs.py` currently uses `CUP` on the base hand with right index poke. Canonical ISL/BANZSL uses a closed ring (thumb + index touching) with the right index hooking through/across. Needs Deaf signer consensus before altering content fixtures.
2. **Letter N vs. V Collision (ISL)**: Currently identical two-finger flat palm poses (`TWO` across flat palm). Differentiating V with fingers splayed requires signer validation.
3. **ASL Sign Pairings Review**: 250 PopSign retargeted signs await Deaf ASL review in the ASL tab of the portal.

---

## 4. Local Model Section (Verified Regression Gate)

> **Status**: Verified all green at commit `3e24045` (4 files / 1,217 portal tests; 45 suites / 264 Android unit tests; worktree clean).

Commands for future routine sanity gates:
```powershell
# 1. Portal formatting, linting, and build
cd C:\work\sign-setu\portal
npm run check
npm run build

# 2. Content manifest integrity
cd C:\work\sign-setu
& $env:SIGNSETU_TOOLS tools/content/build_content.py --manifest-only
git status

# 3. Kinematics retargeting test
& $env:SIGNSETU_MP tools/isl/test_retarget.py

# 4. Portal test suite
cd C:\work\sign-setu\portal
npm test

# 5. Android unit tests (forced fresh run)
cd C:\work\sign-setu
./gradlew :app:testStandardDebugUnitTest --rerun
```
