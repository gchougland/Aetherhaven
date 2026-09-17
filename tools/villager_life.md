# Villager life asset sources and validation

The mod ships original body and facial motions, eleven user supplied
voice profiles with 308 clips, including stomach sounds, and layered speech and
thought particles. 85 item icons are copied from the installed Hytale assets or
this mod, as requested. The item manifest records each source path.

## Pose references

The following photos were visually inspected while authoring poses. They inform
gesture silhouette and contact placement; no photos or traced animations are
included in the mod.

* [BulgarianPod101, greeting wave](https://www.bulgarianpod101.com/blog/2019/08/16/bulgarian-body-gestures/):
  raised outward elbow and open palm. `villager_life_greeting.py` uses constrained
  two-bone IK for two broad forearm sweeps, keeping the wrist aligned with the arm.
  `verify_villager_greeting.py` checks arm travel, wrist stability, and body clearance.

* [Will Oliveira, shrug](https://www.pexels.com/photo/confused-man-shrugging-in-studio-setting-33715994/):
  elbows below the hands, open palms, asymmetric brow and head tilt for questioning.
* [Andrew Patrick Photo, waking and yawning](https://www.pexels.com/photo/man-yawning-and-rubbing-his-eyes-as-he-woke-up-16003598/):
  bent raised elbow, hand near the face, closed eyes, and an unhurried recovery.
* [Liliana Drew, cleaning](https://www.pexels.com/photo/a-woman-sweeping-a-floor-9462143/):
  forward lean, unequal hand heights on an angled grip, movement led by the torso.
* [Kindel Media, stomach discomfort](https://www.pexels.com/photo/close-up-photo-of-person-having-stomach-pain-7298676/):
  overlapping, staggered palms against the abdomen and a protective upper-body curl.
* [Open-palm product presentation](https://www.pngkey.com/detail/u2e6o0q8a9q8u2t4_slide-5-model-holding-product/):
  inspected in the browser for ShowItem: bent elbow below the raised palm, a relaxed
  opposite shoulder, and head/gaze turned toward the presented object. The authored
  motion reaches forward toward the conversation partner and returns slowly.

The local Hytale Player rig, expression atlases, animation assets, and
`BlockyModelBoundsParser`, `BlockyAnimationCache`, and `NPCEntity` source supplied
bone names, hierarchy conventions, the engine's 60 FPS timing, and restart rules.
All animation curves are newly authored. Existing mouth atlas cells support smiles,
frowns, closed lips and open vowels, so additional mouth textures were unnecessary.

## Motion implementation

Body gestures use the `Action` slot through the explicit
`Server/Item/Animations/Aetherhaven_Life_Actions.json` animation set. On the Player
rig, `Emote` resolves cosmetic emote IDs; the client rejected the previous model
animation IDs with "No emote with id". Silent Actions pair a full expression through
`ThirdPersonFace`. Voiced `<gesture>_Speech` Actions pair only eyes and brows;
`VillagerMouthPlayback` sequences reusable mouth poses on `ServerAction`, the
dedicated overlay used by Hytale's trigger-volume animations. `Face` is also
managed by item Action expressions; replacing it for every syllable interrupted
the eyes/brows and hid mouth poses. Speech reserves the mood timer without playing
another Face animation. The overlay contains only Mouth/Jaw tracks and yields
to unrelated triggered animations; cleanup only stops an overlay it still owns.
These tracks own disjoint bones. Body files retain natural timing and never own
facial nodes or legs; seated Status animations remain intact. Reading, mixing and
sweeping keep looping, including during speech. Pitch changes cue timing, not body speed.

Held activities preserve each native item's existing held-idle attachment transform,
including (2, -2, 2.1) for the grimoire. IK adjusts the arms around that fixed grip.
Reading exports the native Book-Top/Book-Bot articulation to the
`FirstPerson` channel as well as the body timeline, matching native spellbook
action bindings. NPC character channels, including the attempted `UsePlayerAnimations`
override, did not open the held mesh in the client. The book now uses its own
`Animation` binding to `Items/Animations/Aetherhaven/Life/Book_Open.blockyanim`,
a two-key static pose using the same hinge angles. Item animations require an
item-compatible root; character action channels require `Characters/` or `NPC/`.
The existing character timelines remain available to companion mods. Reading checks the printed page's top direction, upward-facing surfaces,
supporting-hand contact and clearance from the moving head throughout the loop.

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
These editable source files are excluded from the jar; runtime uses the single
compiled playback manifest, avoiding 297 redundant JSON assets.
The eleven profiles provide 308 clips: 297 vocal recordings with mouth timing and
11 stomach sounds without mouth motion. The compact compiler writes one list of
`[milliseconds, shape]` pairs per vocal recording in the bundled playback manifest.
Six shared mouth poses plus three laughing variants replace all per-recording face
files. Prowl uses the player atlas. Other rigs retarget these small poses to their
native mouths/jaws, sharing identical results across models.

At runtime, `VillagerLifeSpeech` selects the exact sound and its cue list together.
`VillagerMouthPlayback` tracks only currently speaking entities and sends an overlay
animation update when the shape changes, at most once per 80 ms. Slow ticks skip
expired cues instead of replaying them. Interruptions and clip completion close
the mouth; respawns do not resume old speech. Pitch shifts scale cue timestamps.
There is no runtime audio decoding or speech recognizer. Unlike the former baked
clips, this does send bounded mouth-shape updates while a character is speaking.

Add-on voice manifests can provide the same optional `mouthCues` field, beginning
at zero, using A–F, and ending closed (A). Legacy add-on facial timelines remain
supported when the field is absent. Shared Action table aliases use `Parent`
instead of copying definitions for every pitch or compatible skeleton.

After installing the analyzer, the import/export/check pipeline can be run with:

```text
python tools/rebuild_villager_life.py C:/Users/gchou/Downloads/Villager_Voices
```

The synchronized MP4 preview uses `imageio-ffmpeg==0.6.0` as an optional build
dependency. It renders the exported curves and muxes the actual Ogg recordings.

## Held items and revised acting

The laugh now holds both sides with a partial backward lean, a broad smiling mouth
and alert eyes. The [Anton Vierietin laugh photo](https://www.pixtastock.com/photo/101423647)
was visually inspected for the lifted head, sideways tilt and broad smile. No photo
is shipped. Reading eyes track slowly across lines and quickly return to the next.

Activities equip the original `Halloween_Broomstick`, `Tool_Hammer_Iron`,
`Plant_Flower_Bushy_Blue`, `Weapon_Spellbook_Grimoire_Brown`, and `Food_Salad_Caesar`
items, with unchanged models, textures, scales, icons and held-idle grip transforms.
Only the unique wooden spoon needs a custom item; the base assets have no spoon.
Reading opens the grimoire's existing Book-Top and Book-Bot hinges, the same bones
used by vanilla spellbook actions. It does not substitute a reassembled book model.
The original brown grimoire item definition is overridden only to bind its intrinsic
`Animation` to the open hinge pose, as native animated items do. This makes the brown
grimoire open at rest wherever it is displayed, including when a player holds it.
Its native gameplay definition and appearance remain unchanged.

`villager_held_ik.py` solves eight arm/wrist degrees of freedom with elbow limits
and wrist bounds. Sweeping and stirring are continuous closed cycles: the solver
places the hands around the existing item grips, never the reverse. Sweeping
keeps its support palm on the original shaft and brush at floor level. Mixing
keeps the spoon inside the native salad and the bowl nearly level. The numerical
checks sample every engine frame, including between keyframes; offline previews
render original textured meshes. They do not substitute for client validation.

Temporary stacks carry persistent ownership and previous-slot metadata so cleanup
can distinguish an activity item from genuine equipment, even after reloading.
Native salad goes in the main hand (Template_Food already sets Utility.Compatible).
The existing spoon binds to L-Attachment and declares Utility.Usable, so it can
use the normal offhand inventory filter and both items are shown. Existing items
remain in their own slots. Retired cosmetic copies are removed during cleanup.

`ShowItem` selects small native items matching Item_ conversation topics on 40%
of eligible turns after the greeting. The real original item IDs are equipped;
buildings and furniture are excluded. The listener's gesture and thought
bubble are queued 1.2 seconds after the speaker bubble, including romance, and
conversation completion waits for the pending reaction. The action is compiled
with synchronized talking faces for all supported races and Machinaria robots.

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
asset tests check the shared body, expression and mouth poses, cue timing and rig bindings.
All bubble layers use scale 0.28. The bubble artwork is 128 by 128; derived
`Items/Centered` textures fit visible icon bounds inside a 52 by 48 pixel area
on a 64 by 64 canvas. Both layers share the same billboard origin. Runtime uses
`SpawnModelParticles` attached to the entity origin, with `DetachedFromModel=false`
and cleanup on entity removal, so the entire bubble follows walking villagers.
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

Each recording profile supports `Lower` and `Higher` variants.
For example, `WarmMaleLower` uses Warm Male recordings two semitones lower.
Pitch is fixed per character, never jittered. Hytale changes audio playback rate
with pitch, so runtime cue timestamps and speech completion timers scale with it.
Body gestures and eye expressions keep their natural speed. No duplicate OGG or blockyanim files
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
share Talk recordings. Thinking now has two recordings per profile, so it also avoids immediate repeats.

Prowl now uses a player-compatible 20 by 10 mouth attachment with the native
`Mouth1_Textures/Default_Greyscale.png` atlas. Its original mouth placement and
18 by 8 visible footprint are preserved. The embedded old mouth is removed, and
Prowl inherits all Human face/mouth bindings and Action tables without separate
Prowl timelines. `villager_prowl_faces.py` makes this migration repeatable.
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
normal work 60% of the time, sweeping 25%, and reading 15%. Chefs mix salad,
florists tend plants, and merchants read. Innkeepers sweep 60% and read 40%.
Explicit sweep/mix/tend tags remain dedicated activities; retired inspection tags
fall back to reading. Seats and mounted NPCs do not select sweeping, and
meal, sleep, leisure, and mining/forging/chopping activities keep their own behavior.
Garren Vale uses WarmMaleLower.


## Compact bubbles and emotion particles

The old 256px canvas stored its tail anchor in transparent padding. Runtime bubble
icons are now 64px (content up to 52x48), with 128px bubble backgrounds. Both pivot
at the content center; the tail is at (64,123). BillboardY keeps the icon and bubble
in the same upright plane from every horizontal viewing direction. The world spawn
height compensates for the 59px center-to-tail distance at .28 scale. Redundant raw
item-icon copies no longer ship alongside the compact runtime versions.

Original generated emotion art is packed to 64px by
`generate_villager_emotion_particles.py`. Every effect clones the exact native
`Hearts.particlesystem` and `Hearts.particlespawner` used by loved gifts. Only the
spawner ID, texture path and red tint change (white tint preserves the new artwork).
Renderer, opacity, scale curve, spawn rate, motion and three-second system lifetime
are unchanged. Runtime dispatch uses the gift system's eye-height anchor and world
queue. A regression test compares the definitions against the native assets.

Question gestures/voice cues trigger question marks; pondering, disagreement and
35% of voiced reading/thinking trigger confusion; surprise/gasps trigger surprise
or shock; boredom/sighs and rejection trigger gloom. Effects keep a 1.4-second
per-villager cooldown. Ordinary neutral chatter does not produce emotion particles.
The existing loved-gift hearts remain unchanged. Prompts are in
`villager-emotion-art.md`.

## Salad mixing

`villager_life_mixing.py` uses the constrained solver to move the spoon tip around
inside the original salad while preserving both grips. The seven-second loop has
matching endpoints. `verify_villager_mixing.py` checks spoon contact, bowl tilt,
wrist angles, normal elbow flexion, low bowl placement and exposed spoon clearance
above the salad. Mirrored shape dimensions use their absolute extents in the
oriented-box collision tests. The shoulder socket is excluded only against the
torso; the entire upper arm, forearm and hand must clear the head. The stirring
wrist stays under 35 degrees; the underhand support allows up to 42 degrees of
extension to hold the unmodified bowl level near the waist. Bowl yaw is free so
its original grip can turn with the supporting hand. `verify_villager_life_props.py` verifies native grips, supporting-hand
contacts, and sweeping floor clearance. Rock inspection remains unavailable.

The [salad-preparation photograph from the Society of Behavioral Medicine](https://www.sbm.org/healthy-living/how-to-change-your-diet-five-tips-for-healthy-eating)
was visually inspected for bent elbows, downward utensil grip and a small forward
lean. The game pose adapts this to one hand supporting a portable salad bowl.

## Shared animation assets

`villager_animation_pool.py` shares identical complete timelines across voice and
rig exporters. Node names, UVs, keyframes, duration and hold behavior must all
match; numeric formatting and JSON key order do not matter. Stable gesture and profile IDs remain available; recording-specific animation IDs
are replaced by shared speech actions and mouth poses. Consumers must resolve their asset paths through
those bindings instead of deriving filenames from a voice or action ID.

All speech variants reuse the 23 authored gesture files; there are no extended
per-recording body timelines. Outlanders use the player face skeleton and reuse
human facial assets directly. Other races retain their necessary jaw, eye and
mouth-atlas mappings, with identical results shared automatically. Machinaria
uses the same exporter pool within its own optional asset pack.

The compiler prunes obsolete files only inside its generated output folders.
Editable base gesture/face templates remain available as generation inputs.
Run `test_villager_animation_pool.py` and `verify_villager_animation_reuse.py` to
check reuse, reference integrity, durations and rig-specific motion. The latter
also runs in `rebuild_villager_life.py`. Regenerate Machinaria after changing
shared body exports so its action tables pick up the new paths.

After rebuilding a companion mod, update its installed jar in the development
server's `run/mods` too; a rebuilt sibling project does not update that copy.
Check the deployed combination before a play test:
`python tools/verify_installed_villager_animations.py --mods run/mods --mods build/dev-plugin`.
This catches old companion animation tables referencing files removed by reuse.


## Body pose clearance review

`villager_life_sweeping.py` uses geometric two-bone IK with outward elbow poles,
keeps the native broom at 60 degrees above the floor with small circular strokes.
The user approved an activity-only broom grip exception: attachment Z=-4,
sliding the broom four model pixels down from the lower-grip preview while both
hands keep their world height. The original item definition is unchanged;
transparent bristle padding is excluded from the floor-contact audit. `villager_native_tending.py` holds the original blue flowers upright;
`villager_book_support.py` centers the native spine between the palms, preserves
the original item grip, and clears the arm approach/release around the torso.
The offline quad preview maps the top texture row to the top geometric edge,
matching the base-game flower icon instead of displaying its stems upside down.

`villager_pose_clearance.py` supplies the shared oriented-box clearance checks.
`verify_villager_body_poses.py` scans every frame of the 23 shared gestures;
`verify_villager_life_props.py` additionally checks item grip preservation,
book opening/orientation and face clearance, supporting-hand contacts, upright
flowers, broom angle, bristle floor clearance, and wrist limits.
These are offline Player-rig checks, not an in-game verification of every race.
