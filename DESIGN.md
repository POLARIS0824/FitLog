---
name: FitLog
description: Native Android training records and analysis in Material 3 Expressive.
colors:
  fallback-light-primary: "#6750A4"
  fallback-light-onPrimary: "#FFFFFF"
  fallback-light-primaryContainer: "#EADDFF"
  fallback-light-onPrimaryContainer: "#4F378B"
  fallback-light-secondaryContainer: "#E8DEF8"
  fallback-light-onSecondaryContainer: "#4A4458"
  fallback-light-tertiaryContainer: "#FFD8E4"
  fallback-light-onTertiaryContainer: "#633B48"
  fallback-light-surface: "#FEF7FF"
  fallback-light-onSurface: "#1D1B20"
  fallback-light-onSurfaceVariant: "#49454F"
  fallback-light-surfaceContainerLow: "#F7F2FA"
  fallback-light-surfaceContainerHigh: "#ECE6F0"
  fallback-light-surfaceContainerLowest: "#FFFFFF"
  fallback-dark-primary: "#D0BCFF"
  fallback-dark-onPrimary: "#381E72"
  fallback-dark-primaryContainer: "#4F378B"
  fallback-dark-onPrimaryContainer: "#EADDFF"
  fallback-dark-secondaryContainer: "#4A4458"
  fallback-dark-onSecondaryContainer: "#E8DEF8"
  fallback-dark-tertiaryContainer: "#633B48"
  fallback-dark-onTertiaryContainer: "#FFD8E4"
  fallback-dark-surface: "#141218"
  fallback-dark-onSurface: "#E6E0E9"
  fallback-dark-onSurfaceVariant: "#CAC4D0"
  fallback-dark-surfaceContainerLow: "#1D1B20"
  fallback-dark-surfaceContainerHigh: "#2B2930"
  fallback-dark-surfaceContainerLowest: "#0F0D13"
typography:
  display:
    fontFamily: "Google Sans Flex"
    fontVariation: '"wdth" 90, "ROND" 80'
  headline:
    fontFamily: "Google Sans Flex"
    fontVariation: '"wdth" 90, "ROND" 80'
  title:
    fontFamily: "Google Sans Flex"
    fontVariation: '"wdth" 100, "ROND" 0'
  body:
    fontFamily: "Google Sans Flex"
    fontVariation: '"wdth" 100, "ROND" 0'
  label:
    fontFamily: "Google Sans Flex"
    fontVariation: '"wdth" 100, "ROND" 0'
  display-emphasized:
    fontFamily: "Google Sans Flex"
    fontWeight: 750
    fontVariation: '"wdth" 90, "ROND" 80'
  headline-emphasized:
    fontFamily: "Google Sans Flex"
    fontWeight: 750
    fontVariation: '"wdth" 90, "ROND" 80'
  title-emphasized:
    fontFamily: "Google Sans Flex"
    fontWeight: 700
  body-emphasized:
    fontFamily: "Google Sans Flex"
    fontWeight: 700
  label-emphasized:
    fontFamily: "Google Sans Flex"
    fontWeight: 700
spacing:
  CompactPadding: "16dp"
  ExpandedPadding: "24dp"
  NavigationBottomPadding: "100dp"
  TodayBottomPadding: "120dp"
  SheetPadding: "24dp"
---

# Design System: FitLog

## Overview

**Creative North Star: "Native Android training"**

This names the existing identity confirmed in PRODUCT.md, rather than a new visual direction. FitLog uses Material 3 Expressive, bundled Google Sans Flex, wallpaper-derived color, and paired light/dark themes. Rounded headings and tactile native controls give training records emphasis while supporting text stays quieter.

The system is implemented in Jetpack Compose. Frontmatter records the app's explicit overrides and a selected set of reused static fallback colors. Native dimensions use dp; they are not CSS pixels. Material defaults remain owned by the installed Compose library, including typography sizes, line heights, shape geometry, control states, and spring parameters. This document does not freeze those defaults into a second implementation.

**Key Characteristics:**

- Native semantic color pairs that adapt to appearance preferences.
- Rounded display and headline text, with neutral reading text.
- Tonal containers, connected choices, and expressive press feedback.
- Centered content widths with gutters that respond to window size.

## Colors

Wallpaper colors are the default on Android 12 and later when dynamic color is enabled. The fallback palette is violet-led with muted secondary and rose tertiary roles. The hex tokens above describe only the static schemes in Color.kt; screenshot colors are not global brand constants.

### Primary

`primary` and `onPrimary` carry filled actions. `primaryContainer` and `onPrimaryContainer` support emphasized containers, including the Today training card. Primary also highlights Insight quantities and Settings section titles.

### Secondary

`secondaryContainer` and `onSecondaryContainer` mark selected connected choices and supporting icon containers. Connected choices use `surfaceContainerHigh` and `onSurfaceVariant` when unselected.

### Tertiary

`tertiaryContainer` and `onTertiaryContainer` provide a distinct Settings icon tone. Insight uses the native `tertiary` role for improvements and achievements; its full static definition remains in Color.kt.

### Neutral

`surface` is the main page plane. `surfaceContainerLow` forms app bars, training summaries, empty states, and record cards. Settings places `surfaceContainerLowest` groups over a `surfaceContainerLow` page. `onSurfaceVariant` distinguishes descriptions and metadata from primary content.

**The Semantic Pair Rule.** Read each container and its corresponding on-color from `MaterialTheme.colorScheme`; preserve those relationships in both themes.

## Typography

**Display Font:** bundled Google Sans Flex, with Android font fallback for unsupported scripts.

