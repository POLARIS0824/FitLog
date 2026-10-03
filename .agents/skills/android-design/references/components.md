# Components

Which component to use, how Google says to use it, and the Expressive Compose API. Names and parameters were checked against androidx sources (material3 1.5.0-alpha28) in September 2026. Components marked (experimental) need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.

## Selection guide

| Need | Component | Rules that matter |
| --- | --- | --- |
| Page title and 1 or 2 actions | App bar: search, small, medium flexible, large flexible | One action, two at most; boost the key one with a filled or tonal (optionally wide) icon button, never two filled; fills with a contrasting color on scroll, or stays transparent with filled icon buttons floating over content; flexible bars compress to small on scroll |
| Actions for the current page, or local navigation between related pages | Toolbar: floating (contextual) or docked (global) | Standard colors keep focus on content, vibrant colors emphasize controls or signal a mode like editing; never with a navigation bar on screen; floating can pair with a FAB and collapse on scroll |
| The single most important action | FAB (FAB, medium, large) | One per screen; not every screen needs one; stays put on scroll; bottom half of the screen; color from primary, secondary, or tertiary sets |
| Labeled primary action on a long scroll | Extended FAB (small, medium, large) | One per screen; not inside a set of actions; not with a floating toolbar |
| 2 to 6 related actions behind the FAB | FAB menu | Only from a regular FAB; not with a toolbar or rail; color set matches the FAB |
| Discrete actions | Buttons: elevated, filled, tonal, outlined, text; XS to XL; round or square | Don't overuse; three at most in one arrangement; primary action gets more size, color, or shape |
| Related buttons that react together | Standard button group | Same size and shape by default; mixed sizes only for hero moments; different shape only for selection or meaning |
| Choose an option or a view mode within one screen | Connected button group | Items must be toggleable; one color style for the whole group |
| Main action plus alternatives | Split button | Menu aligned to the trailing half, 4dp away |
| Common icon actions | Icon buttons: filled, tonal, outlined, standard; XS 32 to XL 136dp; narrow, uniform, wide | Filled sparingly; equally important buttons share a size; outlined icon when off, filled when toggled on |
| Contextual choices, filters, entered items | Chips: assist, filter, input, suggestion | Never for Save or Cancel; always in a set; can scroll horizontally |
| Content about one topic | Cards: elevated, filled, outlined | Don't force content into cards when spacing or headings would do; container transform to detail only for hero moments; no internal scroll on phones |
| Visual collection | Carousel: multi-browse, uncontained, hero, centered hero, full-screen | Snap scrolling except uncontained; at most 3 items with text on phones; offer "Show all" |
| Scannable items | Expressive list, standard or segmented | Align leading visuals and text; gaps for contained lists, dividers only for uncontained ones; one selection mode at a time |
| Temporary set of actions | Vertical menu | Group with gaps or dividers; vibrant (tertiary) colors sparingly |
| 3 to 5 top destinations (phone) | Flexible navigation bar | Labels always; fewer than 3 destinations means tabs; no swiping between destinations; reselecting scrolls to top |
| Destinations on larger windows | Navigation rail, collapsed (3 to 7 items) or expanded | Leading edge, outside panes; one navigation component per screen; expanded replaces the drawer |
| Related content on one level | Tabs, primary and secondary | Not for sequential content; avoid swipeable content inside |
| Supplementary content | Bottom sheet (phones), side sheet (medium and up) | Drag handle cycles heights; scrim tap closes |
| Blocking decision | Dialog | Rare; low-priority messages go to a snackbar |
| Brief feedback | Snackbar | One at a time, one action, above the FAB and navigation |
| Wait of 200ms to 5s | Loading indicator | Under 200ms show nothing; over 5s use a progress indicator; one indicator per group |
| On/off setting | Switch | Takes effect immediately; not for opposing options (use a connected button group) |
| Pick one or several | Radio buttons (5 or fewer) or checkboxes | Vertical lists; one radio always selected |
| Value in a range | Slider: standard, centered, range | Takes effect immediately; no vertical range sliders |
| Text entry | Text field, filled or outlined | Don't mix the two in one form |
| Search | Search bar, search app bar (global), or search icon button | Group results with gaps; full screen on phones, docked on tablets |

