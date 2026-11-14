# 27 inventory icons

All 27 original PackBuilder IDs are covered. Each image is a hand-placed 32×32 RGBA pixel master with a transparent background, hard pixel edges, upper-left lighting and shared ink/gold/teal/pink ramps. Denomination chips differ by palette, center emblem and 100 / 1K / 10K labels. Cheese, hound collar and void geode have distinct silhouettes. Three relic icons remain standalone masters; GUI routing is controlled by the root item definitions.

Sources: `sources/icons.mjs`. Rerun with Node and `tools/asset-kit.mjs`. Review sheets: `icons-native-32.png`, `icons-contact-8x.png`, and `icons-stress-16.png`. The 16px stress sheet samples the center pixel of each 2×2 source block, without filtering. Labels and fine runes are intended for the native 32px view; color, shape and center mark carry item identity at 16px.

Visual review: inspected all 27 on light/dark backgrounds and at 8× nearest enlargement. Adjusted the outer paw toe to preserve a transparent 1px contour margin. Technical checks read every saved PNG header, confirm RGBA8 / 32×32, binary alpha, contour margins, non-empty occupancy, and exact ID coverage against the current PackBuilder.java source. These are asset checks, not a claim of in-game client validation.