**Body Font:** the same bundled family with neutral reading axes. CJK uses Android fallback.

Type.kt starts with Material `Typography()` and retains its role sizes, line heights, and letter spacing. Display and headline roles use the narrower, rounded axes recorded above. Title, body, and label roles use width 100 and roundness 0. The weight axis follows the role's weight; optical sizing follows its font size. Emphasized display/headline weights are 750 and other emphasized weights are 700.

| Native role in use | Observed purpose |
| --- | --- |
| `displaySmallEmphasized` | Today training message |
| `headlineMediumEmphasized` | Insight summary |
| `headlineSmallEmphasized` | Log empty state and Insight quantities |
| `titleLargeEmphasized` | Record dates, progress-card names, app-bar title weight |
| `titleMediumEmphasized` | Main action labels, Settings destinations, section labels |
| `bodyLarge`, `bodyMedium`, `bodySmall` | Reading text, descriptions, metadata |
| `labelLargeEmphasized`, `labelLarge`, `labelMedium` | Selection, compact actions, scan status |

**The Native Scale Rule.** Extend the existing Material typography roles and font overrides; allow system font scaling and Android script fallback to determine rendered text metrics.

## Layout

FitLogDimens.kt owns the reusable page dimensions. Horizontal padding changes from `CompactPadding` to `ExpandedPadding` at a window width of 600dp. Standard content is centered within `ContentWidth` (640dp); wider record and Insight surfaces use `WideContentWidth` (840dp). App-bar backgrounds span the window while their content follows the relevant width cap.

Main lists reserve `NavigationBottomPadding`; Today reserves `TodayBottomPadding`. Settings subpages use one scrolling list with an IME-aware content area. Reused spacing within the sampled screens includes 4dp metadata gaps, 8dp compact gaps, 12dp list gaps, 16dp content gaps, 20dp record/Settings-row insets, 24dp summary insets, and 32dp larger section gaps. These are observed uses, not a new mandatory spacing scale.

Today places its supporting library beside the training card when the available width is at least 840dp and font scale is below 1.5. Otherwise it stacks them. Its decorative illustration is omitted when available content height is below 600dp. At font scale 1.3 and above, its main button drops the decorative icon and uses flexible padding. Connected controls measure their labels and scroll horizontally when the choices need more room.

## Elevation & Depth

The sampled page content is primarily tonal: cards and Settings groups specify surface colors without custom shadow recipes. The incumbent bottom toolbar and FAB have their own native elevation and behavior. Their visible lift is not a shadow token for ordinary content cards.

Motion remains native `MotionScheme.expressive()`. Expanded app bars use its default spatial specification when snapping; Markdown status feedback uses fast spatial and fast effects specifications. Exact spring parameters and interaction states remain with Material components.

## Shapes

The theme supplies Material Expressive shapes without a custom shape scale. Large summary and empty-state containers use `MaterialTheme.shapes.extraLarge`; record and progress cards use `large`; Settings groups use `extraLargeIncreased`. The Markdown search field is circular. Segmented lists and connected controls take their position-aware shapes from Material defaults.

Expressive buttons use `ButtonDefaults.shapes()` or `shapesFor(height)` rather than an independent radius. Shape changes on press and selection remain part of the native component behavior. The populated Log list header is an unrounded surface; its padded empty-state card remains rounded. This is a local composition detail, not a global corner rule.

## Components

### Buttons

Primary training and connect-folder actions are filled buttons. Main recording actions use `MediumContainerHeight`, matching content padding, and the corresponding expressive shapes. Supporting actions use filled tonal, outlined, or text variants according to the existing component hierarchy. Native enabled, focus, press, and disabled behavior is inherited.

### Connected choices

FitLogConnectedButtonGroup is the shared single-choice control for source and range selection. It measures the emphasized label, gives choices equal widths, uses native connected leading/middle/trailing shapes, and exposes radio-button semantics within a selectable group. Selection uses the secondary container pair; limited width reveals choices by horizontal scrolling.

### Cards / Containers

Training summaries and empty states use broad tonal containers with content insets. Local records and Insight progress cards are individually clickable. SettingsGroup is one continuous surface containing destination rows; each SettingsEntry combines a circular icon container, emphasized title, and current-state or purpose subtitle. Its icon container is 48dp with a 24dp icon; rows use 20dp insets and a 16dp icon-to-text gap.

### Inputs / Fields

Connected Markdown lists expose an `OutlinedTextField` with a circular shape, a search icon, a conditional clear action, and a single-line input model. Filters use native `FilterChip`; supporting sort and tools actions use expressive outlined buttons. Their visual states are Material defaults, not bespoke CSS styles.

### Navigation

FitLogTopAppBar provides pinned or medium flexible app bars, native back/actions, title heading semantics, and a `surfaceContainerLow` background. Log places its app bar first, followed by source selection, search/filter tools, and list content.

**Protected navigation.** Preserve FitLogNavigationToolbar.kt and its paired FAB menu, including appearance, layout, interactions, motion, and semantics, unless the author explicitly requests a change to them.

## Do's and Don'ts

### Do:

- Do use the native semantic color pairs in both light and dark appearance.
- Do reuse the bundled type overrides, shared page dimensions, and existing business components.
- Do keep English and Chinese interface strings in Android resources and allow their text to wrap or scroll as the component supports.
- Do keep Log's app bar above its source selector, search, filters, and records.

### Don't:

- Don't turn wallpaper-derived screenshot colors into fixed brand hex values.
- Don't replace native Material size, shape, motion, or state specifications with invented web equivalents.
- Don't change the bottom toolbar or FAB menu without the author's explicit request.
