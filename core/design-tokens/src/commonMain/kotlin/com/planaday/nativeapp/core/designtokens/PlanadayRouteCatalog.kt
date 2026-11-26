package com.planaday.nativeapp.core.designtokens

/**
 * Exhaustive navigation directory covering all 54 Planaday workspaces (C-0001..C-0054)
 * with clean, customer-facing labels and descriptions (Update 2 §7 — internal requirement
 * references are kept strictly for Developer Diagnostics, never cluttering customer UI).
 */
enum class PlanadayRouteGroup(val title: String) {
    DAILY_PLANNING("Plan & Focus"),
    MIND_TIME("Notes, Diary & Time"),
    PLANNER_VISUAL("Planner & Canvas"),
    KITCHEN_LIFE("Kitchen & Life"),
    LABORATORY("Laboratory"),
    OFFICE_MATH("Math, Office & Files"),
    PROJECTS_CREATIVE("Projects & Creative"),
    CUSTOMIZATION_SYSTEM("Workspace & System"),
}

enum class PlanadayRoute(
    val id: String,
    val title: String,
    val shortLabel: String,
    val group: PlanadayRouteGroup,
    val requirementRef: String,
    val description: String,
) {
    // Plan & Focus
    TODAY("today", "Today", "Today", PlanadayRouteGroup.DAILY_PLANNING, "C-0004", "Your daily schedule, priorities, habits, quick capture, and wellness."),
    HOME("home", "Home", "Home", PlanadayRouteGroup.DAILY_PLANNING, "C-0003", "Customizable dashboard widgets across your active workspace."),
    QUICK_GLANCE("quick_glance", "Quick Glance", "Glance", PlanadayRouteGroup.DAILY_PLANNING, "C-0002", "Compact daily snapshot for fast check-ins."),
    ALL_IN_ONE("all_in_one", "All-in-One", "Overview", PlanadayRouteGroup.DAILY_PLANNING, "C-0001", "Continuous scrollable view of your day."),
    FLOW_CALENDAR("calendar", "Flow Calendar", "Calendar", PlanadayRouteGroup.DAILY_PLANNING, "C-0007", "Interactive day, week, month, year, and agenda calendar with zoom and layers."),
    PERIOD_CALENDAR("period_calendar", "Month & Year", "Month", PlanadayRouteGroup.DAILY_PLANNING, "C-0006", "Multi-week, monthly, and annual planning horizons."),
    TASK_CALENDAR("task_calendar", "Schedule Assistant", "Schedule", PlanadayRouteGroup.DAILY_PLANNING, "C-0008", "Visual task scheduling and time-block planning around your calendar."),
    TASKS("tasks", "Tasks", "Tasks", PlanadayRouteGroup.DAILY_PLANNING, "C-0009", "Tasks, subtasks, priorities, deadlines, dependencies, and carryover."),
    CHORES("chores", "Chores", "Chores", PlanadayRouteGroup.DAILY_PLANNING, "C-0010", "Completion-driven chores, room health, and household rotation."),
    GOALS("goals", "Goals", "Goals", PlanadayRouteGroup.DAILY_PLANNING, "C-0039", "Long-term goals, milestones, and linked tasks."),
    HABITS("habits", "Habits", "Habits", PlanadayRouteGroup.DAILY_PLANNING, "C-0040", "Daily and weekly habits, streaks, and routines."),

    // Notes, Diary & Time
    NOTES("notes", "Notes", "Notes", PlanadayRouteGroup.MIND_TIME, "C-0026", "Nested notebooks, large documents, outlines, links, revisions, and draft recovery."),
    THOUGHT_INBOX("thoughts", "Inbox", "Inbox", PlanadayRouteGroup.MIND_TIME, "C-0027", "Quick thought capture and triage into tasks, notes, or projects."),
    DIARY("diary", "Diary", "Diary", PlanadayRouteGroup.MIND_TIME, "C-0028", "Daily journal, mood & wellness ratings, private logs, and trends."),
    LIFE_EVENTS("life_events", "Life Events", "Timeline", PlanadayRouteGroup.MIND_TIME, "C-0013", "Personal milestones and timeline memories."),
    SPACED_REVIEW("spaced_review", "Study & Review", "Review", PlanadayRouteGroup.MIND_TIME, "C-0029", "Spaced repetition flashcards linked to your notes."),
    CLOCK_WORLD("clock", "World Clock", "Clock", PlanadayRouteGroup.MIND_TIME, "C-0030", "Live clocks across time zones and customizable clock faces."),
    ALARMS("alarms", "Alarms", "Alarms", PlanadayRouteGroup.MIND_TIME, "C-0031", "Recurring alarms, snooze, and quiet hours."),
    TIMERS("timers", "Timers & Pomodoro", "Timers", PlanadayRouteGroup.MIND_TIME, "C-0032", "Multiple simultaneous timers, Pomodoro focus sessions, and intervals."),
    STOPWATCH("stopwatch", "Stopwatch & Ledger", "Ledger", PlanadayRouteGroup.MIND_TIME, "C-0033", "Live stopwatch, lap times, and persistent time tracking."),
    COUNTDOWNS("countdowns", "Countdowns", "Countdowns", PlanadayRouteGroup.MIND_TIME, "C-0034", "Live countdowns to upcoming dates and deadlines."),

    // Planner & Canvas
    PHYSICAL_PLANNER("physical_planner", "Digital Planner", "Planner", PlanadayRouteGroup.PLANNER_VISUAL, "C-0005", "Customizable notebooks, covers, spreads, stickers, ink, and linked records."),
    WHITEBOARD("whiteboard", "Whiteboard", "Board", PlanadayRouteGroup.PLANNER_VISUAL, "C-0019", "Visual canvas for sticky notes, shapes, connectors, drawing, and linked records."),

    // Kitchen & Life
    RECIPES("recipes", "Recipes", "Recipes", PlanadayRouteGroup.KITCHEN_LIFE, "C-0035", "Recipe collection with serving scaling and pantry shortage checks."),
    PANTRY("pantry", "Pantry", "Pantry", PlanadayRouteGroup.KITCHEN_LIFE, "C-0036", "Pantry inventory lots, storage zones, and expiration tracking."),
    MEAL_PLANNER("meals", "Meal Planner", "Meals", PlanadayRouteGroup.KITCHEN_LIFE, "C-0037", "Weekly meal plans and unified food history."),
    GROCERIES("groceries", "Groceries", "Groceries", PlanadayRouteGroup.KITCHEN_LIFE, "C-0038", "Aisle-sorted shopping lists that restock pantry and log expenses."),
    HOUSEHOLD("household", "Household", "Family", PlanadayRouteGroup.KITCHEN_LIFE, "C-0041", "Family profiles, shared items, and chore balance."),
    PEOPLE_GIFTS("people", "People & Gifts", "People", PlanadayRouteGroup.KITCHEN_LIFE, "C-0042", "Contacts, occasions, and gift planning."),
    HEALTH_MEDS("health", "Health & Meds", "Health", PlanadayRouteGroup.KITCHEN_LIFE, "C-0043", "Personal medication schedules, refills, and workouts."),
    FINANCE("finance", "Finance", "Finance", PlanadayRouteGroup.KITCHEN_LIFE, "C-0044", "Budgets, expenses, recurring bills, warranties, and wishlists."),
    MEDIA_HOBBIES("media", "Media & Hobbies", "Media", PlanadayRouteGroup.KITCHEN_LIFE, "C-0045", "Books, films, games, and hobby project tracking."),
    TRAVEL("travel", "Travel", "Travel", PlanadayRouteGroup.KITCHEN_LIFE, "C-0046", "Trips, offline travel packs, time zones, and packing checklists."),
    HOME_ASSETS("home_assets", "Home & Assets", "HomeOS", PlanadayRouteGroup.KITCHEN_LIFE, "C-0047", "Home maintenance, vehicles, pets, plants, and storage bins."),

    // Laboratory
    LAB_WORKSPACE("lab", "Laboratory", "Lab", PlanadayRouteGroup.LABORATORY, "C-0014", "Samples, chain of custody, inventory, pricing, calibration, Wet vs. NIR, and QMS."),

    // Math, Office & Files
    CALCULATOR("calculator", "Calculator & Math", "Calculator", PlanadayRouteGroup.OFFICE_MATH, "C-0015", "Scientific calculator, graphing, matrices, units, and reusable formulas."),
    SPREADSHEETS("spreadsheets", "Spreadsheets", "Sheets", PlanadayRouteGroup.OFFICE_MATH, "C-0016", "Multi-sheet workbooks, formulas, sorting, charts, and CSV/Excel interchange."),
    PRESENTATIONS("presentations", "Presentations", "Slides", PlanadayRouteGroup.OFFICE_MATH, "C-0017", "Visual slide designer, speaker notes, presenter mode, and PowerPoint/HTML export."),
    UNIVERSAL_FILES("files", "Files & Documents", "Files", PlanadayRouteGroup.OFFICE_MATH, "C-0025", "Folders, attachments, PDF annotations, and verified file storage."),
    PHOTO_STUDIO("photos", "Photo Studio", "Photos", PlanadayRouteGroup.OFFICE_MATH, "C-0024", "Photo library, albums, and non-destructive crop, rotate, and tone edits."),

    // Projects & Creative
    PROJECTS("projects", "Projects", "Projects", PlanadayRouteGroup.PROJECTS_CREATIVE, "C-0020", "Projects, milestones, Gantt timelines, critical paths, and custom databases."),
    KANBAN("kanban", "Boards", "Boards", PlanadayRouteGroup.PROJECTS_CREATIVE, "C-0018", "Kanban task boards and custom workflow columns."),
    GAME_DEV("game_dev", "Game Studio", "GameDev", PlanadayRouteGroup.PROJECTS_CREATIVE, "C-0021", "Game design docs, entities, items, skills, economy, and balance simulations."),
    BOOK_STUDIO("book_studio", "Book Studio", "Books", PlanadayRouteGroup.PROJECTS_CREATIVE, "C-0022", "Manuscripts, chapters, characters, worldbuilding, continuity, and eBook export."),
    CODE_STUDIO("code_studio", "Code Studio", "Code", PlanadayRouteGroup.PROJECTS_CREATIVE, "C-0023", "Multi-file code projects, syntax diagnostics, step debugger, and bundle export."),

    // Workspace & System
    HISTORY_REVIEWS("history", "History & Reviews", "History", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0011", "Activity timelines and weekly or monthly reflections."),
    REPORTS("reports", "Reports & Decisions", "Reports", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0012", "Pivot reports, snapshots, and decision journal."),
    INSIGHTS("insights", "Insights", "Insights", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0050", "Focus time calibration and completion trends."),
    CUSTOM_SPACES("spaces", "Custom Workspaces", "Spaces", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0048", "Custom modules, tabs, layouts, and templates."),
    RITUALS("rituals", "Routines & Contexts", "Contexts", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0049", "Scheduled contexts and focus modes."),
    AUTOMATIONS("automations", "Automations", "Automations", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0051", "No-code workflow rules with dry-run preview and execution history."),
    GUIDE("guide", "Help & Shortcuts", "Guide", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0052", "Offline guide, keyboard shortcuts, and module directory."),
    TRASH("trash", "Trash", "Trash", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0053", "Recover recently deleted items within 30 days."),
    SETTINGS("settings", "Settings", "Settings", PlanadayRouteGroup.CUSTOMIZATION_SYSTEM, "C-0054", "Appearance, workspaces, backups, optional sync, and diagnostics."),
}
