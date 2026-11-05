# Model assets

| File | Size | Used? | Notes |
| --- | --- | --- | --- |
| `320n_float32.tflite` | ~12 MB | **Yes** | Loaded by `NsfwClassifier` (`MODEL_PATH = "models/320n_float32.tflite"`). NudeNet 320n detector, float32. |
| `nudenet_int8.tflite` | ~6 MB | No | Quantized variant, kept for experiments. It isn't loaded and still adds to the APK size. |

## Model contract

The code expects:

- **Input:** `float32[1, 320, 320, 3]`, RGB, values in `[0, 1]`.
- **Output:** `float32[1, 22, 2100]`: rows 0–3 are box centre x, centre y, width and height in input pixels; rows 4–21 are scores for the 18 labels, in the order of the `DetectorLabel` enum.

See [docs/detection.md](../../../../../docs/detection.md) for how the output is decoded and thresholded.

## Replacing the model

1. Put the new `.tflite` file in this folder.
2. Update `MODEL_PATH`, and if they differ, `INPUT_SIZE`, `OUTPUT_CHANNELS`, `OUTPUT_CANDIDATES` and the `DetectorLabel` order in `NsfwClassifier.kt`.
3. Keep `noCompress += "tflite"` in `app/build.gradle.kts`, because the model is memory-mapped.
4. Add a row to the table above with the model's source and license.

If the model file is missing or fails to load, frame capture keeps running but inference is skipped, and the setup screen shows *Model file not loaded yet*.
