# FitLog R8 规则
#
# 原则：依赖库自带 consumer rules 的（Room / Hilt / Compose / Retrofit converter）
# 不重复配置；只补真正需要的 keep。

# kotlinx-serialization：@Serializable 类的序列化器经反射按伴生对象查找
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
}

# ADK：Event/Content 等经 kotlinx-serialization 持久化到 RoomSessionService，
# KSP 生成的工具类以类名注册，保留注解与生成物
-keep class com.google.adk.kt.** { *; }
-keep class com.example.fitlog.feature.agent.tools.** { *; }

# OkHttp / Retrofit（无 consumer rules 的部分场景）
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# kxml2（ADK 传递依赖）与 Android 平台内置 XmlPullParser 重复：
# 保留平台版本，忽略库版本的平台类实现警告
-dontwarn org.kxml2.**
-dontwarn org.xmlpull.v1.**
-keep class org.xmlpull.v1.XmlPullParser { *; }
-keep class org.xmlpull.v1.XmlPullParserFactory { *; }

# snakeyaml / slf4j（ADK 传递依赖）引用了仅桌面 JVM 存在的类（java.beans 等），
# Android 运行时不会走到这些路径
-dontwarn java.beans.**
-dontwarn org.slf4j.impl.**
-dontwarn org.slf4j.spi.LoggingEventBuilder
