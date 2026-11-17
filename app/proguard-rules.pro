# Room, Compose и Navigation поставляют собственные consumer-правила,
# поэтому здесь остаются только специфичные для приложения исключения.

# Имена enum-констант хранятся в JSON тренировок, SharedPreferences и extras
# таймера. R8 умеет переименовывать константы, поэтому фиксируем их полностью:
# иначе после обновления приложения старые данные перестанут читаться.
-keep class ru.petproject.giribro.data.SectionType { *; }
-keep class ru.petproject.giribro.data.ThemeMode { *; }
-keep class ru.petproject.giribro.data.StatsPeriod { *; }
-keep class ru.petproject.giribro.timer.TimerPlanSource { *; }
-keep class ru.petproject.giribro.timer.TimerStepKind { *; }
-keep class ru.petproject.giribro.vision.FixationPreset { *; }
-keep class ru.petproject.giribro.vision.RepExercise { *; }
-keep class ru.petproject.giribro.data.SetCaptureMode { *; }

# MediaPipe обращается к Java-классам из нативных библиотек по именам JNI.
# Переименование или удаление этих классов ломает распознавание позы.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
