# FitLog R8 规则
#
# 原则：依赖库自带 consumer rules 的（Room / Compose / kotlinx-serialization）
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

# OkHttp（无 consumer rules 的部分场景）
-dontwarn okhttp3.**
