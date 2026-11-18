# R8 rules applied to apps that use :speech.

# G-12: JNI symbol names are derived from this class and its native method names.
-keep class tr.yasliasistan.speech.whisper.WhisperNative {
    native <methods>;
}
