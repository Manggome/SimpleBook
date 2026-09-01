# 현재 release 빌드는 난독화를 끄고 나간다. 나중에 켤 때를 위해 최소 규칙만 남겨둔다.
-dontwarn org.slf4j.**
-keep class com.github.junrar.** { *; }