## Component details

Usage rules from each component's Guidelines tab on m3.material.io (quotes are Google's). Read the section for every component you place.

### States on containers

- Containers never take interaction states; their actionable children do: "The individual components that are actionable within the app bar inherit hover states, not the whole app bar." No hover or pressed on app bars, dialogs, menus, sheets, navigation bars, or tabs as a whole; don't put `clickable` on those containers.
- Disabled applies to buttons, cards, checkboxes, chips, list items, radios, switches, and text fields. App bars, dialogs, menus, sheets, navigation, tabs, and FABs are never disabled: hide an unavailable FAB.

### Choosing emphasis across buttons

- "Each screen should contain a single prominent button for the primary action." Emphasis order: FAB, then filled, tonal, elevated, outlined, text; icon buttons filled, tonal, outlined, standard.
- Filled: final or unblocking actions (Save, Confirm), "ideally for only one action on a page." Tonal: supporting actions that need a little more than an outline (Next in onboarding). Outlined: attention without being primary, or a way to change your mind. Text: "the lowest priority actions," inside cards, dialogs, and snackbars. Elevated: only to separate from a patterned background.
- Pair by stepping down: filled next to text or outlined; outlined next to text. Place buttons side by side when there's room, not stacked.
- "Don't clutter your UI with too many buttons. Consider presenting low-priority actions in overflow menus or as icon buttons."
- Labels 1 to 3 words, sentence case, never wrapped or truncated; the icon leads. Don't stretch buttons into long flat bars on large screens.
- Toggle buttons are for binary selections (Save, Favorite): outlined icon off, filled on, and the shape morphs, so selection shows through more than color.

### Icon buttons

- For common actions with "a system icon with a clear meaning." "Only use a few icon buttons at once"; in dense layouts group them in a toolbar or button group.
- Filled for a key action (sparingly), tonal for secondary actions beside a high-emphasis one (Raise hand next to a filled End call), outlined for medium emphasis, standard for low emphasis or colorful surfaces.
- "When buttons have a similar importance, they should be the same size." Use size and width (narrow, default, wide) for hierarchy.
- Toggle icon buttons only for things with a selected state, never for overflow.

### Button groups and split button

- Standard group: related buttons that react together. Same size and shape by default; "Only use multiple sizes in a group for hero moments." Use filled, tonal, outlined, or elevated buttons: standard icon and text buttons "have no container treatment."
- Connected group: select options, switch views, or sort; "Avoid using a connected group when none of the buttons can be toggled." One color style per group. Groups never wrap to a second line.
- Split button: one action plus a menu of related ones, labels of one or two words; the trailing half always shows the menu icon.

### FAB, extended FAB, FAB menu

- FAB for "the most important action on a screen," constructive (create, share, start), never minor or destructive; "FABs are not needed on every screen." Never disable it: hide it when unavailable.
- Medium FAB is the default on phones, large on tablets. Lower trailing corner on compact and medium windows; in the rail on expanded.
- Extended FAB: for long scrolling screens where a label helps; one per screen; never in the top half of a phone screen, on cards, or on toolbars; collapses to a FAB when scrolling down.
- FAB menu: 2 to 6 closely related actions from a regular FAB; not with a floating toolbar or navigation rail.

### App bars

- Title plus "1–2 essential actions"; "one action, two if necessary." The primary action alters or exits the page (Send, Save, Edit). Many actions go in a toolbar; avoid overflow in the app bar when possible.
- Boost the key action with one filled or tonal (optionally wide) icon button; "Don't put multiple filled or tonal buttons in the app bar." Prefer filled icons.
- Small for dense layouts or scrolled pages; medium flexible and large flexible emphasize the headline and compress to small on scroll. Titles start-aligned or centered, never truncated; wrap to two lines only in flexible bars.
- Straight corners; never shorter than the default height.

