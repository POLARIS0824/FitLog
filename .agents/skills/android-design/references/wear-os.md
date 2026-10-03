# Wear OS

Material 3 Expressive on watches, from the Wear OS design guide and androidx sources (Wear compose-material3 1.6.2 stable, checked September 2026). The phone guidance on emphasis, hero moments, and restraint still applies; this file covers what changes on a round, glanceable, battery-bound screen. For scaffolding (`AppScaffold`, `ScreenScaffold`), navigation, and migration, use the `wear-compose-m3` skill.

## Levels of expression

Google grades Wear apps and asks for **excellent or transformative**, and says "don't let your app or tile be unremarkable":

| | Excellent (required) | Transformative (recommended) |
| --- | --- | --- |
| Components | Multiple expressive components | Plus customization |
| Color | Dynamic palette themes | Plus unexpected color combinations |
| Typography | Roboto Flex | Roboto Flex with morphing (animated weight or width) |
| Shape | Some shape library and containment | Expressive containment |
| Motion | Tokens plus springs and shape morphing | Same |
| Hero moments | Product-specific expressive moments | Plus dramatic hierarchy |
| Adaptive | Percentage margins, added value past 225dp | Plus large-screen-specific designs |

## Principles

- **One or two critical tasks**, finished in seconds. No dense data, grids, or spreadsheets.
- **Glanceable:** test while moving; tiles get about 7 seconds of attention.
- **Shallow and vertical:** at most two levels deep; scroll in one direction only.
- **Embrace round:** edge-hugging buttons and bezel-hugging elements (progress rings, scroll indicators, curved time text) use the curve instead of fighting it.

## Theme

```kotlin
@Composable
fun WearAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    MaterialTheme(
        colorScheme = dynamicColorScheme(context) ?: BrandWearColorScheme,
        motionScheme = MotionScheme.expressive(), // Wear defaults to standard
        content = content,
    )
}
```

(`androidx.wear.compose.material3.MaterialTheme`, `MotionScheme`, and `dynamicColorScheme`.)

- `dynamicColorScheme(context)` returns null when the watch face provides no theme; always fall back.
- Wear springs (damping / stiffness): expressive spatial fast 0.7 / 800, default 0.75 / 350, slow 0.8 / 200; standard spatial 1.0 with 1400 / 500 / 260; effects never bounce.
- Wear corner scale: 4, 8, 18, 26, 36dp. There is no `MaterialShapes` on Wear.

## Color

- **Build from black, dark theme only:** "Wear OS uses only the dark theme." App backgrounds are black; tiles never use full-bleed images or color. Container roles are fills, never text or icon colors.
- Dynamic themes come from two watch-face seed colors (primary and tertiary) and meet WCAG AAA. Pair only `X` with `onX`.
- Each accent has base, **dim**, and container roles. Primary: the main action and edge button. PrimaryDim: distinct but not demanding. Secondary: supporting actions in dense UI. Tertiary: standout feedback (tap responses, a goal reached). ErrorDim: emergencies and stop buttons.
- Recommended pairings: Primary with PrimaryDim (main plus complementary); PrimaryDim with Tertiary (highlight plus feedback); Primary with SecondaryContainer (main stands out, rest recedes); Tertiary plus Primary with SecondaryContainer when there's no single main action.
- Tiles: primary on the primary action, secondary or tertiary elsewhere; never all filled primary buttons.

## Typography

- Roboto Flex everywhere. Weight and width are the useful axes: narrow width fits long names and numbers; no wide type in headers; no very light weights at small sizes.
- Roles: `display*` (hero information and metrics), `title*` (wayfinding), `label*` (inside components), `bodyLarge` to `bodyExtraSmall`, `numeralExtraLarge` to `numeralExtraSmall` (a few digits that need no localization, tabular by default, can take expressive width), `arcLarge/Medium/Small` (curved text at the top or bottom edge).
- Curved text: `curvedText(text, style = MaterialTheme.typography.arcMedium)` inside a `CurvedScope`.
- Line height about 1.1x. Display, numeral, and `labelLarge` styles never scale with the user font setting; title, other label, body, and arc styles scale up to 20sp. Test those at the largest and smallest settings.

## Layout

