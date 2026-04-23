Place the NSFW model file here before running real detection.

Expected asset path:

`app/src/main/assets/models/nudenet_int8.tflite`

The current implementation will keep frame capture running even if the model is absent, but inference will be skipped and the UI will show that the model is not loaded yet.