### Toolbars

Decide the toolbar's job by what its controls do:

- **Actions for the current page** (they open something or act on the page: lyrics, queue, share, edit): "Use a toolbar to provide actions related to the current page." Fill it with icon buttons: "Icon buttons provide an even hierarchy of controls." Name each one with a plain tooltip ("Use plain tooltips to label icon-only buttons") and a content description. Controls that show and hide a panel are toggle icon buttons, so the open one shows a selected state.
- **Local navigation** (the selection swaps the body content in place between related pages, such as Favorites, Saved, Library): "Floating toolbars can be used as tabs between related subsequent pages in the product hierarchy." Use labeled items with exactly one selected (a tonal pill), and never alongside a navigation bar: "Don't show a navigation bar and a toolbar with navigation controls at the same time."

Rules for both:

- Slots can hold "icon buttons, buttons, and text fields," images, or custom components, but keep one control design: "Avoid mixing too many different controls in the same toolbar."
- Every item must read as its own control (default): labeled buttons in the toolbar's own color with nothing selected merge into one wide pill.
- Emphasize one action at most, with a filled icon button, a different color role, a wide icon button, or a paired FAB: "Avoid emphasizing more than one action at a time." Never a bold button plus a FAB.
- Standard colors (surface container) focus attention on the content; vibrant (primary container) "draws attention to the controls" or signals a temporary mode such as editing. Selected toggles use the toggle tonal style (secondary container in standard, surface container in vibrant).
- Floating: horizontal on phones, only "as big as needed to hold the items inside," fully on screen with an overflow menu for extras, at least 16dp from the window edges (24dp for vertical toolbars), no square filled icon buttons. Vertical mainly on larger windows, opposite the navigation rail; on compact windows only for a simple screen with few controls. One toolbar per compact window.
- Docked: full width at the bottom, for global actions that stay the same across pages; straight corners; replaces the bottom app bar.
- A FAB can sit beside a floating toolbar (never an extended FAB, never a FAB menu); never with a navigation bar.
- On scroll, stay, slide off, or collapse to one key action, but never collapse and slide off at once.

### Navigation bar and rail

- Bar: 3 to 5 top destinations on phones and small tablets; labels always, 1 or 2 words, never truncated; fixed positions; filled icon for the active item. Fewer than 3 destinations means tabs; never on desktop layouts. The FAB sits above the bar, never over it.
- Rail: medium windows and up, 3 to 7 destinations, leading edge, outside panes; "Never use the navigation rail and navigation bar simultaneously." The expanded rail replaces the navigation drawer (no longer recommended).

### Tabs

- "Tabs organize groups of related content that are at the same level of hierarchy"; never for sequential content. Primary tabs under the app bar, secondary tabs inside content.
- Avoid more than 4 fixed tabs; use scrollable tabs (first tab offset 52dp) when labels are long or many. Icons on all tabs or none.
- Tabs attached to an app bar move with it as one unit; avoid swipeable content inside tabbed pages.

### Cards and carousel

- A card holds content and actions on a single topic. "Don't force content into cards when spacing, headlines, or dividers would create a simpler visual hierarchy." At compact sizes, "consider swapping cards for lists."
- Elevated, filled, and outlined differ in style only. A card is either actionable itself or contains actions, never both. No internal scrolling or swipeable content inside a card.
- Carousel: visual items with brief text; snap scrolling except uncontained; at most three items with text on compact screens; buttons go above or below, never on or beside the carousel; offer "Show all."

### Dialogs and sheets

