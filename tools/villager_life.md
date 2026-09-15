# Villager life asset sources and validation

The mod ships 19 original body motions, 27 original facial motions, eight user supplied
voice profiles with 144 clips, including stomach sounds, and layered speech and
thought particles. 85 item icons are copied from the installed Hytale assets or
this mod, as requested. The item manifest records each source path.

## Pose references

The following photos were visually inspected while authoring poses. They inform
gesture silhouette and contact placement; no photos or traced animations are
included in the mod.

* [Will Oliveira, shrug](https://www.pexels.com/photo/confused-man-shrugging-in-studio-setting-33715994/):
  elbows below the hands, open palms, asymmetric brow and head tilt for questioning.
* [Andrew Patrick Photo, waking and yawning](https://www.pexels.com/photo/man-yawning-and-rubbing-his-eyes-as-he-woke-up-16003598/):
  bent raised elbow, hand near the face, closed eyes, and an unhurried recovery.
* [Liliana Drew, cleaning](https://www.pexels.com/photo/a-woman-sweeping-a-floor-9462143/):
  forward lean, unequal hand heights on an angled grip, movement led by the torso.
* [Kindel Media, stomach discomfort](https://www.pexels.com/photo/close-up-photo-of-person-having-stomach-pain-7298676/):
  overlapping, staggered palms against the abdomen and a protective upper-body curl.

The local Hytale Player rig, expression atlases, animation assets, and
`BlockyModelBoundsParser`, `BlockyAnimationCache`, and `NPCEntity` source supplied
bone names, hierarchy conventions, the engine's 60 FPS timing, and restart rules.
All animation curves are newly authored. Existing mouth atlas cells support smiles,
frowns, closed lips and open vowels, so additional mouth textures were unnecessary.

## Motion implementation

Body gestures use the `Action` slot through the explicit
`Server/Item/Animations/Aetherhaven_Life_Actions.json` animation set. On the Player
rig, `Emote` resolves cosmetic emote IDs; the client rejected the previous model
animation IDs with "No emote with id". Each Action now binds `ThirdPersonFace`
to its expression or exact selected lip-sync recording: a separate lower-priority
Face request alone was suppressed while Action played. The 224 voiced variants
use body timelines extended to the facial/audio duration, holding neutral after
the body gesture settles. Sitting remains on `Status`; body files do not animate
facial nodes or legs. Zero wiggle weights avoid procedural item sway.

Prop actions explicitly neutralize `R-Attachment` position and orientation and
otherwise unauthored right-wrist rotation. Hytale's Item idle translates that
attachment by (2, -2, 2.1), which moved the prop away from the second palm when
the action omitted this channel. The grip checker now includes this lower idle
layer rather than validating actions in isolation.

`villager_life_ik.py` solves shoulder and elbow rotations with joint limits and
continuous pole preferences. Palm targets follow the head or torso for nine
contact gestures. Contact solutions are eased into and out of the authored pose,
then baked every six engine frames into ordinary `.blockyanim` assets. This is
authoring-time IK; it does not adapt to terrain or arbitrary prop placement in game.
Work grips use the held prop geometry; arbitrary third-party equipment is not collision-tested. Feet remain planted in the original rig.

`villager_life_faces.py` animates brows, pupils, eyelids and mouth cells. The lip-sync
compiler preserves those expressive curves and replaces mouth movement with cues
from the selected recording. Conversation turns wait for both the voice and gesture.
Finite emotes explicitly restart in Hytale.

## User supplied voices

The shipped audio now comes entirely from the user's 18 MP3 masters, including
separate gasps, grumbles and stomach sounds. Each contains eight ordered profiles:
Bright Female, Bright Male, Warm Female, Warm Male, Mellow Female, Mellow Male,
Gravely Male, Gravely Female. There are 144 split Ogg clips and 144 exact sound events.
The earlier synthetic voice pipelines are retired and cannot overwrite this cast.

`villager_voice_splits.json` records seven reviewed boundaries for each master.
`import_villager_voices.py` preserves internal pauses and breath sounds, trims outer
padding, applies 4 ms edge fades and leaves headroom for Vorbis encoding. It does
not change pitch or speed. Original MP3s remain untouched. The voice manifest
records source hashes, exact source ranges, output hashes and event durations.

Townsfolk use their configured gender and voice tone. Explicit full profile IDs
can override that choice. Characters without that information use a stable UUID
assignment. Conversation timing follows the imported recordings.

## Reproduction and previews

Use Python with the packages in `requirements-villager-life.txt`:

```text
python tools/import_villager_voices.py C:/Users/gchou/Downloads/Villager_Voices
python tools/generate_villager_life_assets.py
python tools/generate_villager_lip_sync.py
python tools/verify_villager_life_motion.py
python tools/verify_villager_life_props.py
python tools/preview_villager_life.py
python tools/preview_villager_faces.py
python tools/preview_villager_lip_sync.py
```

The default reference assets are in the sibling
`HytaleSourceCode/hytale-shared-source/HytaleAssets` directory. Import voices
before exporting animations so talking-face durations match the new recordings.

`build/villager-life-preview/user-voices/split-review.png` displays every master
with the eight boundaries. The audition WAV in that folder plays talk, question,
then laughter for each voice, in the user's profile order.

## Audio-driven lip synchronization

[Rhubarb Lip Sync](https://github.com/DanielSWolf/rhubarb-lip-sync) 1.14.0 analyzes
the recordings with its language-independent `phonetic` recognizer. This suits
nonsense syllables better than requiring an English transcript. Six mouth shapes
cover pressed lips, teeth/consonants, open vowels, wide vowels, rounded vowels and
puckered lips, plus a relaxed closed mouth for pauses. Recognition is approximate;
the JSON cues can be corrected by hand without changing audio.

Install the official Windows release under
`build/rhubarb/Rhubarb-Lip-Sync-1.14.0-Windows`. The downloaded archive's SHA-256 is
`62fa416a8d5e382a3828ee4bef358ce520d0b4cabdeaea75a7ac266d098d1fe3`.
The analyzer is a build tool; its binary and acoustic model are not shipped.

`Server/Aetherhaven/VillagerLipSync/<clip>.json` stores the audio hash and
`mouthCues` with start/end seconds and shape letters. Unchanged recordings preserve
manual edits. `--reanalyze` replaces cues; `--compile-only` exports existing cues.
All 136 vocal clips have timelines. The eight stomach clips have no mouth motion.
The compiler creates 208 expression variants so the same spoken phrase can match
different gestures while keeping exactly the same mouth timing. Laughing uses
smiling versions of the open mouth shapes.

At runtime, `VillagerLifeSpeech` chooses one entry from
`defaults/villager_life_playback.json`. That entry binds an exact single-file sound
event to its corresponding facial animation and duration. Sound and face start in
the same deferred world update. There is no runtime audio decoding, recognizer,
per-syllable network traffic or independent sound randomization.

After installing the analyzer, the import/export/check pipeline can be run with:

```text
python tools/rebuild_villager_life.py C:/Users/gchou/Downloads/Villager_Voices
```

The synchronized MP4 preview uses `imageio-ffmpeg==0.6.0` as an optional build
dependency. It renders the exported curves and muxes the actual Ogg recordings.

## Held props and revised acting

The laugh now holds both sides with a partial backward lean, a broad smiling mouth
and alert eyes. The [Anton Vierietin laugh photo](https://www.pixtastock.com/photo/101423647)
was visually inspected for the lifted head, sideways tilt and broad smile. No photo
is shipped. Reading eyes track slowly across lines and quickly return to the next.

Six original prop models cover an open book, broom, crafting mallet, wooden spoon,
small plant and polished stone. The book uses full Hytale spellbook atlas regions
with writing, leather grain and painted shadows, plus standard model shading.
It is 18 percent wider and deeper, with gold corner pieces and a bookmark. Its
attachment sits above the palms rather than intersecting the page surface.
Other props reuse opaque color samples from existing Hytale atlases. Prop geometry
is original. Props are actual equipped
items, and the animation includes their attachment transforms. The chef uses the
spoon; other crafting uses the mallet. Existing mining, chopping, watering and
smithing tools remain on their work equipment paths. Temporary props clear when
the action or POI ends without clearing unrelated items.

Read and Sweep share hand contact targets with the held geometry. The broom tilts
outward during entry and recovery to clear the floor. The prop checker reconstructs
the exported transforms at every engine frame and verifies both grips and ground
clearance. The motion previews render those same models and their actual sampled
material colors on the local Player rig.

## Checks and remaining visual verification

`VillagerLifeAssetsTest` decodes particles and sound events with the actual Hytale
codecs, checks asset references, engine timing, and neutral returns. Policy and
personality tests cover need priority, fun limits, stable voices, weighted interests
and legacy data. The autonomy and scheduling suite passes.

Every animation node must include `position`, `orientation`, `shapeStretch`,
`shapeVisible`, and `shapeUvOffset`, using empty arrays for unused channels.
`villager_blockyanim.complete_channels` supplies these in both exporters. The
client rejected sparse nodes with NullReferenceException during loading; the
server's animation codec only reads duration and did not catch this. A separate
asset test checks all 1,362 body, expression, lip-sync and extended action files.
All bubble layers use scale 0.28. Original copied icons remain in `Items`; derived
`Items/Centered` textures fit their visible bounds inside a 52 by 48 pixel area
centered at (64, 51) in the original 128 pixel artwork. All layers are then placed
unchanged at offset (93, 18) on a transparent 256 pixel canvas. The tail tip at
(35, 110) becomes the billboard pivot (128, 128), while icons stay centered in
the cloud at (157, 69). This works from all camera angles without world offsets.
Bubble placement uses the higher of collision bounds and the visible eye height
plus head clearance, with the tail tip another 0.2 blocks above that.

The offline motion checker samples every engine frame, checks planted feet,
limits sudden quaternion transitions and verifies active hand contacts within
0.5 model units. Previews use the local rig and exported curves; they are not
captures from the Hytale client. Client blending, perceptual lip-sync accuracy and
multiplayer behavior still require an in-game playtest.

## September 14 follow-up

Leisure keeps the saved `park` schedule symbol for compatibility, but clears the
park-only commute and selects town-wide FUN activities. A recreation search has
a 65 percent conversation weight; failed searches leave POI autonomy available.
Casual same-building searches happen independently of low fun. Search retry times
do not reserve partners; only active acting and the completed-chat cooldown do.
Seated pairs retain their actual mount and stationary work pairs retain their POIs.
Town role separation pushes are disabled, and ending a standing activity no longer
ground-snaps its transform. Seat alignment reads the occupant's allocated mount
point instead of the first point on a bench.

The book is 38.4 model units wide. Its attachment is rigid; the supporting arm is
solved against the opposite grip every three frames. Reading and stretch previews
are in `build/villager-life-preview/reading-stretch-revision.png`. These are offline
rig renders, not a client playtest. Reference images used for this revision:
- [Two-handed book support](https://www.shopify.com/stock-photos/photos/torso-of-a-person-in-a-red-sweater-holding-open-a-book)
- [Overhead stretch silhouette](https://tommorrison.uk/blog/the-non-negotiables-8-ways-to-keep-your-body-moving-well)

Eating uses Hytale's SFX_Consume_Bread every 3.2 seconds during meal use. Both
Sleepy and Stretch select the villager's own yawn recording and matching mouth
cues. Guild-hall idle actions include reading, eating, yawning, stretching and
fidgeting, preserve seated Status, and wait for the selected recording to finish.

## Voiced idle, work and dialogue responses

The refreshed script provides four Idle, four Work and two Thinking variations,
alongside three Talk variations, two of each other voiced reaction and one
Stomach clip per voice. Eleven profiles provide 308 recordings. `villager_voice_splits.json`
preserves the reviewed boundaries and the import manifest records source hashes
and timestamps. ElevenLabs allows ten distinct voices per dialogue generation,
so the ten human profiles share each master and RustyRobot has separate masters.
Split entries specify the ordered profiles and bind reviewed cuts to the source
checksum. Run the importer with `--stage-only` to validate a set before publishing.
Idle never selects Work; work chooses either category. Tool swings retain their
body animation while the recording drives Face. Ponder uses Thinking, and reading
occasionally chooses Thinking between quieter Idle/Work utterances.

Player dialogue now uses these recordings instead of letter speech blips. Both a
dialogue node and an individual choice accept `speechClip`. A choice overrides the
destination node's cue; ordinary rebuilds do not restart speech. Rapid choices
replace a queued response and wait for the current recording/action to finish.
The existing per-player speech toggle and volume setting still apply.

```json
{
  "text": "your.dialogue.choice.text",
  "next": "quest_offer",
  "speechClip": "Question"
}
```

Categories: Talk, Greet, Agree, Question, Thinking, Laugh, Gasp, Grumble, Groan,
Yawn, Sigh, Idle, Work, None. Thinking pairs with Ponder; Question pairs with the
question gesture. Existing assets explicitly mark 42 quest offers and 104 quest
responses. Unknown categories fall back to Talk. `speechVoiceId` on villager and
townsfolk definitions selects BrightFemale, BrightMale, WarmFemale, WarmMale,
MellowFemale, MellowMale, GravelyMale, GravelyFemale, OldMale, OldFemale or RustyRobot,
shared by player dialogue and NPC conversations. Existing tone names still map to
consistent profiles. Add-ons can register further recording families under
`Server/Aetherhaven/VoiceClips/`; see `docs/modding/crossmod-integration.md`.

ReadLoop keeps both hands on the book across its closed seam. Voiced reading
actions hold their final pose until the silent loop resumes, and guild readers
keep reading for 18–26 seconds. The exported loop is checked against both palms
at every engine frame. The six-phase offline preview is
`build/villager-life-preview/reading-loop-check.png`.

Conversation reservations can wait for a partner's current work gesture to finish
instead of requiring simultaneous gaps. Same-building searches run every 18
seconds, unsuccessful partner searches retry after five seconds, and nearby
partners behind walls are excluded. Navigation compares the full Hytale
State.SubState name correctly. Emote cleanup preserves the quiet stance rather
than briefly enabling Idle wandering between actions.

Unhoused residents periodically show Home thoughts. Local weather is read from
the current environment/forced Weather resource; rain and thunderstorms add Rain
thoughts and conversation topics. Visitors do not receive the missing-house cue.

## Fixed voice cast and personal speech settings

All 18 named villagers and 62 townsfolk now have explicit profiles. See
`tools/villager_voice_cast.md` for the full assignment list. Legacy or missing
profiles no longer use the spawned entity UUID, so moving to another world cannot
change the recording identity. Visitor/rescue role fallbacks also resolve the
named villager definition. Goblin townsfolk always resolve to a Gravely recording.

There are 24 profile names: each original profile plus `Lower` and `Higher`.
For example, `WarmMaleLower` uses Warm Male recordings two semitones lower.
Pitch is fixed per character, never jittered. Hytale changes audio playback rate
with pitch, so generated Action tables and Face bindings use the same rate and
the runtime scales the completion timers. No duplicate OGG or blockyanim files
are needed. Plain profiles still use their original pitch.

Town Journal personal settings now include Nearby villager volume (0–100%) and
Random chatter (0–200%). Chatter frequency applies only to what that player hears
from random idle/work/ponder/yawn activity. It does not change shared animation,
conversation selection or fun replenishment. NPC conversations use the volume
setting but bypass the random-chatter filter; player dialogue retains its own
volume and enable switch. 0% chatter is off, 100% uses a 12-second minimum interval
per NPC, and 200% uses six seconds. Activity opportunities can be less frequent.
Preferences persist on the player entity, with defaults for older saves.

Dialogue choices select uniformly from recordings excluding the last recording
heard by that player. This includes switching between Greet and Explain, which
share Talk recordings. The single Thinking recording alternates with a silent
ponder on consecutive requests; an `All_Thinking_2.mp3` master in the same eight
voice order would allow spoken alternatives instead.

Prowl uses a custom integrated face rig and an 18 by 8 mouth strip in the original
texture at x=494, with the neutral cell at y=24. `villager_prowl_faces.py` remaps
mouth UV cues to that strip for both Action-priority faces and standalone Face
tracks, including mood restoration and pitched profiles. The model geometry and
texture are preserved. The face support check explicitly recognizes this rig.
Elias Thornwood now uses WarmMaleLower for a deeper voice.

Native race faces are generated by `villager_creature_faces.py`, also called by
the lip-sync compiler. Trork, feran, klops, slothian and skeleton jaws follow the
same phonetic cues as the human mouth shapes, with short eased transitions and
native hinge limits. Eyelids, pupils and brows use only bones present in each
resident's actual rig and attachments. Klops has one eye and different lid scales;
Tumble's eyebrows use separate native bone names. Kweebecs and the outlander use
their existing compatible mouth atlases. Mannequins have no facial geometry and
continue using body gestures. No native models or textures are changed.

Both standalone Face bindings and Action-priority faces are retargeted. The body
tracks, voice recordings and pitch timing remain shared. To add a native resident,
include its model in `RIGS`, check its attachments, and regenerate; a new rig also
needs a routing entry in `NpcFaceVisuals`. `defaults/villager_creature_faces.json`
records the generated model/rig mappings and facial nodes.

All inherited idle animation sound bindings are removed on the mod's Human base,
Villager and Townsfolk models, regardless of race or sound category. This includes
idle variants such as GlideIdle. Klops/trork movement grunts, trork sleep vocals,
and skeleton search vocals are also muted locally. Animation files, speeds,
footstep intervals, injury/combat sounds and the wild vanilla models are preserved.
The compiler reapplies these local overrides after face generation.

`preview_creature_faces.py` validates generated tracks against the native rigs
and renders closed/open head poses to `build/villager-life-preview/creature-faces.png`.
The offline preview omits hair, hats and armor to expose the facial hinges. It is
a geometry check, not a substitute for checking the animation in the Hytale client.

Romantic conversations are an occasional alternative (15% of successfully paired
chats). Their outcome is chosen once for the shared session: 60% return affection,
40% decline. The initiator shows a heart speech bubble and the same Hearts effect
as a loved gift; the other villager responds in a separate voiced turn. Acceptance
ends with both showing hearts. Rejection ends with the initiator's downcast Bored
pose and a Groan recording, now compiled with synchronized lips for that pose on
all supported rigs and pitches. The listener reacts silently, so voices do not
overlap. Each beat waits for the longest body/face/audio duration before advancing,
including the final reaction. The normal availability, interruption, fun refill,
and social cooldown rules still apply. This does not create persistent couples or
change player gift/reputation records. Balance and authored beats live in
`VillagerRomance.java`.

Shop work selection treats old read/craft tags as generic placeholders for desk
residents, so saved shops receive the job-specific actions too. Standing merchants,
chefs, florists, furniture merchants, crystal keepers and pyrotechnics choose their
normal work 60% of the time, sweeping 25%, and reading 15%. Innkeepers normally
sweep and use inspection for the 25% variation. Explicit sweep/inspect/tend tags
remain dedicated activities. Seats and mounted NPCs do not select sweeping, and
meal, sleep, leisure, and mining/forging/chopping activities keep their own behavior.
Garren Vale uses WarmMaleLower.
