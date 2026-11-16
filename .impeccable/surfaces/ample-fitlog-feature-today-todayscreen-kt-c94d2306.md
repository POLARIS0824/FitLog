---
version: 1
slug: "ample-fitlog-feature-today-todayscreen-kt-c94d2306"
primary_target: "app/src/main/java/com/example/fitlog/feature/today/TodayScreen.kt"
related_targets: ["app/src/main/java/com/example/fitlog/feature/log/LogScreen.kt","app/src/main/java/com/example/fitlog/feature/log/MarkdownLogContent.kt","app/src/main/java/com/example/fitlog/feature/insight/InsightScreen.kt","app/src/main/java/com/example/fitlog/feature/settings/SettingsScreen.kt"]
---

# Main screens refinement

Mode: Operate. Scope: Today, Log (local and Markdown), Insight overview, Settings overview. Audience: the author recording and reviewing training. Build directly in Compose using the incumbent design system.

## Direction contract

THESIS: Give each page one readable task hierarchy: record training, browse a source, understand training, or configure the app. Preserve existing content and navigation.

OWN-WORLD: Retain Google Sans Flex, dynamic and fallback light/dark Material color roles, flexible app bars, connected controls, tonal grouped lists, expressive button shapes, and existing theme motion.

STORY: Today leads to a local record. Log identifies the source before its records and tools. Insight puts the chosen range beside meaningful results and a useful empty state. Settings keeps destination and current state together.

FIRST VIEWPORT: Today pairs the training message and primary action inside one container above quieter library rows. Log keeps its app bar first, then the source selector and a compact list header. Insight shows range, summary or onboarding, and the first useful section. Settings has grouped, readable destinations with contextual subtitles.

FORM: Refinement of existing native task screens; no replacement visual world or concept tournament. Signature interaction is the incumbent native expressive press/selection feedback, carried consistently through primary and supporting actions. seed: none (unavailable)

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

Constraints: bottom toolbar and FAB menu are protected, including semantics and motion. No business/data ownership changes. Both locales remain supported. Existing system files are preserved by the documenter; this refinement does not authorize a new visual system. No shipping raster assets are required. Validate phone, expanded content width, dark theme, and enlarged text where the device allows it.