- Dialogs for "critical information that requires a specific user task, decision, or acknowledgement," used "sparingly"; low-priority messages go to a snackbar. At most two actions, confirm at the trailing edge; headlines never apologize or ask "Are you sure?"
- Full-screen dialogs only on compact windows, for multi-step or input-heavy tasks: close icon and a "Save" action.
- Confirm labels say what happens: "Send," "Create," "Delete"; never "OK," "Done," or "Close." Don't disable a full-screen dialog's confirm action; show all errors at once instead. Dismissing a full-screen dialog with unsaved changes asks "Discard?" in a basic dialog.
- Bottom sheets hold supplementary content on phones; modal sheets replace long menus or simple dialogs. Side sheets hold optional content on medium windows and up and always show a close button.

### Chips

- Chips "represent forking paths for a current task, while buttons represent linear steps." Never use chips to finish or progress a task, and never show a single chip alone.
- Assist chips start with a verb; filter chips name what to include; input chips hold user entries; suggestion chips hold product suggestions. All chip sets on a page are either single-select or multi-select.
- Not elevated on the page; labels 20 characters or fewer; wrap or scroll horizontally in one row.

### Selection controls, sliders, text fields

- Checkboxes for multiple related options in a list; radio buttons for one of five or fewer, listed vertically, one always selected; switches for standalone settings that take effect immediately. Opposing options (list or map view) use a connected button group, not a switch. "A switch can't replace a button."
- Sliders take effect immediately and show the full range; "XL sliders should be reserved for hero moments, where the slider itself is the most important element on the page."
- Text fields always have a visible label that never truncates; filled and outlined differ by style only, but never both in one form. Error text replaces supporting text; fields never span the full width of a large screen.

### Feedback and status

- Loading indicator for waits of 200ms to 5s; nothing under 200ms; a progress indicator over 5s. One indicator for a group of items. Use the contained loading indicator over other content.
- The wavy progress shape "can make longer processes feel less static"; it adds height and suits moments "when a more expressive style is appropriate."
- Snackbar: one at a time, one action at most (an "Undo" is ideal), no icons or links, above the FAB and never over navigation.
- Tooltips label icon-only buttons; never hide critical information in one.
- Badges sit at the icon's upper trailing corner, error colors, at most four characters ("999+").
- Dividers: "Only use dividers if items can't be grouped with open space"; "use dividers to group things, not separate individual items."


### Lists

- Align leading visuals and primary text in the same position on every row; put visuals at the leading edge, never mid-row.
- People get circular or expressive-shape avatars (`MaterialShapes`); products and videos get square or rectangular images.
- An item with an image can take a content-based container color from that image, on its enabled state or on interaction.
- Supporting text: one to three lines. Trailing text for price, count, or date. Leading or trailing checkbox (multi-select), radio (single-select), or switch (settings).
- Primary action takes most of the row; secondary actions (bookmark, overflow) go trailing.
- Show selection with two cues, never color alone (a checkmark plus a fill).
- **One selection mode per list:** single-action items (the whole row does one thing) have no nested actions and no persistent selection; single-select items use radios, never checkboxes, and have no nested actions; multi-select items use checkboxes or switches, never radios. One selection control per item (not a checkbox plus a bookmark). In Compose the role covers the whole row: `SegmentedListItem`'s `checked` overload for multi-select, `Modifier.selectable(role = Role.RadioButton)` or `Modifier.toggleable(role = Role.Checkbox)` on custom rows, and switch rows as below. Focus lands on the selected item.
- Swipe reveals mixed-style buttons with the primary action last; a full swipe triggers it; always offer another path (overflow).
- Settings rows with a switch: use the `onClick` overload of `SegmentedListItem` with a trailing `Switch(checked, onCheckedChange = null)` (clicking the row toggles it), and give the row `Modifier.semantics { role = Role.Switch; toggleableState = ToggleableState(checked) }` so TalkBack announces one switch instead of a button plus a switch. The `checked` overload gives the whole row a checkbox role and a selected fill.
- Controls that need the full row width (a connected button group, a slider, a preview) go below the item's label inside the same segmented item, spanning the content width and ignoring the leading icon's column, not in the trailing slot (default).
- Segmented items must stand off their background: on a `surface` page, pass `colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)`.
- Compact: edge-to-edge list opening a full detail page. Medium and up: list-detail side by side; a list can become cards or a carousel on large screens.

