---
name: android-design
description: >
  Material 3 Expressive design judgment for Android apps in Jetpack Compose, distilled from Google's
  guidelines, UX research, and I/O talks. Use when designing, building, or reviewing Android phone, tablet,
  or desktop screens; choosing components; setting up a theme (color, type, shape, motion); or when a Compose UI
  looks generic and needs hierarchy, emphasis, or polish.
---

# Android Design

How Google designs Android apps with Material 3 Expressive, translated into decisions and Compose code. Expressive is not a separate design language: it is Material 3 plus opt-in APIs, components, and design tactics that Google calls an "expansion pack." The guidelines, research, and API names here come from m3.material.io, Android's design guides (developer.android.com/design/ui), the Wear OS design guide, six Google I/O and Android Developers talks, and androidx sources checked in September 2026.

The through-line: **a screen feels designed when emphasis is spent deliberately.** Rank what the screen is for, then spend size, color contrast, shape, type, containment, and motion on the top goal, less on the next, and almost none on the rest. Stock Material code gives every element equal weight, which is why it looks like a template.

> "Why did all these apps look so similar? So boring? Wasn't there room to dial up the feeling?" (The question from Google's research team that started M3 Expressive.)

Quotes and rules marked **(Google)** come from Google's guidance. Rules marked **(default)** are this skill's starting points: follow them unless the screen's direction gives a reason not to.

**Surfaces.** This skill targets phone, tablet, and desktop apps. Wear OS and home screen widgets are not yet in scope: their references hold distilled Google guidance but have not reached this skill's quality bar. When building one anyway, start from that reference and check the result yourself. Wear OS uses its own theme, Roboto Flex, and Wear components ([references/wear-os.md](references/wear-os.md)); widgets use `GlanceTheme` and Glance components ([references/widgets.md](references/widgets.md)). Where those references differ from this file, they win for their surface.

## Why stock Compose code looks generic

Three causes, each fixable:

1. **Plain `MaterialTheme` is pre-Expressive.** It defaults to `MotionScheme.standard()` (minimal bounce), baseline shapes, and baseline type. Only `MaterialExpressiveTheme` defaults to expressive motion.
2. **Many default components were replaced but not deprecated.** Medium/large top app bars, the bottom app bar, segmented buttons, the navigation drawer, and the small FAB still compile without warnings. See section 9.
3. **Nobody decided what matters most.** Tokens can't make that decision. Section 0 does.

## 0. Before writing UI

Answer these in your reply before any code, a sentence or two each. Skipping this step is what produces generic screens.

1. **Rank the goals.** What is the one primary task on this screen? What is secondary and tertiary? (Google: "Simplifying to one primary task on each page.")
2. **Choose a direction.** Name the screen's emotional character, the content or interaction that defines it, and one visual idea that ties type, imagery, color, and shape together. Each answer must name a decision ("the total set in tall numerals fills the top third"), not an adjective ("modern, clean"). If the idea describes how most apps of this type already look, it isn't an idea yet.
3. **Decide the hero moment.** Is this screen one of the product's one or two hero moments (the most emotional or most central interaction)? If yes, plan how several tactics combine there. If no, keep it calm (Google).
4. **Choose the color source.** Dynamic (wallpaper), brand (a generated scheme), or content-based (from an image on screen). See section 4.
5. **Spend emphasis by rank.** The primary goal gets the most, usually size first. Secondary goals get fewer tactics; tertiary ones get containment and type (Google). Content can dominate the screen when it is the product, as long as the primary action stays unmistakable.
6. **Pick components from section 9's table**, not from memory of older Material.

Then compose the screen (section 8), build it, and run the [screen check](#screen-check) on the rendered result.

## 1. Theme setup

Expressive needs `androidx.compose.material3:material3` **1.5.0-alpha** or later. Stable 1.4.0 has almost none of these APIs. Look up the newest 1.5.x version in [Maven metadata](https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3/maven-metadata.xml) instead of trusting a remembered one; alpha APIs still shift between releases. Current alphas also require compileSdk 37 and Android Gradle Plugin 9.1 or later.

```kotlin
@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> expressiveLightColorScheme()
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography, // Google Sans Flex voices, see section 5
        content = content,
    )
}
```

- There is no `expressiveDarkColorScheme`; use `darkColorScheme()` or a generated dark scheme.
- `MaterialShapes`, `toShape()`, `LoadingIndicator`, and `ContainedLoadingIndicator` still need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`. Everything else here is non-experimental in 1.5.0 alphas.
- Full setup, seed schemes, a spacing object, and version notes: [references/theming.md](references/theming.md).

## 2. Emphasis and hierarchy

Google's research justifies Expressive through usability, not decoration: participants found key UI elements **up to 4x faster** on expressive screens, and the age gap in finding them nearly disappeared. Emphasis is how people find the primary action.

- **Size is the loudest lever.** "The most important action or the main call to action should be the largest element" (the largest interactive element; a summary card can be physically bigger). Larger key actions measurably reduce errors and improve satisfaction. One focal point per screen: when everything is large, nothing is.
- **Placement:** put the key action where the hands and eyes already are. Google's email study: moving Send from a small top-bar icon to a larger button just above the keyboard, in `secondary`, made people find it 4x faster; Aura's home screen puts its extra-large start button low, last in the vertical flow. Choose the size and position from the interaction and the space the content needs (default): an XL button suits a control-led hero, not every important action.
- **The primary action gets the strongest fill** (Google: primary roles go to crucial calls to action; filled buttons sparingly). Secondary actions are tonal or standard; toggles like favorite show state with a filled icon or a tonal container, not a second loud fill competing with the main control.
- **Contrast between roles, not one role everywhere:** `primary` for the main action, `secondary` or `secondaryContainer` for supporting controls, `tertiary` for status or accents. Using `primary` and `primaryContainer` for everything flattens the screen.
- **Containment:** group related items in containers; give the most important content "ample space and the brightest surface."
- **Summaries support the task, they don't outrank it.** When the primary goal is acting on a list (checking off habits, replying to messages), the next actionable item must stay the most inviting thing on screen. Stat cards can be bold, but give actionable rows clear containers and let completed items recede (quieter color, no strong fill), grouped by state rather than interleaved (section 8). Selection highlighting is for what the user chose, not for what is finished.
- **Order of work:** build the hierarchy first (size, color, spacing, placement, containment), *then* add unique emphasis (shape, scale, morph) to celebrate success or progress.

### Google's worked example: the Aura breathing app

Each screen spends emphasis by goal rank. Use this as the reasoning template.

| Screen | Rank | Emphasis |
| --- | --- | --- |
| Home | Primary: start a session | Extra-large "Start breathing" button in dark `primary` on a soft surface, low on screen. Settings grouped as list items in `secondary`. Daily message in a soft container, least emphasis. |
| Session | Hero moment | A screen-filling flower shape (`MaterialShapes.Flower` and `Sunny`) grows and shrinks with each breath on spring motion; vibrant yellow on inhale; huge countdown numerals against small labels; pause/stop small at the bottom; navigation bar hidden. |
| Report | Secondary | Fewer tactics "to reduce cognitive load": metrics in uniform flower shapes, emphasized numbers, a medium (not XL) Finish button. |
| Progress | Tertiary | Subtle: key data in `primary`, completed days as small secondary shapes, numbers "large enough to scan, but they don't dominate." |

Their v1-to-v2 lesson: ungrouped settings of similar size and inconsistent color competed for attention; grouping them above one extra-large button went "from cluttered to calm."

## 3. Hero moments and restraint

> "Hero moments use multiple expressive tactics to break from predictable or uniformly applied design ideas."

- A hero moment layers type, shape, color, motion, and flexible components on the product's key interaction: the Phone dialer, a media player, a completed goal, a breathing session. Ask: *is it emotionally impactful?* and *is it a key interaction?*
- **One or two per product.** They're "brief, delightful, surprising, and unexpected"; more than that overwhelms.
- Secondary screens use fewer tactics. Don't combine every tactic everywhere.
- In a hero moment, **one custom thing moves**: keep text stable while the shape animates (Google). Built-in component morphs (a button changing shape on press) don't count.
- **When content is the product, the content is the hero.** In players, galleries, and trips, give the album art or photo scale and let its color set the screen. A library-shape mask, a full bleed, or a title layered over the image are options, not requirements: an ordinary rounded crop is just as strong when the composition around it carries the idea.
- **Keep familiar patterns.** Expressive styles the patterns; it doesn't replace them. In Google's tests, a playlist rebuilt as scattered album art "looked modern and exciting" but people didn't recognize it as a playlist, and removing text labels from email actions hurt usability. Context matters too: what suits a media player may not suit a banking app.
- A "strong minority" of users prefer calmer designs, and "no amount of emotion can compensate for a lack of clarity." Expressive sharpens a screen that already works; it doesn't rescue a confused one.

## 4. Color

- **The generator makes the palette; you assign the roles.** Material's color utilities turn one source color into the accent, surface, light, and dark roles (Google). Take the generated palette as it comes and spend the design effort on which element gets which role.
  - *Dynamic* (wallpaper) is Google's recommendation and gives personalization. The M3 Expressive update made dynamic color richer at the source ("higher chroma across all hues"); apps get it automatically. The app shares hues with the rest of the phone, so its identity comes from hierarchy, shape, and type.
  - *Brand* gives the app its own identity: a scheme generated from the brand color with Material Theme Builder, or with MaterialKolor at runtime at the default palette style (theming reference). Don't raise a palette style to look "more expressive": MaterialKolor's `PaletteStyle.Expressive` is a hue-shifting scheme variant unrelated to M3 Expressive, and it pulls the palette away from the brand's hue.
  - *Content-based* colors a contained area from an image on screen (album art, a photo). Limit a screen to two color sources and keep the source image visible (Google).
  - `expressiveLightColorScheme()` is only the light fallback when dynamic color is unavailable, as in Google's `MaterialExpressiveTheme` sample.
- **Accents mark meaning.** "Accent colors usually exhibit the most expressiveness within a UI, whether it's for branding, highlighting actions, personal expression, or user expression." Apply them by importance: primary to crucial actions (the FAB), secondary and tertiary "down the hierarchy." "Use all accent colors mindfully, taking into account that the human eye is particularly drawn to vibrant colors." (Google)
- **Different jobs get different roles.** Use "a variety of primary, secondary, and tertiary accent colors for hierarchy and distinction": action (`primary`), selection (`secondary`), status or delight (`tertiary`). Google's example: data in `primary`, progress accents in `secondary` yellow.
- **Surfaces are most of the screen.** Surface colors "represent the majority of your app's colors. Don't be shy to use lots of surface space; the human eye needs space to relax." (Google) Build hierarchy with surface steps and put accent containers only on elements with a job (the hero, a selection, a key status), not on every card.
- **Apply roles only in their intended pairs or layering orders** (Google's rule): `onX` on `X`; `onSurface`, `onSurfaceVariant`, and `primary` on surfaces; `inverseOnSurface` and `inversePrimary` on `inverseSurface`. Containers are fills, never text colors. Default text is `onSurface`; `onSurfaceVariant` for lower emphasis.
- **Never fade text or icons with alpha.** `copy(alpha = ...)` breaks the contrast the pairs guarantee. Lower emphasis is a different role, not a transparent one.
- **Surfaces:** `surface` for the body, `surfaceContainer` for navigation regions, and the five container levels for nesting. The most important container gets the brightest surface.
- Dividers use `outlineVariant`; text field borders use `outline`.
- **Surface steps:** use each component's prescribed surface mapping (search, for example, specifies containers "more than one step apart"; see the components reference). For custom groups, use a surface difference when the boundary needs emphasis; proximity and alignment can group without another fill.
- **Vibrant** component styles are high emphasis; use them sparingly ("Vibrant menus are more prominent, and should be used sparingly"). Vibrant menus are tertiary-based; vibrant toolbars use `primaryContainer` ([references/components.md](references/components.md#toolbars)).
- Semantic colors (error red, a success green defined as a static color) never come from content color, and keep one meaning everywhere: "if you establish a pattern, repeat it throughout the app." (Google)
- **Inverse containers** (`inverseSurface` / `inverseOnSurface`) reverse one element out of the screen, like a snackbar or a total next to lighter metric cards.
- **Color can follow state:** a scheme derived from the app's content or context (the sky in a weather app, album art in a player) is content-based color at screen scale. Offering the seed and palette style (and a pure black dark mode) as user settings is common in shipped Expressive apps.
- **Strong fills mark the hero or one coherent group** (default). A set of related filled controls can read as one unit; unrelated saturated cards side by side read as noise, not hierarchy.
- **Category colors** need clearly different hues so each is recognizable at a glance. Two can use `primaryContainer` and `tertiaryContainer` (tertiary sits at a different hue; secondary shares primary's hue at lower chroma, so it reads as the same color); more get a static color each (four roles from one seed, not harmonized, since harmonizing pulls hues together; recipe in [references/theming.md](references/theming.md#extra-colors-semantic-and-categories)), never an improvised hex. Color the badge or icon, not a whole card, and give each category one visible color across the screen: every mark of a category (bar segment, legend, badge) uses the same role, not a solid color in one place and its pale container in another.
- **Charts and data graphics** use the solid roles (`primary`, `secondary`, `tertiary`, a static color's main role), not containers: graphics need 3:1 against their background (Google), and pale containers disappear.

## 5. Typography

- Use the role scale: **Display** for short important text and numerals, **Headline** for short high-emphasis text on phones, **Title** for secondary regions, **Body** for reading, **Label** inside components. Most screens need about five styles; pick sizes with clear contrast between them, not near-duplicates.
- **Emphasized styles** (`MaterialTheme.typography.displayLargeEmphasized` through `labelSmallEmphasized`) are heavier variants. Components don't use them by default; apply them to the primary button label, selected items, unread items, key numbers, and headlines. "Give extra impact to a headline, or subtly strengthen text of the same size."
- **Use Google Sans Flex for every style** in phone, tablet, and desktop apps (this skill's standard, not a Google requirement; Wear uses Roboto Flex). It is open source on Google Fonts since November 2025, most of the shipped Expressive apps sampled for this skill use it, and its shapes echo the Material shape library. Six variable axes: weight (1 to 1000), width (25 to 151), optical size (6 to 144), slant (0 to -10), grade (0 to 100), and roundness (`ROND`, 0 to 100). Bundle the variable font and set axes per style ([references/theming.md](references/theming.md#google-sans-flex)).
- **Expressive voices on large styles, the text voice on small:** give Display and Headline the voice that serves the direction; keep Body and Label on default axes so they stay highly readable. Be careful at Title.
- **The hero title or number must read as Google Sans Flex** (default): its character comes through strong axis settings (heavy weight, very narrow or very wide width, or high roundness). A medium weight at a middle width looks like any sans; keep the calm voices for secondary text.
- **Axes carry feeling and meaning.** Google describes weight as ranging from "calm as a whisper" to "loud and rugged" and roundness as "personal, playful." In their research with 3,000+ readers, taller, more elegant (narrower) styles read as more premium and engaging. Axes also carry state: heavy in a filled container for the selected item, light for the rest, at the same size so nothing reflows. Grade adds emphasis without changing width.
- **Always match optical size to the text size.** It reshapes letters to stay legible at every size; one setting across Display and Label hurts both. Set `opsz` per style (see the theming reference).
- **Pick voices, not random axis values.** Define each voice once as a style and reuse it; two or three per app, each with one job. Starting points for Google Sans Flex voices are in [references/theming.md](references/theming.md#google-sans-flex); tune them to the direction rather than copying the nearest preset.

- **Other voices:** Google Sans Code (or Roboto Mono) for code, metadata, and timecodes; Google Sans Mono only at medium and large editorial sizes, never for code. Roboto Serif for long reading.
- **Editorial treatments** let type dominate a hero moment: a huge number, an album title over a photo, text that widens as a slider rises. Keep them consistent, match the emotion (narrow and light for calm, bold and wide for energy), and never use them for labels.
- **Contrast inside one line:** two voices in the same phrase can carry meaning, such as a flight's origin light and condensed and its destination heavy, wide, and slanted ("PDX › SFO"). The line reads as past and future without extra UI.
- **Type as graphic:** oversized words or numbers cropped by the screen edge, or tinted close to their background ("68" behind a weather summary, a trip name behind the traveler). When text becomes art, repeat the information readably elsewhere and hide the decorative copy from accessibility services (`Modifier.clearAndSetSemantics {}`).
- **Number plus unit:** a huge numeral with its unit in a small label style on the same baseline ("15 min", "0%", "68°"): put both in a `Row` with `Modifier.alignByBaseline()`.
- Line height about 1.2x on large styles, 1.5x on body. Tabular numbers (`fontFeatureSettings = "tnum"`) for timers and changing values. Sentence case everywhere.

## 6. Shape

- **Corner scale:** 4, 8, 12, 16, **20**, 28, **32**, **48**dp and full (`MaterialTheme.shapes.extraSmall` to `extraExtraLarge`; the bold ones are Expressive additions). Buttons default to full; cards to medium.
- **Tension creates focus.** Mix round and square; "break from the surrounding shape style to draw attention to a particular element." Material "historically focused on rounded shapes"; sharp contrast is more memorable.
- **Shape signals state.** Expressive buttons, icon buttons, toggles, and list items morph on press and selection (round to square or back). Use the components' `shapes` parameters to get this for free.
- **The shape library** (`MaterialShapes`: Cookie9Sided, Sunny, Flower, Clover4Leaf, Burst, Pill, Heart, and 28 more) is for avatars, image crops, decorative graphics, achievements, and a bespoke hero button. Not for text-heavy containers. "Shapes without clear meaning... add more visual clutter than delight."
- **A decorative shape needs a reason from the subject.** Google: "Ensure that shapes resonate with the product's narrative." A sun and cloud on a weather screen, a setting sun for a meditation app, and album art cut into a scalloped record label all depict something; a blob or ripple that depicts nothing is clutter. When a shape depicts an object, keep it recognizable (default): give it the object's color as a static color ([references/theming.md](references/theming.md#extra-colors-semantic-and-categories)) instead of a pale scheme role chosen to quiet it, and make it smaller or crop it if it competes with the hero.
- **Uniform among peers:** equally important data meant for comparison (metrics, calendar days) uses the same shape and size, evenly spaced, without overlap (Google: "Use uniform shapes and sizes"). Vary scale or form when the data has a deliberate hierarchy, keeping items aligned and comparable. Decorative layering, such as type over an image, is fine when controls and text stay clear.
- **Optical roundness:** nested radius = outer radius minus padding (28dp card with 12dp padding holds 16dp corners).
- Grouped items use small inner corners and large outer ones (segmented lists: 4dp inner, 16dp outer; selected morphs to 16dp all around).

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
Image(painter = avatar, contentDescription = null, contentScale = ContentScale.Crop,
    modifier = Modifier.size(96.dp).clip(MaterialShapes.Cookie9Sided.toShape()))
```

Morphing between two library shapes: see [references/theming.md](references/theming.md#shape-morphing).

## 7. Motion

Material motion is spring physics, themed through `MotionScheme`:

- **Spatial** specs (position, size, rotation, corner radius) overshoot and settle. **Effects** specs (color, opacity) never overshoot.
- Speeds: **fast** for small components (buttons, switches), **default** for partial-screen motion (sheets, rails), **slow** for full-screen changes.
- Custom components use the theme instead of `tween()`, so they match Material components and follow scheme changes:

```kotlin
val scale by animateFloatAsState(if (pressed) 0.94f else 1f, MaterialTheme.motionScheme.fastSpatialSpec())
val tint by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHigh,
    MaterialTheme.motionScheme.defaultEffectsSpec())
```

- Spring values for both schemes: [references/theming.md](references/theming.md#motion).
- **Expressive** is the default choice and belongs on hero moments. **Standard** suits utilitarian products. Override a subtree by nesting `MaterialTheme(motionScheme = ...)`.
- Springs are interruptible and carry velocity when retargeted; never lock input during an animation.
- **Screen transitions stay simple.** Bounce belongs to components and hero moments. Use container transform for card or list item to detail only in hero moments and shallow hierarchies ("Don't use container transform in apps with deep hierarchies"); platform-default forward/backward for everyday hierarchy; a quick fade between top-level destinations, never a lateral slide (it conflicts with carousel and list gestures). (Google) Fade content out fully before fading new content in; don't fade bottom sheets.
- **Reduced motion:** swap to a scheme whose specs `snap()` (Google's own pattern) and drop decorative morphs and parallax; keep meaningful fades.

## 8. Containment, spacing, and layout

- **Spacing sets the mood.** "A denser layout can feel more serious and focused, while a more spacious layout can feel calm and open." Negative space around the primary element is emphasis.
- Use an 8dp scale (4, 8, 12, 16, 24, 32, 48). Put padding and gaps on parents, not margins on children. Compact screens use 16dp side margins; medium and up 24dp.
- **Group with gaps and containers instead of dividers.** Expressive lists, menus, and search results separate groups with gaps between filled items; dividers are for uncontained lists only.
- Keep reading text at 40 to 60 characters per line.
- **Adapt by breakpoint:** compact (under 600dp) one pane with a navigation bar; medium (600 to 839dp) one pane (two only for low-density content); expanded and up two panes with a navigation rail. Moving up a breakpoint, *reveal* more, don't just enlarge. Details: [references/layout.md](references/layout.md).

### Composing the screen

Components are the last step. Compose from the direction in section 0 and the content itself:

1. **One element leads.** Decide what fills the most space: the content (a photo, a number, a headline), the primary control, or a key summary. Only one leads; everything else is arranged around it. Google: one focal point per screen, "empty space to focus attention."
2. **Space follows rank.** The lead gets scale and room; supporting information packs into compact groups; tertiary details sit at the edges or behind a tap. The most important content gets "ample space and the brightest surface mapping." (Google)
3. **Type can be the structure.** A display-size title or number can anchor the layout by itself, with no card around it. Heavier weight, larger size, color, and spacing create "editorial-like moments." (Google)
4. **Group by spacing or by containers, deliberately.** Proximity and alignment group implicitly and keep the screen open; containers and surface steps group explicitly and add weight (Google). Use containers where a group must read as one unit or be tapped as one; use spacing when items flow as a list or a story. Ungrouped information blends together. When containment marks a state in a list (unread mail, pending tasks), gather the items in that state under one heading and one container group, and let the rest follow as plain rows under their own heading. Rows that alternate between contained and flat read as stripes, not as a state (default; Google groups by "rhythm, similarity").
5. **Give different information different forms.** A summary, a comparison, and a history rarely want the same card grid. Match the grid to the content: hierarchical for editorial and detail screens, modular for equal items, a column for flowing lists (Google, via [references/layout.md](references/layout.md)). Repeating one container shape for everything is what makes a screen look assembled from the catalog.
6. **Let content set the color.** When an image or a subject leads, derive the screen's color from it (section 4) instead of adding accents around it.

Keep familiar patterns underneath (section 3): the composition is new, the interaction model is not.

Patterns seen in shipped Expressive apps (metric card, editorial header, pill hero control, and others) are in [references/components.md](references/components.md#patterns-from-shipped-expressive-apps). Read them for examples after choosing the composition, not to choose it.

## 9. Components: use the Expressive versions

Stock code reaches for the left column. Lint won't flag it.

| Instead of | Use | Compose |
| --- | --- | --- |
| Medium/large top app bar | Flexible app bar (shorter, bigger title, subtitle) | `MediumFlexibleTopAppBar`, `LargeFlexibleTopAppBar` |
| Bottom app bar | Floating or docked toolbar | `HorizontalFloatingToolbar` (FAB overload), `FlexibleBottomAppBar` |
| Segmented buttons | Connected button group | `Row` of `ToggleButton` with `ButtonGroupDefaults.connected*ButtonShapes()` |
| Navigation drawer | Expanded navigation rail | `WideNavigationRail`, `ModalWideNavigationRail` |
| `NavigationBar` | Flexible navigation bar | `ShortNavigationBar` + `ShortNavigationBarItem` |
| Small FAB, speed dial | Medium/large FAB, FAB menu | `MediumFloatingActionButton`, `FloatingActionButtonMenu` |
| Spinner for short waits | Loading indicator (200ms to 5s) | `LoadingIndicator`, `ContainedLoadingIndicator` |
| Plain progress bar | Wavy progress where expressiveness fits (default strokes) | `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator` |
| `ListItem(headlineContent=)` with dividers | Segmented expressive list | `SegmentedListItem` spaced by `ListItemDefaults.SegmentedGap` |
| Flat dropdown menu | Grouped vertical menu | `DropdownMenuPopup` + `DropdownMenuGroup` |
| One-size buttons | Sized, morphing buttons (XS 32 to XL 136dp) | `Button(shapes = ButtonDefaults.shapesFor(height))` |

- **App bar vs toolbar:** "Where app bar supports navigation, toolbar provides critical actions for the current page." App bars get one action (two at most), boosted with a filled or tonal icon button; many actions go in a toolbar. Never show a toolbar and a navigation bar together.
- **Page controls go in a toolbar, not loose text buttons.** Actions for the current page (lyrics and queue in a player) go in a floating toolbar of icon buttons, or in the app bar within its one or two actions. A floating toolbar can instead be local navigation when the selection swaps the content in place: labeled items with one selected. Decide by what the controls do; details in [references/components.md](references/components.md#toolbars).
- **One FAB per screen** for the single most important action; not every screen needs one. An action that belongs to one card or section (logging water in a water card) is a button inside that container: "individual components, such as cards, shouldn't have their own FAB," and FABs aren't for minor actions (Google). When a screen's only action belongs to one section, it has no FAB.
- **Button groups:** same size and shape by default; mixed sizes only in hero moments.
- Component selection, usage rules, and code: [references/components.md](references/components.md).

## 10. Customize without forking

"Material is no longer an 'all or nothing' choice." Keep Material's accessible behavior and put the brand on top:

1. **Use as-is** with theme tokens (color, type, shape, motion).
2. **Wrap** a component to preset its parameters.
3. **Style** it (Compose Styles API as it lands in Material, or colors/shapes parameters).
4. **Fork** only as a last resort: "When you fork, you break your connection to the Material foundation."

Google's brand examples keep behavior intact and add one subtle touch: a custom shadow and animated background on a single promo button, a custom reward animation replacing the ripple when a task completes, oversized graphic components for a bold identity.

## 11. Accessibility and writing

- Contrast 4.5:1 for small text and 3:1 for large text, icons, and controls. Correct role pairs give this automatically, including in medium and high contrast modes; generated or hand-made schemes must read the system contrast setting ([references/theming.md](references/theming.md#contrast-levels)).
- Never rely on color alone: states and links also get an icon, label, or underline.
- Touch targets 48dp with 8dp between them. Support 200% text scaling: text grows, padding doesn't, layouts reflow. Icons, checkboxes, and progress indicators never scale with text. An ellipsis is acceptable only when the full text is reachable (tooltip, link, or expanded view); otherwise wrap (Google).
- Content descriptions state purpose ("Voice search"), not appearance, and never the role.
- Sentence case, second person, no periods on single-sentence labels, contractions, exclamation points only for real celebrations.

## 12. Android conventions

Material says how components look; Android's own design guides say how an app behaves on the platform. Generated code often carries iOS habits instead (Google, "10 steps to Android"):

| iOS habit | Android |
| --- | --- |
| Centered navigation title by habit | Start-aligned title by default (centered is valid when the composition calls for it); large titles are flexible app bars that collapse on scroll |
| Back chevron; "Cancel" and "Done" text in the top bar | Up arrow for hierarchy (system back handles "back"); a close icon to dismiss full-screen modals, with the confirming action in their app bar. Dialogs keep their labeled text buttons |
| Action sheets | Bottom sheets |
| Segmented control | Tabs for peer content destinations; a connected button group for options or view modes within one screen |
| Table rows with disclosure chevrons and hairline dividers | List items without chevrons, grouped with gaps (section 8) |
| SF Symbols | Material Symbols, one style (outlined, rounded, or sharp) app-wide |

- **Edge-to-edge:** backgrounds, images, and scrolling content draw behind the system bars; text and controls stay inset. The gesture bar stays transparent. The status bar is transparent unless content scrolls under it, then it gets one protection (Material top app bars already provide it). No tap targets inside the system gesture insets. Pin text inputs above the keyboard. Implementation: the `edge-to-edge` skill.
- **Settings:** only infrequent preferences; frequent ones sit next to their feature. App version, licenses, and account management get their own destinations, not settings rows. The overview shows each setting's current value; 15 or more settings means subscreens whose titles match the row that opened them. Labels lead with the important word and avoid generic verbs (Set, Change, Manage, Use). A switch for on/off, never a lone radio button. Place "Settings" in secondary navigation, after everything except "Help & feedback."
- **Onboarding and sign-in:** show value before asking for permissions or an account; ask for a permission at the moment it's needed, after explaining why; every intro step is skippable. Passkeys first ("Create a passkey," "Sign in"), with recovery always visible.
- **Notifications:** only timely value (never promotions, "we miss you," or rating requests). Title under 30 characters, text under 40, up to three actions, a large icon only when it adds content (circular for a person, square otherwise), never for branding. Pick channel importance honestly (HIGH only for time-critical messages, calls, alarms), and a tap must open the screen that notification is about.
- **Never lock orientation or theme:** support landscape, resizing, and light and dark.
- **Widgets:** read [references/widgets.md](references/widgets.md) before designing one.

## Wear OS

Watches build from black, use edge-hugging buttons, Roboto Flex with numeral and arc styles, percentage margins, and a 225dp breakpoint; Wear's `MaterialTheme` also defaults to Standard motion. Read [references/wear-os.md](references/wear-os.md) before designing Wear screens; use the `wear-compose-m3` skill for Wear scaffolding and migration.

## Screen check

Look at the rendered screen, not the code. Capture it with `android screen` (see the `android-cli` skill) or a Compose preview screenshot. If you can't render it, report "visual quality unverified" instead of answering from code.

- Is the hierarchy obvious at first glance: does the eye land on the primary goal?
- What gives the screen its character, and does every expressive choice serve the direction from section 0?
- Does the distribution of color reinforce the hierarchy and the atmosphere, with accents on elements that have a job?
- Are supporting content and actions still easy to find and use? Every action must look tappable: text-only buttons belong inside a clear group of actions (a dialog, card, or snackbar), not floating alone.
- Does it hold up with real content lengths, 200% text, and a smaller window?
- Would it still be clear with the expressive flourishes removed?

If the screen could pass for the Material component catalog, it isn't done.

When the render contradicts the direction or the hierarchy, fix the largest mismatch and capture it again before finishing. For stateful screens, also capture the alternate state that changes emphasis or layout (playing and paused, selected and unselected, empty and populated).

Code checks, separately:

- [ ] Theme and type for the surface: phone, tablet, and desktop use `MaterialExpressiveTheme` with the color source from section 4 and Google Sans Flex on every text style; Wear and widgets follow their references.
- [ ] Components from the right column of section 9's table (Wear and widgets: the components their references name).
- [ ] Text and icons use their intended role pairs: no `copy(alpha = ...)`, no hard-coded hex colors.
- [ ] Custom animations use `MaterialTheme.motionScheme`, not `tween()`.
- [ ] Navigation, dismissal, and selection match the chosen Android component's semantics (section 12); content drawn edge-to-edge.

## Quick reference

| Need | Technique | Value / API |
| --- | --- | --- |
| Expressive defaults | Expressive theme | `MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())`, material3 1.5.0-alpha+ |
| Primary action | Largest interactive element, strongest fill, where the hands already are | `ButtonDefaults.shapesFor(height)` at the size the context needs (XS 32 to XL 136dp), `primary` |
| Hierarchy by color | Role contrast | `primary` action, `secondary` selection, `tertiary` accent, `on*` pairs |
| Key text | Emphasized type | `typography.headlineLargeEmphasized` |
| Hero numbers | Display plus tabular figures | `displayLarge` + `fontFeatureSettings = "tnum"` |
| Decorative shape | Shape library | `MaterialShapes.Cookie9Sided.toShape()` (opt-in) |
| Nested corners | Optical roundness | inner = outer minus padding |
| Component motion | Theme springs | `MaterialTheme.motionScheme.fastSpatialSpec()`, `defaultEffectsSpec()` |
| Grouping | Gaps and containers | `ListItemDefaults.SegmentedGap`, `surfaceContainer*` |
| Short wait | Loading indicator | `LoadingIndicator()` for 200ms to 5s |
| Page actions | Floating toolbar | `HorizontalFloatingToolbar` + `vibrantFloatingToolbarColors()` for emphasis |
| Hero moments | Combine tactics | one or two per product |

## References

- [references/theming.md](references/theming.md): read when setting up or changing a theme, color scheme, type scale, shapes, motion scheme, or spacing.
- [references/components.md](references/components.md): read when choosing a component or writing one you haven't used in this session; its patterns section after choosing the composition.
- [references/layout.md](references/layout.md): read when a screen must adapt to tablets, foldables, landscape, or desktop windows, or support mouse and keyboard.
- [references/widgets.md](references/widgets.md): read before designing or reviewing a home screen widget (not yet in scope, see Surfaces).
- [references/wear-os.md](references/wear-os.md): read before designing any Wear OS screen or tile (not yet in scope, see Surfaces).
