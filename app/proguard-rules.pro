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

# Flogger находит вызвавший класс по стеку (CallerFinder.findCallerOf).
# Если R8 переименует его классы или встроит findLoggingClass в forEnclosingClass,
# поиск кадра возвращает null и падает static-инициализатор
# com.google.mediapipe.framework.Graph:
#   IllegalStateException: no caller found on the stack for: ...
#   ExceptionInInitializerError -> NoClassDefFoundError: .../framework.Graph
# Полный -keep запрещает и обфускацию, и inlining этих классов.
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**

-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
