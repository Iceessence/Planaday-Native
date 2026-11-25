package com.planaday.nativeapp.core.designtokens

/**
 * Exhaustive navigation directory preserving all 54 original named Planaday workspaces (C-0001..C-0054)
 * plus first-release N-series views, organized into customizable collapsible sidebar groups.
 */
enum class PlanadayRouteGroup(val title: String) {
    DAILY_PLANNING("Daily & Planning"),
    MIND_TIME("Mind, Knowledge & Time"),
    PLANNER_VISUAL("Physical Planner & Canvas"),
    KITCHEN_LIFE("Kitchen & Life-OS"),
    LABORATORY("Laboratory Suite (15)"),
    OFFICE_MATH("Math, Office & Files"),
    PROJECTS_CREATIVE("Projects & Creative Studios"),
    CUSTOMIZATION_SYSTEM("Customization, Sync & System"),
}

enum class PlanadayRoute(
    val id: String,
    val title: String,
    val shortLabel: String,
    val group: PlanadayRouteGroup,
    val requirementRef: String,
    val description: String,
) {
    // Daily & Planning
    TODAY("today", "Today Spread", "Today", PlanadayRouteGroup.DAILY_PLANNING, "C-0004", "Daily priorities, events, quick capture, meals, timers & wellness."),
    HOME("home", "Home Dashboard", "Home", PlanadayRouteGroup.DAILY_PLANNING, "C-0003", "Configurable widgets and workspace overview."),
    QUICK_GLANCE("quick_glance", "Quick Glance", "Glance", PlanadayRouteGroup.DAILY_PLANNING, "C-0002", "Fast-launch compact daily snapshot."),
    ALL_IN_ONE("all_in_one", "All-in-One Scroll", "Scroll", PlanadayRouteGroup.DAILY_PLANNING, "C-0001", "Continuous scrollable personal productivity hub."),
    FLOW_CALENDAR("calendar", "Flow Calendar (1m–4h)", "Calendar", PlanadayRouteGroup.DAILY_PLANNING, "C-0007", "Minute-level zoom, 7 overlap policies, multi-calendar layers & ICS."),
    PERIOD_CALENDAR("period_calendar", "Week / Month / Year", "W/M/Y", PlanadayRouteGroup.DAILY_PLANNING, "C-0006", "Multi-scale period planning views."),
    TASK_CALENDAR("task_calendar", "Task Calendar (Auto-Schedule)", "Schedule", PlanadayRouteGroup.DAILY_PLANNING, "C-0008", "Constraint-aware intelligent task scheduling & learned durations."),
    TASKS("tasks", "Tasks & Carryover", "Tasks", PlanadayRouteGroup.DAILY_PLANNING, "C-0009", "Do Date vs Hard Due Date, carryover age, priorities & dependencies."),
    CHORES("chores", "Chores & Room Health", "Chores", PlanadayRouteGroup.DAILY_PLANNING, "C-0010", "Completion-driven chore recurrence, room health & fairness."),
    GOALS("goals", "Goals & Milestones", "Goals", PlanadayRouteGroup.DAILY_PLANNING, "C-0039", "Long-term goals linked to canonical tasks & milestones."),
    HABITS("habits", "Habits & Routines", "Habits", PlanadayRouteGroup.DAILY_PLANNING, "C-0040", "Streaks, daily/weekly targets & routines."),

    // Mind, Knowledge & Time
    NOTES("notes", "Notes & Knowledge Graph", "Notes", PlanadayRouteGroup.MIND_TIME, "C-0026", "Hierarchical notebooks, WikiLinks, 100k-word editor & Draft Recovery Inbox."),
    THOUGHT_INBOX("thoughts", "Thought Inbox", "Inbox", PlanadayRouteGroup.MIND_TIME, "C-0027", "Rapid capture and triage into linked canonical records."),
    DIARY("diary", "Diary & Mood (1–10)", "Diary", PlanadayRouteGroup.MIND_TIME, "C-0028", "Custom wellness scales, private bathroom logs, photos & trends."),
    LIFE_EVENTS("life_events", "Life Events", "Events", PlanadayRouteGroup.MIND_TIME, "C-0013", "Major personal & historical milestone timeline."),
    SPACED_REVIEW("spaced_review", "Spaced Review", "Review", PlanadayRouteGroup.MIND_TIME, "C-0029", "Spaced repetition learning decks linked to notes."),
    CLOCK_WORLD("clock", "Clock & World Clock", "Clock", PlanadayRouteGroup.MIND_TIME, "C-0030", "6+ clock designs & multi-timezone world clock."),
    ALARMS("alarms", "Smart Alarms", "Alarms", PlanadayRouteGroup.MIND_TIME, "C-0031", "Wall-time alarms with quiet hours, snooze & reboot diagnostics."),
    TIMERS("timers", "Timers & Pomodoro", "Timers", PlanadayRouteGroup.MIND_TIME, "C-0032", "Monotonic elapsed timers, Pomodoro & interval presets."),
    STOPWATCH("stopwatch", "Stopwatch & Time Ledger", "Ledger", PlanadayRouteGroup.MIND_TIME, "C-0033", "Lap stopwatch and persistent time ledger."),
    COUNTDOWNS("countdowns", "Countdowns", "Count", PlanadayRouteGroup.MIND_TIME, "C-0034", "Target date countdown trackers."),

    // Physical Planner & Canvas
    PHYSICAL_PLANNER("physical_planner", "Physical Planner", "Planner", PlanadayRouteGroup.PLANNER_VISUAL, "C-0005", "Covers, bindings, dynamic left/right side tabs, 5k+ bookmarks & 50-state map."),
    WHITEBOARD("whiteboard", "Infinite Whiteboard", "Board", PlanadayRouteGroup.PLANNER_VISUAL, "C-0019", "10k-object spatial canvas, frames, connectors & SVG export."),

    // Kitchen & Life-OS
    RECIPES("recipes", "Recipes & Clipper", "Recipes", PlanadayRouteGroup.KITCHEN_LIFE, "C-0035", "Scalable portions, unit conversion & offline recipe provenance."),
    PANTRY("pantry", "Pantry Lots (FIFO/FEFO)", "Pantry", PlanadayRouteGroup.KITCHEN_LIFE, "C-0036", "Lot expiry tracking, zones, barcodes & par-level alerts."),
    MEAL_PLANNER("meals", "Meal Planner & Food History", "Meals", PlanadayRouteGroup.KITCHEN_LIFE, "C-0037", "Unified Food History linked directly to Today & Recipes."),
    GROCERIES("groceries", "Groceries & Reconciliation", "Grocery", PlanadayRouteGroup.KITCHEN_LIFE, "C-0038", "Aisle-sorted shopping reconciling directly into Pantry & Expenses."),
    HOUSEHOLD("household", "Household Fairness", "Family", PlanadayRouteGroup.KITCHEN_LIFE, "C-0041", "Family profiles, per-item sharing & chore fairness."),
    PEOPLE_GIFTS("people", "People & Gifts", "People", PlanadayRouteGroup.KITCHEN_LIFE, "C-0042", "Contacts, gift ideas & occasions."),
    HEALTH_MEDS("health", "Health & Medications", "Health", PlanadayRouteGroup.KITCHEN_LIFE, "C-0043", "Medication schedules, refills & workouts (organizational aid only)."),
    FINANCE("finance", "Finance, Bills & Budgets", "Finance", PlanadayRouteGroup.KITCHEN_LIFE, "C-0044", "Exact decimal envelopes, expenses, bills, warranties & wishlists."),
    MEDIA_HOBBIES("media", "Media & Hobbies", "Media", PlanadayRouteGroup.KITCHEN_LIFE, "C-0045", "Books, films, games & hobby collections."),
    TRAVEL("travel", "Travel & Hosting", "Travel", PlanadayRouteGroup.KITCHEN_LIFE, "C-0046", "Offline travel packs, packing templates & hosting checklists."),
    HOME_ASSETS("home_assets", "Home, Pets, Vehicles & Bins", "HomeOS", PlanadayRouteGroup.KITCHEN_LIFE, "C-0047", "Home maintenance, vehicles, pets/plants & storage bins."),

    // Laboratory Operations
    LAB_WORKSPACE("lab", "Laboratory Suite (15 Tabs)", "Lab", PlanadayRouteGroup.LABORATORY, "C-0014", "Samples/Custody, Inventory, Budgets, Calibration/QC, Wet vs NIR & QMS (non-LIMS aid)."),

    // Mathematics, Office & Files
    CALCULATOR("calculator", "Calculator, Graph & 23 Units", "Calc", PlanadayRouteGroup.OFFICE_MATH, "C-0015", "Scientific/Lab AST calculator, pole-safe roots & 23-category unit converter."),
    SPREADSHEETS("spreadsheets", "Spreadsheets (DAG Engine)", "Sheets", PlanadayRouteGroup.OFFICE_MATH, "C-0016", "Multi-sheet sparse formulas, #REF!, CSV/XLSX & live Planaday pulls."),
    PRESENTATIONS("presentations", "Presentations (PPTX/HTML)", "Slides", PlanadayRouteGroup.OFFICE_MATH, "C-0017", "Slide designer, presenter view, PPTX import/export & HTML fidelity."),
    UNIVERSAL_FILES("files", "Universal Files & PDF Review", "Files", PlanadayRouteGroup.OFFICE_MATH, "C-0025", "Personal/Lab nested folders, SHA-256 blobs & document review."),
    PHOTO_STUDIO("photos", "Camera & Photo Studio", "Photos", PlanadayRouteGroup.OFFICE_MATH, "C-0024", "SHA-256 deduplicated library, albums & non-destructive edit recipes."),

    // Projects & Creative Studios
    PROJECTS("projects", "Projects & Custom Schemas", "Projects", PlanadayRouteGroup.PROJECTS_CREATIVE, "C-0020", "Nested projects, Gantt critical path & declarative Custom Record Schemas."),
    KANBAN("kanban", "Kanban Boards (Live Tasks)", "Kanban", PlanadayRouteGroup.PROJECTS_CREATIVE, "C-0018", "Zero-duplication live Tasks/Chores board + custom WIP-limited boards."),
    GAME_DEV("game_dev", "Game Dev Studio", "GameDev", PlanadayRouteGroup.PROJECTS_CREATIVE, "C-0021", "GDD, characters, enemies, skill trees, items & deterministic balancing."),
    BOOK_STUDIO("book_studio", "Book Studio & Continuity", "Books", PlanadayRouteGroup.PROJECTS_CREATIVE, "C-0022", "Series, chapters, scenes, worldbuilding, continuity checker & export."),
    CODE_STUDIO("code_studio", "Code Studio & Debugger", "Code", PlanadayRouteGroup.PROJECTS_CREATIVE, "C-0023", "Multi-file editor, isolated desktop compiler/debugger & safe interpreter."),

    // Customization, Reports, Sync & System
    HISTORY_REVIEWS("history", "History & Reviews", "History", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0011", "Permission-controlled timelines & period reflections."),
    REPORTS("reports", "Reports, Pivots & Decisions", "Reports", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0012", "Analytics, evidence snapshots & Decision Journal."),
    INSIGHTS("insights", "Insights & Heatmaps", "Insights", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0050", "Duration calibration & productivity heatmaps."),
    CUSTOM_SPACES("spaces", "Custom Spaces & Tabs", "Spaces", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0048", "Declarative custom tabs, widgets & workspace layout profiles."),
    RITUALS("rituals", "Rituals & Context Schedules", "Rituals", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0049", "Schedule-based automatic workspace switching & contexts."),
    AUTOMATIONS("automations", "Automations (Preview & Guard)", "Auto", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0051", "Trigger-condition-preview-action rules with replay dedup & approval gates."),
    GUIDE("guide", "Guide & Feature Directory", "Guide", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0052", "Offline guide, keyboard shortcuts & complete feature directory."),
    TRASH("trash", "Trash (30-Day Recovery)", "Trash", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0053", "Soft-deleted record recovery & ref-counted confirmed purge."),
    SETTINGS("settings", "Settings, Sync & Diagnostics", "Settings", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0054", "Themes, self-hosted sync pairing, verified backups & build identity."),
}
