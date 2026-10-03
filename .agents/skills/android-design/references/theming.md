# Theming

Theme setup for Material 3 Expressive in Compose: versions, color schemes, typography, shapes, motion, and spacing. API names were checked against androidx sources (material3 1.5.0-alpha28) in September 2026.

## Versions

- `androidx.compose.material3:material3`: Expressive requires **1.5.0-alpha** or later. Stable 1.4.0 only has `ShortNavigationBar`, `WideNavigationRail`, and `ModalWideNavigationRail` from this skill.
- Toolchain: 1.5.0-alpha29 (and Wear Compose 1.7) require **compileSdk 37 and AGP 9.1+**; AGP 9.4 in turn needs Gradle 9.6. The `android create` template ships AGP 9.0.1, so raise these first or the build fails on dependency metadata checks.
- Check the newest version in [Maven metadata](https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3/maven-metadata.xml) before adding it; alpha APIs still change (for example `Slider`'s `onValueChange` became required on main after alpha28, so pass it by name).
- Still `@ExperimentalMaterial3ExpressiveApi`: `MaterialShapes`, `RoundedPolygon.toShape()` / `toPath()`, `Morph.toPath()`, `LoadingIndicator`, `ContainedLoadingIndicator`, the pull-to-refresh loading indicator. Still `@ExperimentalMaterial3Api`: `AppBarWithSearch`, `carouselParallaxScrollEffect`, sheets, tooltips.
- Shape morphing uses `androidx.graphics:graphics-shapes`, which material3 already exposes.

## Theme

```kotlin
@Composable fun MaterialExpressiveTheme(
    colorScheme: ColorScheme? = null,
    motionScheme: MotionScheme? = null,
    shapes: Shapes? = null,
    typography: Typography? = null,
    content: @Composable () -> Unit,
)
```

- At the top level, nulls become `expressiveLightColorScheme()`, `MotionScheme.expressive()`, `Shapes()`, and `Typography()`. Nested, nulls inherit from the outer theme.
- `expressiveLightColorScheme()` is `lightColorScheme()` with more colorful `on*Container` roles. There is no dark counterpart; use `darkColorScheme()` or a generated scheme.
- Plain `MaterialTheme(...)` still works but defaults to `MotionScheme.standard()`.

## Color schemes

Pick one source per app, plus at most one content-based scheme per screen.

### Dynamic (wallpaper)

`dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` on API 31+. Google recommends it. Always provide a fallback scheme for older devices.

### Brand (seed color)

Compose has no seed or variant API. Two options:

1. **Static export:** generate light and dark schemes in Material Theme Builder from the brand color (or several: primary, secondary, tertiary, neutral) and paste the `lightColorScheme(...)` / `darkColorScheme(...)` values into the theme. Turn on "match color" (color fidelity) when the brand color must appear as-is.
2. **Runtime generation** with the third-party [MaterialKolor](https://github.com/jordond/MaterialKolor) library (`com.materialkolor:material-kolor`; check its latest version):

```kotlin
val colorScheme = rememberDynamicColorScheme(
    seedColor = BrandColor,
    isDark = dark,
    specVersion = ColorSpec.SpecVersion.SPEC_2025,
    style = PaletteStyle.TonalSpot, // Material's default; Fidelity when the brand color must appear as-is
    contrastLevel = rememberSystemContrast(), // see Contrast levels below
)
```

Keep the generator's default style. `TonalSpot` is what Material and Theme Builder generate; `Fidelity` keeps tones close to the seed, like Theme Builder's "match color." The other styles are scheme variants, not M3 Expressive: `Vibrant` pushes primary chroma to the maximum, and `Expressive` shifts hues away from the seed (in testing, a teal brand gained brown and orange roles) and tints surfaces. Palette variants are color-generation choices, not levels of M3 Expressive. For more color on a screen, use content-based color or give accent roles to more elements that have a job.

A common pattern: brand scheme by default, dynamic color as a user setting.

### Content-based

Color a contained area from an image on screen (a media player from its album art, cards from their photos). Keep the source image visible where its color is used, limit a screen to two scheme sources, and never recolor semantic colors.

With MaterialKolor, extract a seed from the image and theme just that subtree:

```kotlin
val seed = remember(art) { art.themeColor(fallback = BrandColor) } // com.materialkolor.ktx, on an ImageBitmap
val scheme = rememberDynamicColorScheme(seedColor = seed, isDark = isSystemInDarkTheme(), style = PaletteStyle.Content, contrastLevel = rememberSystemContrast())
MaterialExpressiveTheme(colorScheme = scheme) { NowPlaying(...) }
```

A nested theme changes `MaterialTheme.colorScheme` but not `LocalContentColor`, so plain `Icon` and `Text` keep the outer scheme's color (black icons on a dark player). Draw the themed subtree inside a `Surface(color = MaterialTheme.colorScheme.surface)`, which sets the matching content color.

Decode a small thumbnail for extraction, not the full image. MaterialKolor 5.x is built with Kotlin 2.4; if it fails to resolve against your Kotlin or Compose versions, pin the latest version that does (4.x for older Kotlin).

### Roles

| Role | Use |
| --- | --- |
| `primary` / `onPrimary` | The most important action and active states |
| `primaryContainer` / `onPrimaryContainer` | Standout fills (FAB, selected hero element) |
| `secondary`, `secondaryContainer` | Supporting controls, selection, tonal buttons, filter chips |
| `tertiary`, `tertiaryContainer` | Contrasting accents: status, badges, progress, delight; vibrant menus (vibrant toolbars use `primaryContainer`) |
| `error`, `errorContainer` | Errors (static in every scheme) |
| `surface`, `surfaceContainerLowest` to `surfaceContainerHighest` | Body background, then nested containers by emphasis; nav areas use `surfaceContainer` |
| `onSurface`, `onSurfaceVariant` | Default text; lower-emphasis text and icons |
| `outline`, `outlineVariant` | Text field borders; dividers and decorative lines |
| `inverseSurface`, `inverseOnSurface`, `inversePrimary` | Snackbars and reversed elements |
| `primaryFixed`, `primaryFixedDim` (and secondary, tertiary) | Same tone in light and dark themes |

Pairing and no-alpha rules live in [SKILL.md section 4](../SKILL.md#4-color). Material itself uses opacity only for state layers (built into components), disabled content (38%), and the `scrim` behind modals. `outlineVariant` is for dividers and decorative lines only, never text or icons.

### Extra colors (semantic and categories)

For a Success green, or categories beyond the two category-safe accent containers, Material's answer is a **static color**: one seed that produces four roles (color, on-color, container, on-container) following the same pairing rules. Generate them at runtime so they follow light, dark, and contrast:

```kotlin
@Immutable
data class ExtraColor(val color: Color, val onColor: Color, val container: Color, val onContainer: Color)

@Composable
fun rememberExtraColor(seed: Color, harmonize: Boolean = true): ExtraColor {
    val scheme = MaterialTheme.colorScheme
    val source = if (harmonize) scheme.harmonizeWithPrimary(seed) else seed // com.materialkolor.ktx
    val generated = rememberDynamicColorScheme(
        seedColor = source,
        isDark = scheme.surface.luminance() < 0.5f,
        style = PaletteStyle.TonalSpot,
        contrastLevel = rememberSystemContrast(),
    )
    return ExtraColor(generated.primary, generated.onPrimary, generated.primaryContainer, generated.onPrimaryContainer)
}
```

- Harmonizing shifts the hue slightly toward the scheme's primary while keeping its meaning (a red stays red). Skip it when the color is literal (a brand color, transit line colors) or must stay distinguishable: pass `harmonize = false` for categories, and pick seeds far apart in hue.
- Contrast for colors outside the scheme roles (category seeds, chart series, widget art): HCT tone difference is the contrast rule. "Smaller elements (less than ¼" or 40 dp) require a tone difference of 50 with their background, larger elements require a tone difference of 40." (Google)
- Use one role per category across a screen: `color` with an `onColor` glyph when the category also appears in a chart, or `container` with `onContainer` when it only marks badges. Category rules: SKILL.md section 4.

### Contrast levels

Users pick standard, medium, or high contrast in system settings (Android 14+). Dynamic schemes follow it automatically; hand-made and generated schemes don't. For generated schemes, pass the system value to MaterialKolor:

```kotlin
@Composable
fun rememberSystemContrast(): Double {
    val context = LocalContext.current
    return remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(UiModeManager::class.java).contrast.toDouble() // 0 standard, 0.5 medium, 1 high
        } else 0.0
    }
}
```

The brand and content-based snippets above already pass it. For a static Theme Builder export, also export the medium and high contrast schemes and pick one from this value.

## Typography

- 15 baseline styles plus 15 emphasized ones (`displayLargeEmphasized` ... `labelSmallEmphasized`, 1.5.0 only). Emphasized raises weight: Display, Headline, Body, and Title Large go Regular to Medium; Title Medium/Small and Labels go Medium to Bold.
- `Typography(fontFamily = ...)` sets one family everywhere. To put a brand face only on large styles, copy the defaults:

```kotlin
private val Brand = FontFamily(Font(R.font.brand_display_medium, FontWeight.Medium), Font(R.font.brand_display_bold, FontWeight.Bold))
private val base = Typography()

val AppTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontFamily = Brand),
    displayMedium = base.displayMedium.copy(fontFamily = Brand),
    displaySmall = base.displaySmall.copy(fontFamily = Brand),
    headlineLarge = base.headlineLarge.copy(fontFamily = Brand),
    headlineMedium = base.headlineMedium.copy(fontFamily = Brand),
    headlineSmall = base.headlineSmall.copy(fontFamily = Brand),
    displayLargeEmphasized = base.displayLargeEmphasized.copy(fontFamily = Brand),
    headlineLargeEmphasized = base.headlineLargeEmphasized.copy(fontFamily = Brand),
    // repeat for the other Display and Headline emphasized styles you use
)
```

- Change letter spacing and line height to fit a new face, not the scale's sizes: "Avoid changing the type size; this can affect how components render and reflow." (Google) For editorial sizes (a total that fills the top of the screen), define a separate style outside the scale, like `numeralStyle` below.

### Google Sans Flex

Open source on [Google Fonts](https://fonts.google.com/specimen/Google+Sans+Flex) (since November 2025) and the required family for every style in this skill. Axes: `wght` 1 to 1000, `wdth` 25 to 151, `opsz` 6 to 144, `slnt` -10 to 0, `GRAD` 0 to 100, `ROND` 0 to 100.

Bundle the variable TTF in `res/font/` (downloadable Google Fonts don't carry variation settings). Variation settings apply on API 26+. Because optical size should match each style's size, build the font per text style.

Voice starting points (tune them to the screen's direction; they are not presets to copy):

| Voice | Axes (approx.) | Use for |
| --- | --- | --- |
| Friendly hero | weight 800 to 900, roundness 100 | The one hero title or number in a warm, playful product |
| Tall numerals | width 25 to 50; weight 300 off, 800 on | Numbers that fill a card (times, totals, calculators), state through weight |
| Premium display | width 75 to 90, weight 400 to 600 | Elegant headers and editorial titles |
| Airy display | weight 100 to 300, large sizes only | Calm headers, inactive states |
| Wide header | width 125 to 151, weight 500 to 700, roundness 0 | Confident section or dialog titles; never in app bars |
| Loud expressive | width 151, weight 900, slant -10 | Celebrations and the user's own voice; rare |
| Text | default axes, weight 400 to 500 | Body and labels; don't vary it |

Code:

```kotlin
/** A Google Sans Flex voice: fixed axes, optical size matched to this style's font size. */
fun TextStyle.flex(weight: Int = 400, width: Float = 100f, round: Float = 0f, slant: Float = 0f) = copy(
    fontFamily = FontFamily(
        Font(
            resId = R.font.google_sans_flex,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.width(width),
                FontVariation.slant(slant),
                FontVariation.opticalSizing(fontSize),
                FontVariation.Setting("ROND", round),
            ),
        ),
    ),
    fontWeight = FontWeight(weight),
)

private val base = Typography()

val AppTypography = base.copy(
    displayLarge = base.displayLarge.flex(weight = 850, round = 100f),   // friendly hero
    displayMedium = base.displayMedium.flex(weight = 850, round = 100f),
    headlineLarge = base.headlineLarge.flex(weight = 500, width = 85f),  // premium display
    headlineMedium = base.headlineMedium.flex(weight = 500, width = 85f),
    titleLarge = base.titleLarge.flex(weight = 500),
    bodyLarge = base.bodyLarge.flex(),
    bodyMedium = base.bodyMedium.flex(),
    labelLarge = base.labelLarge.flex(weight = 500),
    // ...every style you use, including the *Emphasized ones with a heavier weight
)

// A voice used outside the type scale, such as tall numerals with state:
@Composable
fun numeralStyle(selected: Boolean) =
    MaterialTheme.typography.displayLarge.flex(weight = if (selected) 800 else 300, width = 35f)
        .copy(fontFeatureSettings = "tnum")
```

- Define two or three voices once per app and apply them consistently; don't invent new axis values per screen.
- State through axes: selected items heavy (700 to 900), unselected light (200 to 300); keep sizes equal so nothing reflows. `FontVariation.grade(...)` changes emphasis without changing width.
- Each distinct axis combination is a separate font instance; for animated axes (text that widens as a slider moves), step through a small set of values and keep it to a hero moment.
- Other voices that pair well: Google Sans Code or Roboto Mono for metadata and timecodes, Roboto Serif for long reading.
- Tabular numbers: `style.copy(fontFeatureSettings = "tnum")`.

## Shapes

`Shapes(extraSmall, small, medium, large, extraLarge, largeIncreased, extraLargeIncreased, extraExtraLarge)`, read as `MaterialTheme.shapes.*`:

| Token | Default | Typical use |
| --- | --- | --- |
| extraSmall | 4dp | Chips, snackbars |
| small | 8dp | Text fields, menus |
| medium | 12dp | Cards |
| large | 16dp | FABs, sheets on large screens |
| largeIncreased | 20dp | Expressive containers |
| extraLarge | 28dp | Dialogs, bottom sheets |
| extraLargeIncreased | 32dp | Expressive hero containers |
| extraExtraLarge | 48dp | Large hero surfaces |

Change a token to change every component mapped to it; pass `shape` to change one component.

### Shape library

`MaterialShapes` (experimental) has 35 `RoundedPolygon`s: Circle, Square, Slanted, Arch, Fan, Arrow, SemiCircle, Oval, Pill, Triangle, Diamond, ClamShell, Pentagon, Gem, Sunny, VerySunny, Cookie4Sided, Cookie6Sided, Cookie7Sided, Cookie9Sided, Cookie12Sided, Ghostish, Clover4Leaf, Clover8Leaf, Burst, SoftBurst, Boom, SoftBoom, Flower, Puffy, PuffyDiamond, PixelCircle, PixelTriangle, Bun, Heart.

Clip with `Modifier.clip(MaterialShapes.Sunny.toShape())` or use as a `Surface` shape.

### Shape morphing

Prefer components that morph on their own (buttons, icon buttons, toggles, button groups, list items, loading indicators). For a custom morph between two library shapes:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress = progress) // unit-square coordinates
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingBadge(done: Boolean, modifier: Modifier = Modifier) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(if (done) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec())
    Box(modifier.size(64.dp).clip(MorphShape(morph, progress)).background(MaterialTheme.colorScheme.tertiaryContainer))
}
```

## Motion

`MotionScheme` has six specs: `defaultSpatialSpec`, `fastSpatialSpec`, `slowSpatialSpec`, `defaultEffectsSpec`, `fastEffectsSpec`, `slowEffectsSpec`. `MotionScheme.expressive()` and `MotionScheme.standard()` are the presets:

| Spec | Expressive (damping / stiffness) | Standard |
| --- | --- | --- |
| Fast spatial | 0.6 / 800 | 0.9 / 1400 |
| Default spatial | 0.8 / 380 | 0.9 / 700 |
| Slow spatial | 0.8 / 200 | 0.9 / 300 |
| Effects fast / default / slow | 1.0 / 3800, 1600, 800 | same |

- Override one subtree by nesting `MaterialTheme(motionScheme = MotionScheme.standard()) { ... }` (`LocalMotionScheme` was removed).
- Reduced motion, Google's pattern:

```kotlin
object ReducedMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}
```

- A custom brand scheme overrides the same six functions with `spring(dampingRatio, stiffness)`; keep effects specs at `Spring.DampingRatioNoBouncy`.

## Spacing

Compose has no spacing tokens. Define the 8dp scale once in the theme package and use it everywhere:

```kotlin
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}
```

Screen margins: 16dp on compact, 24dp on medium and larger. Put padding and gaps on parent layouts (`Arrangement.spacedBy`) instead of margins on children.
