# ISLRTC test clips

`student.mp4`, `today.mp4` and `husband.mp4` are from the ISLRTC Indian Sign Language Dictionary (Indian Sign Language Research and Training Centre, Government of India), under the Government Open Data License - India. They are used unchanged, only for device tests, and are not part of the app.

| Test | What it checks |
| --- | --- |
| `SignPipelineDeviceTest` | Each clip through MediaPipe Holistic on the device, sign segmentation and the bundled ISL reader. Pose must be found in at least 90% of frames, hands in at least 80% of the frames `tools/isl/retarget_islrtc.py` found on a PC, and every clip must give a segment with guesses. The reader's top three are only logged. |
| `SkeletonOverlayDeviceTest` | A frame of `husband.mp4` through MediaPipe and the camera overlay's drawing: both hands and the arms must be traced. |

`SignPipelineDeviceTest` is the check that catches a broken MediaPipe or protobuf runtime; run it after any update to either. Both run with `./gradlew connectedStandardDebugAndroidTest` on an emulator or phone.
