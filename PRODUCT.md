# FitLog

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

The author uses this app personally to record training and review progress.

## Product Purpose

Complete a local training analysis loop: enter training, browse records, review extracted Markdown training data, and inspect progress.

## Operating Context

The app is built with Jetpack Compose. Today starts a local record, Log browses local records or an optional Markdown library, Insight reviews training, and Settings manages appearance, AI configuration, and the library.

## Capabilities and Constraints

- Local database records work independently of a Markdown library or AI service.
- Markdown is external and read-only. Original content, AI candidates, and human confirmations remain separate.
- Preserve local records, human corrections, and global exercise aliases.
- Statistics include local records and version-valid confirmations from the current library.
- Failures offer explicit manual retry; preserve unsaved input.
- Keep English and Chinese strings in Android resources.
- Preserve the bottom navigation toolbar and its FAB menu, including appearance, layout, behavior, motion, and semantics.
- Log's app bar precedes its source selector, search, filters, and list.
- Reuse the established Material 3 Expressive theme and business components.

## Brand Commitments

FitLog retains its existing native Android identity, Google Sans Flex typography, dynamic color, and light/dark themes. The current task refines Today, Log, Insight, and Settings rather than replacing that identity.

## Evidence on Hand

Current Compose screens, preview fixtures, and string resources in `app/src/main/`. Device verification uses the existing Pixel_10 without clearing its data. No new commercial or performance claims are authorized.

## Product Principles

- Let training records lead the interface.
- Keep data ownership and source distinctions clear.
- Make the next action and current state easy to recognize.
- Use native interactions and restrained emphasis.
