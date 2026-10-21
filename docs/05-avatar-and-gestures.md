# 05. Avatar and Gesture Design

Status: Accepted. Replaces the earlier skeleton avatar. The app draws both characters in code (§5.7) and plays authored and recorded motions.

## 1. Answers in brief

| Question | Answer |
| --- | --- |
| Can we build an avatar? | Yes, in-house, with free tools. The rendering is the easy part; the cost is authoring and reviewing every sign and gesture. Another group is reported to be building an ISL avatar; this project does not depend on it. |
| Is there a prebuilt one we can use? | No usable one (§2). The characters are original; open-source tools serve the pipeline ([open source](09-open-source.md)). |
| Indian signs differ from general ones. | Correct. ISL is a separate language from ASL and BSL (different vocabulary; two-handed fingerspelling against ASL's one-handed), and ISL has regional varieties (often grouped as Mumbai-Delhi, Kolkata and Bangalore-Chennai-Hyderabad). ASL is also used in some Bengaluru schools. No foreign avatar or sign asset can be reused for vocabulary. |
| Small children do not know signs. | Correct, and this applies to most deaf children too. Children get a separate **gesture lexicon** of iconic, culturally common gestures (bindi or sindoor for mother, moustache for father, roof for home, hand on chest for me), with several variants per concept. |
| Two avatars, a child hero and an adult? | Yes: two original characters on one shared rig, playing one motion library. Copying Shin-chan or Nobita is not possible (§4). |

## 2. What already exists, and why none of it fits

| Product or project | What it is | Why not |
| --- | --- | --- |
| CWASA / JASigning (UEA) with SiGML and HamNoSys | Research signing-avatar engine driven by sign notation | Research licence; ISL coverage negligible; every sign must be written in HamNoSys by an expert |
| Student projects (Unity or Blender ISL avatars, "text to ISL" pipelines) | Prototypes | Unreviewed ISL, word-for-word grammar, no licence clarity, unmaintained |
| Ishaara | Commercial browser text-to-ISL avatar | Proprietary web service; would send officer text to a third party; ISL quality not independently validated |
| sign.mt | Open multilingual sign translation app | CC BY-NC-SA licence; no ISL model found |
| Google SignGemma | Sign-to-text model | ASL only, recognition not generation; weights not openly released |
| Ready Player Me | Hosted avatar platform | Shut down after its sale to Netflix; shows the risk of depending on hosted avatar services |
| Hand Talk, Signapse and similar | Commercial signing avatars | ASL, BSL or Libras only; proprietary |

Anything hosted can disappear, and anything that generates ISL automatically produces unreviewed output. Both are unacceptable here.

## 3. Two lexicons

The avatar never improvises. It plays entries from reviewed lexicons.

| | ISL lexicon | Gesture lexicon |
| --- | --- | --- |
| For | Deaf people who know ISL (adults, older students) | Young children, deaf children without ISL, anyone non-verbal |
| Source | 262 signs retargeted from INCLUDE's Deaf signers; authored drafts for lesson words INCLUDE lacks; the ISLRTC dictionary (10,000 terms) as reference [permission to confirm] | Special educators, child-welfare staff and parents' common gestures; cross-checked against ISL, since many iconic gestures overlap ISL signs |
| Reviewer | Qualified Deaf ISL reviewer; regional reviewer per state | Child-welfare reviewer plus educators from at least three regions and different communities |
| Variants | Regional variant tagged per entry | Several variants per concept, tagged by community and region; never one variant alone |
| Grammar | Phrase bank of reviewed sentences with slots; typed text in draft ISL order | Single concepts; the question is carried by pointing, the pictures and the character's face |
| Shown with | Caption and picture | Picture card, and audio for hearing children |

### Gesture lexicon starter list

Every entry below is a **candidate**. None is final until the review above approves it.

| Concept | Candidate variants | Notes and risks |
| --- | --- | --- |
| Me | Flat hand on own chest; index finger to own chest | A hand on the heart can also read as "love" or "promise"; show with the picture |
| You | Open palm towards the person | Finger-pointing at a person is rude in many homes |
| Mother | Fingertip to the centre of the forehead (bindi); finger along the hair parting (sindoor); hand drawing a head covering (dupatta, ghunghat, hijab); touch at the nose (nose ring) | Bindi and sindoor are Hindu, mainly married-women markers; many communities use neither; always offer several and the picture |
| Father | Stroking a moustache; stroking a beard; hands circling the head (turban); hand on head (cap) | Many fathers have none of these; same caution |
| Grandparent | Bent back with a walking stick | Avoid mocking portrayal |
| Baby | Rocking cradled arms | |
| Brother or sister | Tying a thread on the wrist (rakhi); hand at a height (taller or shorter than me) | Rakhi is widely known but community-specific |
| Home | Two hands meeting in a roof shape | |
| Eat, food | Bunched fingertips to the mouth | Very widely understood in India |
| Hungry | Rubbing the belly | |
| Water, drink | Thumb to the mouth from a closed fist (pouring); cupped hand to the mouth | |
| Toilet | Raised little finger (common school gesture for urination) | Embarrassing for some children; show the picture card first |
| Sleep, tired | Palms together under a tilted head | |
| Pain, hurt | Wince and rub or point to the body part | Use with the body-parts board |
| Cold, hot | Hug self and shiver; fan the face | |
| Scared | Hands over the face; hugging self | Sensitive; not on the first screen |
| Yes | Clear vertical nod | Never the sideways head wobble, never thumbs up (§5.7) |
| No | Head shake with an index-finger wag | |
| Come | Indian beckon: palm down, fingers sweeping in | The Western palm-up beckon can be read as rude |
| Go, stop | Palm pushing away | Never an open palm for Stop on the stage |
| Wait | Fingertips bunched pointing up, small shake | Same gesture also means "what?"; use only with caption |
| Walk | Two fingers walking on the palm | |
| Train | Forearm circling beside the body | |
| Bus, car | Hands on a steering wheel | |
| Phone | Thumb and little finger at the ear | |
| Money | Thumb rubbing the fingertips | |
| School | Writing on the palm | |
| Police | Salute | Children may fear police; the officer introduces themself with this and the picture card |
| Doctor | Two fingers from the ears to the chest (stethoscope) | Avoid the injection gesture, which frightens children |
| Where? | Palms up, raised eyebrows, small shrug | The face carries the question |

## 4. The two characters

| | Child character | Adult character |
| --- | --- | --- |
| For | Young child, Other language and Not sure profiles | ISL user profile |
| Look | Original Indian child in a simple style, large head and eyes, expressive brows and mouth (which carry facial grammar), five-fingered hands drawn 1.85 times the rig's hand unit | Neutral stylised adult framed from head to hips; plain dark top so hands stand out; simplified face with calm lidded eyes; hands 2.0 times the hand unit |
| Face | Big, readable expressions | Eyebrows, eyes, mouth and head movement; this is ISL grammar, not decoration |
| Clothes | Plain, everyday, regionally neutral; no school or police uniform, no religious markers | Plain dark V-neck top; no uniform |
| Lexicon | Gesture lexicon, and ISL signs when Signs is on | ISL lexicon and phrase bank |
| Background | Plain, calm, warm | Plain, contrasting with skin and clothes |

**Why not Shin-chan or Nobita.** Crayon Shin-chan and Doraemon (with Nobita) are protected characters owned by their Japanese rights holders, who enforce them. A "replica" infringes copyright and, for names and likeness, trademark rights, whatever the purpose. A police app cannot ship it without a licence, and such a licence is unlikely. Shin-chan was also taken off air in India after complaints about its content and returned only in an edited form, a reputational risk for a police app. The same applies to Chhota Bheem and other commercial characters. What is wanted from them, a friendly, familiar cartoon child, is achievable with an original character in a similar simple style; the custom parts belong to the department.

**No blank face for adults.** A featureless "mask" face was rejected: without eyebrows and mouth, ISL questions read as statements and negation is lost. The adult's simplified face still moves its brows, eyes and mouth, keeps the neutrality a mask was meant to give, avoids the uncanny valley, and resembles no real person.

**Character review.** A child psychologist reviews both characters, and they are tested with children (§8): a character that reads as a toy invites play, and one that reads as a stranger may frighten.

## 5. Technical design

### 5.1 One rig, two builds, one motion library

Both characters share one 2D upper-body rig (`ui/avatar/Rig.kt`), so every motion plays on either character. The rig is defined in §5.7.

### 5.2 Lexicon entries

Gesture lookup entries (`lexicon.json`):

```
LexiconEntry
  id          "mother.bindi"
  concept     "mother"
  card        picture card id
  locations   BodyLocation[]   HEAD, FOREHEAD, EYES, NOSE, EAR, FACE, MOUTH, CHIN, CHEST, BELLY, ARM, HANDS, AWAY
  actions     HandAction[]     TOUCH, STROKE, POINT, CIRCLE, FLAT_HAND, FIST, BOTH_HANDS, MOVE, HEAD_MOVE
  label{lang}
  how{lang}   one-line description of the movement
```

The motion for an entry is the gesture with the same id. Review state lives in the content review record, not in the entry.

### 5.3 Authoring pipelines

| Source | Pipeline | Output |
| --- | --- | --- |
| Everyday gestures | Keyframed by hand against named anchors | `gestures.json` (42) |
| Alphabet, digits, draft word signs | Generated by `tools/content/build_signs.py`, left hand as the base | `signs.json` (26 letters, 10 digits, 71 word signs) |
| Recorded ISL signs | `tools/isl/retarget.py` over the INCLUDE pose release: cleanest recording per word, gaps filled, Gaussian smoothing (sigma 1.2 frames), wrists placed by shoulders and face, per-finger curls, palm roll and angle, elbow offsets; keys at ends, pauses and turns, at least every 0.5 s | `isl_signs.json` (262) |

Every recorded performer signs the consent form; raw footage is kept only until the motion is approved, and keyframes contain no image of the performer.

### 5.4 Runtime

Both characters are drawn live on the 2D rig, playing authored and recorded keyframes with jointed fingers and facial expression beside the picture card. No video clips or 3D renderer ship; the rig needs no dependency beyond Compose.

### 5.5 Size

Motions are JSON keyframes: `gestures.json`, `signs.json` and `isl_signs.json` together are a small fraction of the APK. Pre-rendered clips would cost about 75 KB each (480 by 480, H.264, about 400 kbit/s, 1.5 s), so the 411 current motions on two characters would add about 60 MB, beyond the 100 MB size target.

### 5.6 Playback rules

- Plays only on an officer action; never loops on its own; replay and slow-speed buttons. The subject sets the signing speed (½×, ¾×, 1×) for the session, in memory only; a young child starts at ¾× at most. Tapping a word in typed text replays that word alone at half speed; tapping a question replays it.
- The caption and picture are shown first; the motion plays with them, never alone.
- If a motion is missing, the picture and caption stand alone and the officer sees "gesture not available".

### 5.7 The code-drawn rig

- **Space.** Each character lives in a 200 by 200 unit space, y down, facing the viewer; the character's right hand appears on the viewer's left. The stage shows the view box x −10 to 210, y 0 to 258 (to the hips). Proportions differ per character (`ChildBuild`, `AdultBuild`).
- **Arms.** A key gives a target at a named anchor (`forehead`, `mouth`, `chin`, `cheek`, `ear`, `chest`, `belly`, `shoulder`, `front`, `hip`, ...) with an offset, reached by the wrist, palm, index tip or thumb. The elbow comes from a two-bone solve (out, in or down) unless the key carries a recorded elbow (`ex`, `ey`).
- **Hands.** Five fingers of three joints. A named shape (`FLAT`, `POINT`, `BUNCH`, `PINKY`, `Y`, `CUP`, `PINCH`, counting shapes and others) or a measured `c:` shape sets each finger's curl, the thumb across the palm and the spread; `roll` shows the palm, the thumb side or the back of the hand. Shapes blend between keys.
- **Face and body.** Brows (raise, worry, frown), eyes (open, smile, blink), mouth (smile, open, round), head tilt, nod and turn, body lean, shrug, hunch and shiver. Yes-no questions raise the brows and wh-questions furrow them.
- **Playback.** A motion eases in from rest over 0.35 s and back over 0.45 s; each authored move lands slightly early and holds, and a free move overshoots and settles. Recorded keys less than 0.35 s apart are joined by a Catmull-Rom curve, so the hands pass through them. After a question's motion the character glances down at the answers. It breathes and blinks between motions unless "remove animations" is on, and stays still during a sign window.
- **Phrases.** A phrase-bank sentence chains its signs ("you", the item, the asking pose) into one motion; each part starts from the pose it has alone, so a reviewed sign never changes inside a sentence. Each slot carries its own complete sentence in every language.
- **Two-handed contact.** A hand can be placed on the other hand: its wrist, palm centre, thumb or any fingertip (`index_opp`, `ring_opp`, ...). The touched hand is solved first, so a fingertip lands on a fingertip on both characters.
- **Typed text.** Words are looked up in `words.json` and played through one lexicon: with Signs on, the recorded sign, then the authored sign, then the everyday gesture; with Gestures on, the gesture; otherwise the word's letters (Hindi in Latin letters) or a picture beat. With Signs on, words play in draft ISL order: time words first, then the rest as typed, then negation, then the question word. The caption lights the word being played and a tile beside the character shows a spelled word's current letter.
- **Recorded signs.** 262 INCLUDE words, with measured hand shapes and elbows. The same lookup serves typed text, questions without a gesture of their own, sign-back of a reader label and the officer's ISL dictionary. Homonyms are offered in the dictionary only.
- **Look.** Every arm bone and sleeve has a dark rim, so an arm crossing the body or face stays visible. A palm facing the viewer uses a lighter tone; straight fingers seen from the back show nails. Everything is drawn in code, with no image assets.
- **Gestures avoided.** Thumb-up, thumb-down and an open palm are never used for yes, no or stop; Stop on the stage is an octagonal sign.
- **Review.** Debug builds include a gallery of every gesture on both characters, frozen at the moment of contact (`GalleryActivity`), and the reviewer portal plays the same motions through a TypeScript port of the rig checked against `Rig.kt`. Every entry remains a candidate until reviewed.

## 6. Officer gesture lookup

When the subject makes a gesture the officer does not understand:

1. Officer taps **They gestured**.
2. **Where** did the hand go? Body outline: head, forehead, eyes, nose, ear, face, mouth, chin, chest, belly, arm, hands, away from the body.
3. **What** did it do? Touch, stroke, point, circle, flat hand, fist, both hands, move, head movement.
4. The app lists matching lexicon entries, both matched first, then place only, then action only, each with its picture and pose.
5. Officer picks the likely one and taps **Show**: the subject sees the gesture and picture with "Is this it?" and Yes and No, or a choice among two or three pictures where the item has others.
6. Only a confirmed match is logged as the subject's answer; unconfirmed lookups are logged as unclear.

This works offline, needs no model, and works with home signs, because the confirmation step catches mismatches. Signed answers are read separately by the on-device sign reader ([hand model integration](11-hand-model-integration.md)); its readings are proposals and become the subject's only when the subject picks the meaning. The body outline is an officer tool; the subject is never shown a body map in the default flow.

## 7. Fallback chain

**Officer to subject**, use the first that works:

1. A human interpreter on a call (ISL users; **Call an interpreter**).
2. A character motion from the reviewed lexicon (recorded or authored sign, gesture, or phrase).
3. Picture card with caption, plus recorded audio for hearing subjects.
4. The officer performs the gesture, copying it from the card on their panel or the printed quick card.

**Subject to officer:** picture card, Yes or No, a lesson-sheet input, drawing, writing, a signed answer or account read on the phone and confirmed by the subject's pick, gesture with lookup and show-back, interpreter.

## 8. Validation

| What | Who | Pass mark [confirm with reviewers] |
| --- | --- | --- |
| Each ISL sign and phrase | Deaf ISL reviewer; then 5 or more Deaf adults from at least two regions | 80% understand the phrase without the caption |
| Each gesture entry | Child-welfare reviewer and educators; then children in deaf and hearing schools | 70% of children pick the right picture after seeing the gesture |
| Both characters | Child psychologist; then children | No child shows fear; most children prefer the character to no character |
| Officer gesture lookup | Officers in UAT | Right meaning in the top three for 80% of scripted gestures |

Reviewers use the reviewer portal (`portal/`): every motion plays on both characters with speed and scrub, and verdicts export to the content review record.


## 9. Building it in-house with no budget

The characters and their motions are built with free tools chosen so that no 3D modelling skill is needed.

- **Characters and authored motions.** Drawn and keyframed in code (§5.7); simple gestures take three to five keys each.
- **Recorded signs.** Retargeted from the INCLUDE pose release (§5.3); further signs need a volunteer Deaf signer, filmed after signing the consent form, with landmarks extracted by MediaPipe and retargeted by the same script.
- **People.** Performers and reviewers are volunteers from Deaf associations, deaf schools and child-welfare bodies, recruited through the department.

## 10. Risks

| Risk | Mitigation |
| --- | --- |
| A gesture means something different, or something rude, in some community | Multiple variants, community reviewers, picture always shown, show-back confirmation |
| Character resembles a commercial cartoon | Original design, no reference images of commercial characters during design, legal check of the final design |
| Children treat the character as a game | Psychologist review; character only plays on officer action; no game elements |
| Facial grammar too subtle on a small screen | Exaggerated expressions on the child character; brows mark questions |
| A retargeted sign misleads because the 2D rig flattens depth | Deaf review of every recorded sign; fingers pointing at the camera read as bent, noted for reviewers |
| Performer withdraws consent | Keyframes are re-performed by another performer and re-reviewed |
| Volunteer performers and reviewers are not available | Recruit through the department; picture cards and gesture lookup do not depend on new motions |

## Sources

- ISL and ASL differences, ISL varieties: https://home.csulb.edu/~lemaster/Asia/ASIA%20India.pdf, https://cse.iitk.ac.in/users/amit/books/zeshan-2000-sign-language-in.html, https://caravanmagazine.in/lede/on-the-other-hand-american-sign-language-bengaluru, https://atypicaladvantage.in/atypical-blog/post/difference-between-american-and-indian-sign-language
- ISLRTC dictionary: https://www.outlookindia.com/amp/story/national/from-just-3-000-to-10-000-words-now-indian-sign-language-dictionary-grows-to-help-hearing-impaired-news-322677
- INCLUDE and OpenHands: https://zenodo.org/records/4010759, https://zenodo.org/records/6674324
- Existing avatars: https://vh.cmp.uea.ac.uk/index.php/CWA_Signing_Avatars_Demos, https://aclweb.org/anthology/2020.lrec-1.739.pdf, https://www.ishaara.app/, https://arxiv.org/html/2310.05064v2, https://multilingual.com/google-signgemma-on-device-asl-translation/, https://www.pocketgamer.biz/netflix-acquires-avatar-creation-platform-ready-player-me
- Avatar evidence: https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2022.730917/full, https://www.researchgate.net/publication/254007035_Assessing_the_deaf_user_perspective_on_sign_language_avatars
- Character rights and history: https://www.marcus-spectrum.com/Blog/files/DoraemonIP413.html, https://deccanchronicle.com/entertainment/shinchan-was-lewd-they-would-censor-it-all-says-dubbing-artist-1827765, https://iopn.library.illinois.edu/journals/jams/article/view/2124
- Gesture ambiguity: https://folklore.usc.edu/confusing-gestures/, https://madrascourier.com/?p=26828
- Tools: https://github.com/vrm-c/vrm-specification, https://github.com/google/filament, https://github.com/saturday06/VRM-Addon-for-Blender
- VRoid Studio terms: https://vroid.com/en/studio/guidelines
