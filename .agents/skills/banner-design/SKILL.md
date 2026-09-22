---
name: banner-design
description: "Create explicitly requested banner, cover, advertising, or hero-image assets. Not for app toolbar/header code, native Compose UI, or full websites. Use only tools needed for the requested asset."
argument-hint: "[platform] [style] [dimensions]"
license: MIT
metadata:
  author: claudekit
  version: "1.0.0"
---

# Banner Design - Multi-Format Creative Banner System

Design banners across social, ads, web, and print formats. Generates multiple art direction options per request with AI-powered visual elements. This skill handles banner design only. Does NOT handle video editing, full website design, or print production.

## When to Activate

- User requests banner, cover, or header design
- Social media cover/header creation
- Ad banner or display ad design
- Website hero section visual design
- Event/print banner design
- Creative asset generation for campaigns

## Prerequisites

**Python:** This skill uses Python scripts. On Windows, use `python` instead of `python3` (e.g., `python scripts/search.py` instead of `python3 scripts/search.py`).

## Workflow

### Step 1: Reuse Requirements

Infer purpose, dimensions, copy, brand, style, and quantity from the request and available project context. Ask only about a missing detail that materially affects the result; otherwise deliver a reasonable first draft. No mandatory questionnaire or concept approval.

### Step 2: Select Art Direction

Use existing brand guidance when applicable. Read only needed size/style entries from `references/banner-sizes-and-styles.md`. Reference-image research and additional design skills are optional when a concrete visual question remains; do not require Pinterest, Chrome, or a fixed chain of skills.

Use only available tools needed for the requested output. The optional examples below do not require installing absent skills or using their historical `.claude` paths. Resolve helpers relative to the installed skill that owns them.

### Step 3: Design & Generate Options

For each art direction option:

1. **Create HTML/CSS banner** using available implementation tools (a design skill is optional)
   - Use exact platform dimensions from size reference
   - Apply safe zone rules (critical content in central 70-80%)
   - Max 2 typefaces, single CTA, 4.5:1 contrast ratio
   - Reuse known brand context; optionally run an available brand helper when extraction is needed

2. **Generate visual elements**, only when needed, with an available image-generation capability

   Choose an available generator suited to the requested dimensions and image type. Prompt research and generation are optional when existing assets or HTML/CSS already meet the brief. Do not install missing legacy skill dependencies or send private assets to external services without authorization.

### Step 4: Export Banners to Images

After designing HTML banners, export each to PNG using `chrome-devtools` skill:

1. **Serve HTML files** via local server (python http.server or similar)
2. **Screenshot each banner** at exact platform dimensions:
   ```bash
   # Export banner to PNG at exact dimensions
   node .claude/skills/chrome-devtools/scripts/screenshot.js \
     --url "http://localhost:8765/banner-01-minimalist.html" \
     --width 1500 --height 500 \
     --output "assets/banners/{campaign}/{variant}-{size}.png"
   ```
3. **Auto-compress** if >5MB (Sharp compression built-in):
   ```bash
   # With custom max size threshold
   node .claude/skills/chrome-devtools/scripts/screenshot.js \
     --url "http://localhost:8765/banner-02-gradient.html" \
     --width 1500 --height 500 --max-size 3 \
     --output "assets/banners/{campaign}/{variant}-{size}.png"
   ```

**Output path convention** (per `assets-organizing` skill):
```
assets/banners/{campaign}/
├── minimalist-1500x500.png
├── gradient-1500x500.png
├── bold-type-1500x500.png
├── minimalist-1080x1080.png    # if multi-size requested
└── ...
```

- Use kebab-case for filenames: `{style}-{width}x{height}.{ext}`
- Date prefix for time-sensitive campaigns: `{YYMMDD}-{style}-{size}.png`
- Campaign folder groups all variants together

### Step 5: Present Options & Iterate

Present all exported images side-by-side. For each option show:
- Art direction style name
- Exported PNG preview (use `ai-multimodal` skill to display if needed)
- Key design rationale
- File path & dimensions

Iterate based on user feedback until approved.

## Banner Size Quick Reference

| Platform | Type | Size (px) | Aspect Ratio |
|----------|------|-----------|--------------|
| Facebook | Cover | 820 × 312 | ~2.6:1 |
| Twitter/X | Header | 1500 × 500 | 3:1 |
| LinkedIn | Personal | 1584 × 396 | 4:1 |
| YouTube | Channel art | 2560 × 1440 | 16:9 |
| Instagram | Story | 1080 × 1920 | 9:16 |
| Instagram | Post | 1080 × 1080 | 1:1 |
| Google Ads | Med Rectangle | 300 × 250 | 6:5 |
| Google Ads | Leaderboard | 728 × 90 | 8:1 |
| Website | Hero | 1920 × 600-1080 | ~3:1 |

Full reference: `references/banner-sizes-and-styles.md`

## Art Direction Styles (Top 10)

| Style | Best For | Key Elements |
|-------|----------|--------------|
| Minimalist | SaaS, tech | White space, 1-2 colors, clean type |
| Bold Typography | Announcements | Oversized type as hero element |
| Gradient | Modern brands | Mesh gradients, chromatic blends |
| Photo-Based | Lifestyle, e-com | Full-bleed photo + text overlay |
| Geometric | Tech, fintech | Shapes, grids, abstract patterns |
| Retro/Vintage | F&B, craft | Distressed textures, muted colors |
| Glassmorphism | SaaS, apps | Frosted glass, blur, glow borders |
| Neon/Cyberpunk | Gaming, events | Dark bg, glowing neon accents |
| Editorial | Media, luxury | Grid layouts, pull quotes |
| 3D/Sculptural | Product, tech | Rendered objects, depth, shadows |

Full 22 styles: `references/banner-sizes-and-styles.md`

## Design Rules

- **Safe zones**: critical content in central 70-80% of canvas
- **CTA**: one per banner, bottom-right, min 44px height, action verb
- **Typography**: max 2 fonts, min 16px body, ≥32px headline
- **Text ratio**: under 20% for ads (Meta penalizes heavy text)
- **Print**: 300 DPI, CMYK, 3-5mm bleed
- **Brand**: reuse applicable existing guidance; helper scripts are optional

## Security

- Protect secrets and private data; do not expose environment values or credentials.
- Local drafts do not authorize publication, external messaging, or sending private assets to third-party services. Obtain authorization for sensitive/external actions when not already granted.
- User-requested artifact paths and editable skill documentation may be shared; do not treat them as secrets merely because they are local.


- Never fabricate or expose personal data