- Top: time text (not on dialogs, confirmations, or pickers) and an optional title. Middle: content. Bottom: actions, ideally an **edge button**. Scroll indicator only on scrolling screens.
- Margins in **percentages** at the outer edges, fixed dp between elements.
- Design for the smallest round screen first (204 to 216dp; check 192dp with large fonts). **Breakpoint at 225dp:** add value past it (more content, bigger controls, the tile title). A larger screen must never show less. Don't stretch or scale up components; don't enlarge fonts unless they're graphic.
- Non-scrolling screens (media, pickers, timers, dialogs): top, middle, bottom sections, the middle stretching; prefer icon buttons over wide pills; show data graphically.
- Elevate unambiguous primary actions to the top of long scrolling pages; label sections in long mixed lists.
- **Rotary input:** on non-scrolling screens with limited space (steppers, pickers, players), let the crown control the main value; tapping alone is not enough.
- **One prominent action per screen;** other actions use lower-emphasis styles.

## Expressive components

| Pattern | API |
| --- | --- |
| Edge-hugging button | `ScreenScaffold(scrollState, edgeButton = { EdgeButton(onClick, buttonSize = EdgeButtonSize.Medium) { ... } })`; sizes ExtraSmall 46, Small 56, Medium 70, Large 96dp |
| Morph on press | `FilledIconButton(onClick, shapes = IconButtonDefaults.animatedShapes()) { ... }` (also `TextButtonDefaults.animatedShapes()`, toggle `animatedShapes()`) |
| Button group | `ButtonGroup { ... }`: one `MutableInteractionSource` per button, `Modifier.animateWidth(interactionSource)`, and the same source passed to the button; the pressed button widens and squeezes its neighbors |
| Morphing list | `TransformingLazyColumn` with `val spec = rememberTransformationSpec()`; per item `Modifier.transformedHeight(this, spec)` and `transformation = SurfaceTransformation(spec)` |

Custom `TransformationSpec` effects (rotating or morphing items at the edges) are hero-moment material, not a default.

## Surfaces

- **Tiles:** one primary use case; a few options plus "more"; every container tappable with a clear outcome; no decorative containers; the system draws the app icon; hide the title on small screens when two rows need 48dp targets; glanceable graphs, not detailed numbers; design empty, sign-in, and error states.
- **Media:** five-button controls with play/pause at 64dp (80dp past 225dp) inside a progress ring; volume via the rotating side button; prioritize downloaded media; tiles show selectable media, not play/pause.
- **Always-on:** few lit pixels; drop frequently updating progress.
- **Haptics:** strong for key moments (payment confirmed), subtle for precision (scrolling), synced with motion.
- **Priorities across surfaces:** complication answers the top question (weather now), the tile adds the next (today), the app holds the rest (hourly, preferences). Notifications only when worth buzzing the wrist.
- **Tiles:** one task per tile (separate goals and workout tiles); show data age when cached ("45 min ago"). One slot-based layout (title, main, bottom). Choose the content by goal: text plus one clear action, up to 5 related action buttons, one key metric around a progress ring, or a glanceable graph. A tile for an ongoing activity shows it is running, and tapping opens it: "Don't start a new instance." 
- **Watch faces:** time first, black as the main color, stay inside the bezel; always-on lights 15% or less of the pixels.
- **Gestures (Wear OS 7):** double pinch triggers the screen's one primary action, wrist turn dismisses; every gesture action must also have a visible button.

## Behaviors

- **Navigation:** swipe right closes the screen; no back buttons. Don't mix vertical and horizontal scrolling (media playback excepted). Non-scrolling multi-page layouts can paginate vertically or horizontally.
- **Physical buttons:** map a multifunction button only to a single-press, binary, reversible action (start/stop, play/pause) that also exists on screen. Never a destructive or multi-step action.
- **Launch:** black background with the 48dp circular app icon centered; build the screen from static text and placeholders, not an indeterminate spinner.
- **Ongoing activities:** the Recents entry states type and status (track, workout duration, ETA); the tile shows a glanceable summary.
- **Clipping:** test with Bold text, larger text, and long languages; calls to action fit the smallest screen; compact chips beat cards in dense layouts.
- **Offline:** an indicator at the top when features are unavailable (gray them out or hide them), at the end of a list when nothing more can load.
- **Sign-in:** passkeys through Credential Manager first, at least two methods total; sign-in-only apps ask immediately, others wait until needed and explain the benefit.
- **Dialogs:** alerts are full-screen interruptions, used sparingly, with text left-aligned past three lines. Confirmations only acknowledge a finished action; they never ask a question. Usually skip them: "A visible change in the UI is enough to show that an action succeeded."
- **Swipe to reveal** (delete, archive in lists): a partial left swipe reveals a primary and optional secondary action; a swipe past 75% commits the primary; destructive actions leave an undo chip.