### Menus

- A temporary set of actions; persistent actions belong in a toolbar.
- Unavailable items show disabled, not removed.
- Gaps group items more expressively than dividers: at most one or two gaps, fixed size, never in a scrolling menu (use dividers there).
- Slots (image, swatch, progress) only for simple content; never a button or switch inside an item.
- On compact screens, long or complex menus can become a bottom sheet; on larger screens, menus stay in context and can use submenus.
- The trigger keeps its look and shows a pressed state while the menu is open.
- Multi-select menus stay open until dismissed; single-select and action menus close on selection. Disabled items stay focusable but not selectable.

### Search

- Entry point by importance: search app bar when search is the product's main job, a search bar below the title for one view's content, a search icon button when secondary.
- Contained style: container `surfaceContainerHigh` on a `surface` background (never on `surfaceContainer`); 24dp margins unfocused, 12dp focused.
- Leading: a navigation icon button or a non-functional search icon. Trailing: at most two icons (voice, profile avatar, overflow).
- Hint text names what is searchable: "Search your messages," not "Search."
- Results and suggestions are lists: add leading icons, category labels (Recent, Contacts), avatars, filter chips, and gaps between groups.
- Full-screen results on compact, docked results on medium and up; keep the query visible after searching.
- Back releases focus, dismisses suggestions and results, and returns the bar to its resting state. Label results ("Results") so it's clear search is active, and announce new results to screen readers (a live region on the results list). Cap the bar at 720dp wide.

## Patterns from shipped Expressive apps

Recurring moves in Google's showcases and well-reviewed Expressive apps. Read them for examples after choosing the composition (SKILL.md section 8), not to choose it; use one only when it serves the screen's direction.

- **Metric card:** a small label over a huge emphasized number in a tinted container; the number is the hero, the label whispers.
- **Editorial header:** a Display or custom-width title that dominates the top of a screen or sits over a full-bleed photo (an album or trip cover).
- **Shape-masked media:** album art, avatars, and badges clipped to library shapes (Cookie, Flower, Sunny, Clover), sometimes clustered for groups.
- **Pill hero control:** a full-width pill primary action paired with smaller round secondary buttons (Play with skip; Pause with Stop and Restart). Toggles beside it (like, shuffle, repeat) can be standard, tonal, or filled as long as their state stays clear and the main action stays distinguishable.
- **Split action pair:** two large half-width pills in contrasting colors for a two-way decision (Snooze and Stop), or a wide pill beside a round button (Stop with Pause).
- **Floating pill toolbar** at the bottom holding the page's actions (or local navigation), with the selected item as a tonal pill when it is local navigation.
- **Segmented settings:** grouped list items with gaps, leading icons in tonal circles, switches with check and close thumb icons (`Switch(thumbContent = ...)`).
- **Wavy progress** for playback and goals; circular wavy progress for countdowns.

## Expressive API map

