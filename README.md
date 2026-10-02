# Mob — on-device recipe & meal-planning app for Android

A comprehensive Android clone of the [Mob](https://www.mob.co.uk) experience: real recipes, personalised feed,
search with diet and "what's in my fridge" filters, weekly planner, aisle-sorted smart shopping list,
saved recipes and collections, curated meal plans, Cook Mode, Adapt Recipe, an Inspiration feed, URL import,
and an assistant ("Ask Mob"). Everything — including the reasoning model behind recommendations, recipe
adaptation, auto-planning and the assistant — runs **on the phone**. There is no backend server.

> Mob Premium is intentionally not reproduced: every feature is unlocked.

## What's real and where it comes from

Recipes are never generated. They are fetched live from public sources and cached on the device:

| Source | Key needed | What you get |
|---|---|---|
| [TheMealDB](https://www.themealdb.com/api.php) | No | Hundreds of real recipes with photos, YouTube videos, cuisines and categories |
| [USDA MyPlate Kitchen via myplate.food](https://myplate.food/api) | No | 1,072 public-domain USDA recipes with full nutrition tables |
| [Spoonacular](https://spoonacular.com/food-api) | Optional (Settings) | Thousands more with diet/intolerance filters and analysed nutrition |
| Any recipe URL | No | Import via schema.org `Recipe` JSON-LD or microdata (BBC Good Food, Jamie Oliver, NYT Cooking, Mob…). Share a page to the app and it imports |
| Any MCP server | Optional (Settings) | Tools from Model Context Protocol servers become available to the assistant over Streamable HTTP |

Nutrition is shown when the source publishes it. When it doesn't, the on-device model can produce an
estimate, clearly labelled as an estimate.

## On-device AI

`ai/LlmRouter` picks the best available engine on each call:

1. **Gemini Nano** through the ML Kit GenAI Prompt API (AICore) — Pixel 9/10 and other AICore phones.
2. **Gemma** through Google's LiteRT-LM, running inside the app from a `.litertlm` file the app downloads
   (default: Gemma 3 1B int4, ~550 MB; paste a Gemma 3n E2B URL for stronger reasoning on a Pixel).
3. **Rules only** — every AI feature has a deterministic fallback so the app is fully usable without a model.

The engine adapters are isolated in `ai/engines/GeminiNanoEngine.kt` and `ai/engines/LiteRtLmEngine.kt`.
Both SDKs are pre-1.0 and their surfaces have moved between releases; if a method name differs in the SDK
version you build with, those two files are the only places to touch.

AI features: personalised "For you" feed with one-line reasons, Adapt Recipe (vegan/veggie/GF/DF, spicier,
milder, more protein, lighter, quicker, cheaper, custom swaps), nutrition estimates, auto-plan the week,
and the Ask Mob assistant which calls tools (search, fridge search, get recipe, save, add to planner,
plan week, shopping list, preferences, plus any MCP tools) in a bounded ReAct loop.

### MCP servers

Ready-made recipe MCP servers you can host and connect from Settings → Recipe sources → MCP servers:

- [pipeworx-io/mcp-recipes](https://github.com/pipeworx-io/mcp-recipes) — TheMealDB search
- [suraj-yadav-aiml/recipe-mcp](https://github.com/suraj-yadav-aiml/recipe-mcp) — FastMCP, remote-deployable
- [ddsky/spoonacular-mcp](https://github.com/ddsky/spoonacular-mcp) — Spoonacular
- [recipe-mcp/recipe-mcp](https://github.com/recipe-mcp/recipe-mcp) — multi-source adapters

Run one with a Streamable HTTP transport (most FastMCP servers: `--transport streamable-http`) and paste its
`/mcp` URL. Cleartext HTTP is only allowed for `localhost`, `10.0.2.2` and `*.local` (see
`res/xml/network_security_config.xml`); use HTTPS otherwise.

## Building

Requirements: Android Studio Panda or newer (anything that supports AGP 9.4), JDK 17 or newer, and Android SDK 37
(`platforms;android-37.0`). Then:

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest
adb install app/build/outputs/apk/debug/app-debug.apk
```

The project uses AGP 9.4 (built-in Kotlin) on Gradle 9.8, Kotlin 2.4, Compose BOM 2026.09, Room 2.8 with KSP,
Ktor 3.5, Coil 3, Jsoup, the official MCP Kotlin SDK 0.15, ML Kit GenAI Prompt 1.0.0-beta4 and LiteRT-LM 0.17.
Versions live in `gradle/libs.versions.toml`. The debug APK is large (~110 MB) because LiteRT-LM ships native
libraries; it targets arm64 only.

**Build status.** `assembleDebug` and `testDebugUnitTest` succeed (19 unit tests pass). The app has not yet been
run on a device or emulator, so screens and the two on-device AI engines are compile-checked but untested at
runtime. Expect rough edges on first launch.

## Project layout

```
app/src/main/kotlin/com/mobuk/app
├── MobApp.kt, MainActivity.kt        Application (Coil image loader, DI graph) and single-activity host
├── di/AppGraph.kt                    Hand-rolled dependency graph (lazy singletons)
├── domain/model                      Recipe, Ingredient, Nutrition, PlannerEntry, ShoppingItem, MealPlan, UserPrefs…
├── domain/logic                      QuantityParser/Formatter, ServingScaler, AisleClassifier, DietTagger,
│                                     TimerExtractor, SearchFilters, IngredientNormalizer (shopping merge)
├── data/local                        Room database, DAOs, entity mappers, DataStore prefs
├── data/remote                       TheMealDB, MyPlate, Spoonacular providers; schema.org URL importer
├── data/mcp                          McpManager (official Kotlin SDK, Streamable HTTP)
├── data/repository                   Recipe, Planner, Shopping, Collection, MealPlan, Chat repositories
├── ai                                LlmEngine, LlmRouter, Prompts, JsonExtract
├── ai/engines                        GeminiNanoEngine (ML Kit), LiteRtLmEngine (Gemma)
├── ai/download                       ModelDownloader + foreground ModelDownloadService
├── ai/agent                          ToolRegistry, BuiltInTools, RecipeAgent (+ RuleAssistant fallback)
├── ai/features                       Recommender, RecipeAdapter (+ Substitutions), WeekPlanner,
│                                     NutritionEstimator, MealPlanThemes
└── ui                                Compose screens: home, search/browse, recipe detail, cook mode, planner,
                                      shopping, saved/collections, meal plans, inspiration, assistant,
                                      importer, settings (AI models, MCP servers), onboarding
```

## Feature map vs. Mob

| Mob feature | Here |
|---|---|
| Recipe library | Live from TheMealDB, MyPlate, Spoonacular, URL import, MCP |
| Search & filters (veggie, vegan, GF, cuisine, category, time, calories) | Search tab with filter sheet and sort |
| "What's in my fridge" | Fridge search chip → ingredient search across sources |
| Personalised feed | Home "For you" (rules + on-device model re-ranking with reasons) |
| Inspiration Feed (full-screen video) | Inspiration screen: vertical pager, video opens in source player |
| Weekly planner, servings per meal | Planner tab, week navigation, move/serve/cooked, auto-plan |
| Smart shopping list by aisle, portion scaling, custom items | Shopping tab, synced from planner, merged quantities, share |
| Saved recipes & collections | Saved tab with collections, covers, add-all-to-list |
| Curated meal plans | 10 themed plans assembled from live recipes; add whole plan to planner |
| Cook Mode (screen awake, step by step, videos) | Cook Mode with timers detected from the method, big text, ingredient checklist |
| Adapt Recipe | On-device model rewrite with rule-based substitution fallback; save as your own |
| Nutrition & macros | From source, or on-device estimate |
| Profile & preferences | Onboarding + Settings: diets, allergies, dislikes, cuisines, goals, household |
| Premium / paywall | Not reproduced; all features unlocked |
| Editorial "Life" articles, chefs, shop | Not reproduced (editorial content can't be sourced without a licence) |

## Privacy

Recipe requests go directly from the phone to the public recipe APIs above (and to any MCP server you add).
Preferences, planner, lists, chat history and downloaded models stay in app-private storage. Model inference
is local.