| Component | Compose API | Notes |
| --- | --- | --- |
| Flexible app bars | `MediumFlexibleTopAppBar`, `LargeFlexibleTopAppBar` | `subtitle`, `titleHorizontalAlignment`; `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()` |
| Search app bar | `AppBarWithSearch` | `@ExperimentalMaterial3Api` |
| Contained search | `SearchBar(state, inputField)`, `rememberContainedSearchBarState()`, `SearchBarDefaults.containedColors(state)`, `ExpandedFullScreenContainedSearchBar` | |
| Floating toolbar | `HorizontalFloatingToolbar`, `VerticalFloatingToolbar` | FAB overload; `FloatingToolbarDefaults.vibrantFloatingToolbarColors()`, `VibrantFloatingActionButton`, `exitAlwaysScrollBehavior(FloatingToolbarExitDirection.Bottom)`, `Modifier.floatingToolbarVerticalNestedScroll(...)`, `ScreenOffset` |
| Docked toolbar | `FlexibleBottomAppBar` | |
| FABs | `FloatingActionButton`, `MediumFloatingActionButton`, `LargeFloatingActionButton`, `SmallExtendedFloatingActionButton`, `MediumExtendedFloatingActionButton`, `LargeExtendedFloatingActionButton` | |
| FAB menu | `FloatingActionButtonMenu`, `FloatingActionButtonMenuItem`, `ToggleFloatingActionButton` | `Modifier.animateIcon({ checkedProgress })` in the toggle's scope |
| Buttons | `Button`, `ElevatedButton`, `FilledTonalButton`, `OutlinedButton`, `TextButton` with `shapes =` | Heights: `ButtonDefaults.ExtraSmallContainerHeight` 32, `MinHeight` 40, `MediumContainerHeight` 56, `LargeContainerHeight` 96, `ExtraLargeContainerHeight` 136; `shapesFor(h)`, `contentPaddingFor(h)`, `iconSizeFor(h)`, `textStyleFor(h)` |
| Toggle buttons | `ToggleButton`, `ElevatedToggleButton`, `FilledTonalToggleButton`, `OutlinedToggleButton` | `ToggleButtonSize`, `ToggleButtonDefaults.shapesFor(...)`; colors from each variant's own defaults: `ToggleButtonDefaults.colors(...)`, `ElevatedToggleButtonDefaults.colors(...)`, `FilledTonalToggleButtonDefaults.colors(...)`, `OutlinedToggleButtonDefaults.colors(...)` |
| Icon toggles | `IconToggleButton(checked, onCheckedChange, shapes = IconButtonDefaults.toggleableShapes())` and filled/tonal/outlined variants | `IconButtonDefaults.iconToggleButtonColors(...)`, `filledIconToggleButtonColors(...)`; swap to a filled icon when checked |
| Standard button group | `ButtonGroup(overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) })` | Scope: `clickableItem`, `toggleableItem`, `customItem`, `Modifier.animateWidth(interactionSource)` |
| Connected button group | `Row` of `ToggleButton`s | `ButtonGroupDefaults.ConnectedSpaceBetween`, `connectedLeadingButtonShapes()`, `connectedMiddleButtonShapes()`, `connectedTrailingButtonShapes()` |
| Split button | `SplitButtonLayout` | `SplitButtonDefaults.LeadingButton`, `TrailingButton(checked, onCheckedChange)`; tonal, outlined, elevated variants |
| Icon buttons | `IconButton(shapes = IconButtonDefaults.shapes())` and filled/tonal/outlined variants | Size with `Modifier.size(IconButtonDefaults.smallContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide))` (the width option is nested in `IconButtonDefaults`); extraSmall to extraLarge; `Narrow`, `Uniform`, `Wide`. App bar actions use small; medium and up are for hero controls |
| Loading indicator (experimental) | `LoadingIndicator`, `ContainedLoadingIndicator` | Also pull-to-refresh |
| Wavy progress | `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator` | Determinate and indeterminate; `amplitude`, `wavelength`, `waveSpeed`. Keep the default strokes: a thick custom stroke on a small circle turns the wave lumpy. Scale the indicator's size, not its stroke |
| Lists | `ListItem(onClick, ...) { headline }`, `SegmentedListItem(shapes = ListItemDefaults.segmentedShapes(i, n))` | `ListItem(headlineContent = ...)` is deprecated; segmented items spaced by `ListItemDefaults.SegmentedGap` |
| Vertical menus | `DropdownMenuPopup`, `DropdownMenuGroup(shapes = MenuDefaults.groupShape(i, n))`; items: `DropdownMenuItem(onClick, text, shape)` for actions, `SelectableDropdownMenuItem` for single choice (radio role), `CheckableDropdownMenuItem` for multi-choice | `MenuDefaults.itemShape(i, n)`; vibrant: `itemVibrantColors()`, `selectableItemVibrantColors()`, `groupVibrantContainerColor`; submenus by nesting `DropdownMenuPopup` with `MenuDefaults.rememberDropdownMenuPopupPositionProvider(...)` |
| Navigation bar | `ShortNavigationBar`, `ShortNavigationBarItem(iconPosition = NavigationItemIconPosition.Top)` | `Start` icon position for medium windows |
| Navigation rail | `WideNavigationRail(state = rememberWideNavigationRailState())`, `WideNavigationRailItem(railExpanded = ...)`, `ModalWideNavigationRail` | `NavigationSuiteScaffold` swaps bar and rail by window size |
| Slider | `Slider(state = s, onValueChange = { ... })` with `val s = remember { SliderState(value, steps, trackRange = 0f..1f) }`, `VerticalSlider` | The `value =` overload is deprecated in the 1.5 alphas, and `SliderState.valueRange` is now `trackRange`. Custom tracks (a wavy seek bar, inset icons) go in the state overload's `track = { ... }` slot; no XS to XL presets |
| Carousel | `HorizontalMultiBrowseCarousel`, `HorizontalUncontainedCarousel`, `HorizontalCenteredHeroCarousel` | `rememberCarouselState` |

## Icons

Use Material Symbols in one style (outlined, rounded, or sharp) across the app.

1. Download icons from [fonts.google.com/icons](https://fonts.google.com/icons) in the Android format (vector drawable XML), with Fill 0 for the default state and Fill 1 for selected states.
2. Delete only the `android:tint="?attr/colorControlNormal"` attribute from each file's `<vector>` tag, keeping the tag's closing `>`; in Compose, `Icon` applies the color.
3. Draw with `Icon(painterResource(R.drawable.ic_favorite), contentDescription = "Like", tint = ...)`; leave `tint` at its default (`LocalContentColor`) inside components so the role pair stays correct.

`androidx.compose.material:material-icons-extended` is the older Material Icons set, frozen at 1.7.8 and large; use it only for quick prototypes. Snippets below use `Icons.Rounded.*` for brevity.

## Snippets

### Flexible app bar

```kotlin
val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        LargeFlexibleTopAppBar(
            title = { Text("Habits") },
            subtitle = { Text("3 left today") },
            navigationIcon = { /* back icon button */ },
            actions = {
                FilledTonalIconButton(onClick = onAdd, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add habit")
                }
            },
            scrollBehavior = scrollBehavior,
        )
    },
) { padding -> /* content */ }
```

### XL control-led hero button

```kotlin
val height = ButtonDefaults.ExtraLargeContainerHeight
Button(
    onClick = onStart,
    shapes = ButtonDefaults.shapesFor(height),
    modifier = Modifier.fillMaxWidth().heightIn(height),
    contentPadding = ButtonDefaults.contentPaddingFor(height),
) {
    Text("Start session", style = ButtonDefaults.textStyleFor(height))
}
```

### Floating toolbar with a FAB

```kotlin
Box(Modifier.fillMaxSize()) {
    content()
    HorizontalFloatingToolbar(
        expanded = expanded,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onCompose) {
                Icon(Icons.Rounded.Edit, contentDescription = "Compose")
            }
        },
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        modifier = Modifier.align(Alignment.BottomEnd).offset(x = -FloatingToolbarDefaults.ScreenOffset, y = -FloatingToolbarDefaults.ScreenOffset),
    ) {
        IconButton(onClick = onArchive) { Icon(Icons.Rounded.Archive, contentDescription = "Archive") }
        IconButton(onClick = onLabel) { Icon(Icons.Rounded.Label, contentDescription = "Label") }
    }
}
```

Collapse on scroll by attaching `Modifier.floatingToolbarVerticalNestedScroll(expanded, onExpand = { expanded = true }, onCollapse = { expanded = false })` to the scrolling container.

### FAB menu

```kotlin
var open by rememberSaveable { mutableStateOf(false) }
FloatingActionButtonMenu(
    expanded = open,
    button = {
        ToggleFloatingActionButton(checked = open, onCheckedChange = { open = it }) {
            val icon = if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add
            Icon(icon, contentDescription = null, modifier = Modifier.animateIcon({ checkedProgress }))
        }
    },
) {
    FloatingActionButtonMenuItem(onClick = onPhoto, text = { Text("Photo") }, icon = { Icon(Icons.Rounded.PhotoCamera, null) })
    FloatingActionButtonMenuItem(onClick = onNote, text = { Text("Note") }, icon = { Icon(Icons.Rounded.Notes, null) })
}
```

### Connected button group

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
    options.forEachIndexed { index, label ->
        ToggleButton(
            checked = selected == index,
            onCheckedChange = { selected = index },
            shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            },
            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
        ) { Text(label) }
    }
}
```

### Standard button group

```kotlin
ButtonGroup(overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) }) {
    clickableItem(onClick = onPrevious, label = "Previous", icon = { Icon(Icons.Rounded.SkipPrevious, null) })
    toggleableItem(checked = playing, label = "Play", onCheckedChange = onPlayToggle, icon = { Icon(Icons.Rounded.PlayArrow, null) }, weight = 2f)
    clickableItem(onClick = onNext, label = "Next", icon = { Icon(Icons.Rounded.SkipNext, null) })
}
```

### Split button

```kotlin
SplitButtonLayout(
    leadingButton = { SplitButtonDefaults.LeadingButton(onClick = onSend) { Text("Send") } },
    trailingButton = {
        SplitButtonDefaults.TrailingButton(checked = menuOpen, onCheckedChange = { menuOpen = it }) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "More send options")
        }
    },
)
```

### Segmented list

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
    settings.forEachIndexed { index, setting ->
        SegmentedListItem(
            onClick = { onOpen(setting) },
            shapes = ListItemDefaults.segmentedShapes(index, settings.size),
            leadingContent = { Icon(setting.icon, null) },
            supportingContent = { Text(setting.summary) },
        ) { Text(setting.title) }
    }
}
```

Separate groups with a larger gap (16 to 24dp) between columns of segmented items.

### Grouped vertical menu

```kotlin
DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
    val groups = listOf(listOf("Rename", "Duplicate"), listOf("Delete"))
    groups.forEachIndexed { groupIndex, items ->
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(groupIndex, groups.size)) {
            items.forEachIndexed { i, item ->
                DropdownMenuItem(
                    onClick = { onAction(item) },
                    text = { Text(item) },
                    shape = MenuDefaults.itemShape(i, items.size).shape,
                )
            }
        }
        if (groupIndex < groups.lastIndex) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
    }
}
```

### Loading and progress

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Loading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ContainedLoadingIndicator() }

LinearWavyProgressIndicator(progress = { playback.fraction }, modifier = Modifier.fillMaxWidth())
```

### Navigation by window size

```kotlin
NavigationSuiteScaffold(
    navigationItems = {
        destinations.forEach { dest ->
            NavigationSuiteItem(
                selected = dest == current,
                onClick = { onNavigate(dest) },
                icon = { Icon(dest.icon, contentDescription = null) },
                label = { Text(dest.label) },
            )
        }
    },
) { /* current destination */ }
```

Use the `navigationItems` + `NavigationSuiteItem` overload (from `material3-adaptive-navigation-suite`, same 1.5.0 versions) rather than the older `navigationSuiteItems` scope overload. For explicit control, use `ShortNavigationBar` on compact and `WideNavigationRail` on medium and up.
