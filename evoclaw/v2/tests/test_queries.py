#!/usr/bin/env python3 -u
"""
Comprehensive test suite for omenclawdemo2 LLM Task Graph Orchestrator.

Tests 100 diverse queries across 12 categories to validate:
  1. Plan is generated (tasks exist)
  2. All tasks reach a terminal state (completed/failed)
  3. Task results are non-empty and meaningful
  4. Specific expectations per category (file created, math correct, etc.)

Usage:
  # Run ALL 100 tests (warning: costs ~$2-5 in LLM API calls, takes ~30min)
  python test_queries.py --all

  # Run a specific category
  python test_queries.py --category weather

  # Run N random tests from each category
  python test_queries.py --sample 2

  # Run a single test by index
  python test_queries.py --index 0

  # Dry run (just list tests, don't execute)
  python test_queries.py --dry-run

  # Custom backend URL
  python test_queries.py --url http://localhost:8081 --sample 1
"""

import argparse
import json
import random
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Optional
from urllib.request import Request, urlopen
from urllib.error import URLError


# ── Configuration ────────────────────────────────────────────────────────────

BASE_URL = "http://localhost:8081"
POLL_INTERVAL = 3        # seconds between status checks
MAX_WAIT = 120           # max seconds to wait for a run to finish
REQUEST_DELAY = 2        # seconds between test runs to avoid rate limiting


# ── Data Models ──────────────────────────────────────────────────────────────

class Category(Enum):
    WEATHER = "weather"
    MATH = "math"
    RESEARCH = "research"
    FILE_OPS = "file_ops"
    CURRENT_EVENTS = "current_events"
    COMPARISON = "comparison"
    HOW_TO = "how_to"
    MULTI_STEP = "multi_step"
    CREATIVE = "creative"
    DATA_ANALYSIS = "data_analysis"
    TASK_BASED = "task_based"
    EDGE_CASES = "edge_cases"


class Verdict(Enum):
    PASS = "PASS"
    FAIL = "FAIL"
    WARN = "WARN"
    SKIP = "SKIP"


@dataclass
class TestCase:
    index: int
    category: Category
    query: str
    description: str
    expect_min_tasks: int = 2
    expect_tool_used: Optional[str] = None
    expect_file_created: bool = False
    expect_result_contains: Optional[str] = None
    expect_all_completed: bool = True


@dataclass
class TestResult:
    test: TestCase
    verdict: Verdict = Verdict.SKIP
    run_id: Optional[int] = None
    phase: Optional[str] = None
    task_count: int = 0
    completed_count: int = 0
    failed_count: int = 0
    duration_sec: float = 0
    errors: list = field(default_factory=list)
    notes: list = field(default_factory=list)


# ── Test Cases (100) ─────────────────────────────────────────────────────────

TEST_CASES = [
    # ── WEATHER (10) ─────────────────────────────────────────────────────────
    TestCase(0, Category.WEATHER,
             "What is the weather in New York today?",
             "Basic weather query for a known city",
             expect_tool_used="search", expect_result_contains="temperature"),
    TestCase(1, Category.WEATHER,
             "Check the weather in Tokyo and tell me if I need an umbrella",
             "Weather + reasoning",
             expect_tool_used="search"),
    TestCase(2, Category.WEATHER,
             "Compare the weather in London and Paris today",
             "Multi-city weather comparison",
             expect_min_tasks=2, expect_tool_used="search"),
    TestCase(3, Category.WEATHER,
             "What's the weather in Mumbai? Save it to a file called mumbai_weather.txt",
             "Weather + file save",
             expect_tool_used="search", expect_file_created=True),
    TestCase(4, Category.WEATHER,
             "Is it a good day to go for a run in Bangalore based on today's weather?",
             "Weather + lifestyle advice",
             expect_tool_used="search"),
    TestCase(5, Category.WEATHER,
             "What is the temperature in Delhi in Celsius and Fahrenheit?",
             "Weather with unit conversion",
             expect_tool_used="search"),
    TestCase(6, Category.WEATHER,
             "Check weather in Sydney and suggest what to wear",
             "Weather + clothing recommendation",
             expect_tool_used="search"),
    TestCase(7, Category.WEATHER,
             "How is the weather in Berlin? Is it good for a picnic?",
             "Weather + activity suggestion",
             expect_tool_used="search"),
    TestCase(8, Category.WEATHER,
             "Get the weather for San Francisco and calculate wind chill if temperature is below 15C",
             "Weather + conditional math",
             expect_min_tasks=2),
    TestCase(9, Category.WEATHER,
             "Check weather in 3 cities: Chennai, Hyderabad, Pune and summarize",
             "Multi-city weather batch",
             expect_min_tasks=3),

    # ── MATH (10) ────────────────────────────────────────────────────────────
    TestCase(10, Category.MATH,
             "What is 247 * 389?",
             "Basic multiplication",
             expect_tool_used="math"),
    TestCase(11, Category.MATH,
             "Calculate the compound interest on $10000 at 5% for 3 years",
             "Compound interest calculation",
             expect_tool_used="math"),
    TestCase(12, Category.MATH,
             "What is the square root of 144 plus the cube root of 27?",
             "Multi-operation math",
             expect_tool_used="math"),
    TestCase(13, Category.MATH,
             "If I have 500 apples and give away 37% of them, how many do I have left?",
             "Percentage calculation",
             expect_tool_used="math"),
    TestCase(14, Category.MATH,
             "Calculate fibonacci number at position 10",
             "Algorithmic math question"),
    TestCase(15, Category.MATH,
             "What is 2^20?",
             "Exponentiation",
             expect_tool_used="math"),
    TestCase(16, Category.MATH,
             "Convert 100 kilometers to miles and then to nautical miles",
             "Unit conversion chain",
             expect_tool_used="math"),
    TestCase(17, Category.MATH,
             "Calculate the area of a circle with radius 7.5 meters",
             "Geometry formula",
             expect_tool_used="math"),
    TestCase(18, Category.MATH,
             "If a train travels at 120 km/h for 2.5 hours, how far does it go?",
             "Distance = speed * time",
             expect_tool_used="math"),
    TestCase(19, Category.MATH,
             "What is the sum of all numbers from 1 to 100?",
             "Series sum",
             expect_tool_used="math"),

    # ── RESEARCH (10) ────────────────────────────────────────────────────────
    TestCase(20, Category.RESEARCH,
             "What is quantum computing and how does it differ from classical computing?",
             "Tech explanation",
             expect_tool_used="search"),
    TestCase(21, Category.RESEARCH,
             "Tell me about the history of the Internet",
             "Historical research"),
    TestCase(22, Category.RESEARCH,
             "What are the top 5 programming languages in 2024?",
             "Ranked list research",
             expect_tool_used="search"),
    TestCase(23, Category.RESEARCH,
             "Explain blockchain technology in simple terms",
             "Simplified explanation"),
    TestCase(24, Category.RESEARCH,
             "What is machine learning and what are its main types?",
             "ML overview"),
    TestCase(25, Category.RESEARCH,
             "Research the benefits of meditation and summarize in 5 points",
             "Health research with formatting"),
    TestCase(26, Category.RESEARCH,
             "What is the GDP of India and how has it changed in the last 5 years?",
             "Economic data query",
             expect_tool_used="search"),
    TestCase(27, Category.RESEARCH,
             "Tell me about the Mars rover missions by NASA",
             "Space exploration research"),
    TestCase(28, Category.RESEARCH,
             "What are microservices and when should you use them?",
             "Architecture research"),
    TestCase(29, Category.RESEARCH,
             "Research electric vehicles: pros, cons, and market trends",
             "Multi-faceted research",
             expect_min_tasks=3),

    # ── FILE OPERATIONS (8) ──────────────────────────────────────────────────
    TestCase(30, Category.FILE_OPS,
             "Create a file called hello.txt with the text 'Hello World'",
             "Basic file creation",
             expect_min_tasks=1, expect_file_created=True, expect_tool_used="file"),
    TestCase(31, Category.FILE_OPS,
             "Write a short poem about coding and save it to poem.txt",
             "Creative file writing",
             expect_file_created=True),
    TestCase(32, Category.FILE_OPS,
             "Create a file called shopping_list.txt with 10 grocery items",
             "List generation + file save",
             expect_file_created=True),
    TestCase(33, Category.FILE_OPS,
             "Search for information about Python programming and save a summary to python_info.txt",
             "Search + file save pipeline",
             expect_min_tasks=2, expect_file_created=True),
    TestCase(34, Category.FILE_OPS,
             "Create a CSV file called data.csv with 5 rows of name, age, city",
             "Structured file creation",
             expect_file_created=True),
    TestCase(35, Category.FILE_OPS,
             "Write a README.md file for a calculator project",
             "Documentation generation",
             expect_file_created=True),
    TestCase(36, Category.FILE_OPS,
             "Create a JSON file called config.json with database connection settings",
             "JSON file creation",
             expect_file_created=True),
    TestCase(37, Category.FILE_OPS,
             "Write a haiku about nature and save it to haiku.txt",
             "Creative + file save",
             expect_file_created=True),

    # ── CURRENT EVENTS (8) ───────────────────────────────────────────────────
    TestCase(38, Category.CURRENT_EVENTS,
             "What is happening in the world today?",
             "Broad current events"),
    TestCase(39, Category.CURRENT_EVENTS,
             "Give me the latest news about artificial intelligence",
             "Tech news",
             expect_tool_used="search"),
    TestCase(40, Category.CURRENT_EVENTS,
             "What is the current status of the Russia Ukraine conflict?",
             "Conflict status",
             expect_tool_used="search"),
    TestCase(41, Category.CURRENT_EVENTS,
             "Check the status of Iran and USA relations in the last 15 days day by day",
             "Day-by-day conflict timeline",
             expect_tool_used="search", expect_result_contains="Mar"),
    TestCase(42, Category.CURRENT_EVENTS,
             "What are the trending topics in technology this week?",
             "Tech trends"),
    TestCase(43, Category.CURRENT_EVENTS,
             "Give me a daily report of stock market movements for the last 7 days",
             "Financial timeline",
             expect_tool_used="search"),
    TestCase(44, Category.CURRENT_EVENTS,
             "What happened in sports news today?",
             "Sports news"),
    TestCase(45, Category.CURRENT_EVENTS,
             "What is the latest update on climate change policies?",
             "Climate news"),

    # ── COMPARISON (8) ───────────────────────────────────────────────────────
    TestCase(46, Category.COMPARISON,
             "Compare Python vs Java for backend development",
             "Programming language comparison",
             expect_tool_used="search"),
    TestCase(47, Category.COMPARISON,
             "What is the difference between SQL and NoSQL databases?",
             "Database comparison"),
    TestCase(48, Category.COMPARISON,
             "Compare React, Angular, and Vue.js",
             "Triple framework comparison",
             expect_min_tasks=2),
    TestCase(49, Category.COMPARISON,
             "iPhone vs Samsung: which is better for photography?",
             "Product comparison"),
    TestCase(50, Category.COMPARISON,
             "Compare AWS, Azure, and GCP cloud platforms",
             "Cloud provider comparison",
             expect_min_tasks=2),
    TestCase(51, Category.COMPARISON,
             "What is the difference between REST and GraphQL?",
             "API style comparison"),
    TestCase(52, Category.COMPARISON,
             "Compare Docker vs Kubernetes: when to use each?",
             "DevOps tool comparison"),
    TestCase(53, Category.COMPARISON,
             "Monolith vs Microservices architecture comparison",
             "Architecture comparison"),

    # ── HOW-TO / TUTORIALS (8) ───────────────────────────────────────────────
    TestCase(54, Category.HOW_TO,
             "How to set up a Spring Boot project from scratch?",
             "Framework setup guide"),
    TestCase(55, Category.HOW_TO,
             "How to make a REST API in Python using Flask?",
             "API development guide"),
    TestCase(56, Category.HOW_TO,
             "How to deploy a Docker container to production?",
             "Deployment guide"),
    TestCase(57, Category.HOW_TO,
             "How to implement authentication with JWT tokens?",
             "Security implementation guide"),
    TestCase(58, Category.HOW_TO,
             "How to set up CI/CD pipeline with GitHub Actions?",
             "CI/CD guide"),
    TestCase(59, Category.HOW_TO,
             "How to optimize SQL queries for better performance?",
             "Database optimization guide"),
    TestCase(60, Category.HOW_TO,
             "How to implement a binary search tree in Java?",
             "Data structure guide"),
    TestCase(61, Category.HOW_TO,
             "Tutorial: Building a chat application with WebSockets",
             "WebSocket tutorial"),

    # ── MULTI-STEP COMPLEX (10) ──────────────────────────────────────────────
    TestCase(62, Category.MULTI_STEP,
             "Research the top 3 JavaScript frameworks, compare them, and save the comparison to a file",
             "Research + compare + file",
             expect_min_tasks=3, expect_file_created=True),
    TestCase(63, Category.MULTI_STEP,
             "Calculate my BMI if I weigh 75kg and am 1.78m tall, then search for health recommendations",
             "Math + search pipeline",
             expect_min_tasks=2),
    TestCase(64, Category.MULTI_STEP,
             "Check the weather in 3 Indian cities, find the hottest one, and write a travel advisory",
             "Weather + analysis + writing",
             expect_min_tasks=3),
    TestCase(65, Category.MULTI_STEP,
             "Research machine learning, summarize it, and save the summary to ml_notes.txt",
             "Research + summarize + save",
             expect_min_tasks=2, expect_file_created=True),
    TestCase(66, Category.MULTI_STEP,
             "Calculate the monthly payment for a $300,000 mortgage at 6% interest for 30 years, then search for current mortgage rates to compare",
             "Math + research comparison",
             expect_min_tasks=2),
    TestCase(67, Category.MULTI_STEP,
             "Search for the latest AI news, pick the top 3 stories, and create a newsletter draft saved to newsletter.txt",
             "News + curation + file",
             expect_min_tasks=3, expect_file_created=True),
    TestCase(68, Category.MULTI_STEP,
             "What is the population of the top 5 countries? Calculate their combined population and save to a report",
             "Research + math + file",
             expect_min_tasks=3),
    TestCase(69, Category.MULTI_STEP,
             "Look up 3 healthy breakfast recipes, rate them by preparation time, and save the quickest one to recipe.txt",
             "Research + analysis + file",
             expect_min_tasks=3, expect_file_created=True),
    TestCase(70, Category.MULTI_STEP,
             "Search for tips on time management, organize them by priority, and create an action plan document",
             "Research + organize + save",
             expect_min_tasks=2),
    TestCase(71, Category.MULTI_STEP,
             "Check Bitcoin price, calculate what 0.5 BTC is worth, and save the report to crypto_report.txt",
             "Price + math + file",
             expect_min_tasks=3),

    # ── CREATIVE (8) ─────────────────────────────────────────────────────────
    TestCase(72, Category.CREATIVE,
             "Write a short story about a robot learning to paint",
             "Short story generation"),
    TestCase(73, Category.CREATIVE,
             "Create a limerick about debugging code",
             "Poetry generation"),
    TestCase(74, Category.CREATIVE,
             "Write 5 motivational quotes about perseverance",
             "Quote generation"),
    TestCase(75, Category.CREATIVE,
             "Create a fictional product description for a time-traveling watch",
             "Product copy generation"),
    TestCase(76, Category.CREATIVE,
             "Write a professional email declining a meeting politely",
             "Business writing"),
    TestCase(77, Category.CREATIVE,
             "Create a job description for an AI Engineer position",
             "HR document generation"),
    TestCase(78, Category.CREATIVE,
             "Write a 30-second elevator pitch for a food delivery startup",
             "Business pitch"),
    TestCase(79, Category.CREATIVE,
             "Create a birthday message for a colleague who loves hiking",
             "Personal writing"),

    # ── DATA / ANALYSIS (8) ──────────────────────────────────────────────────
    TestCase(80, Category.DATA_ANALYSIS,
             "Generate a sample dataset of 10 employees with name, department, salary and save as CSV",
             "Data generation + CSV",
             expect_file_created=True),
    TestCase(81, Category.DATA_ANALYSIS,
             "Calculate the average of these numbers: 85, 92, 78, 96, 88, 73, 91",
             "Statistical calculation",
             expect_tool_used="math"),
    TestCase(82, Category.DATA_ANALYSIS,
             "Create a project plan with 5 milestones, dates, and owners for a website redesign",
             "Project planning"),
    TestCase(83, Category.DATA_ANALYSIS,
             "Generate a SWOT analysis for a small coffee shop business",
             "Business analysis"),
    TestCase(84, Category.DATA_ANALYSIS,
             "Calculate the ROI if I invest $50,000 and get back $65,000 after 2 years",
             "Financial analysis",
             expect_tool_used="math"),
    TestCase(85, Category.DATA_ANALYSIS,
             "Create a weekly meal plan for a vegetarian diet and save it to meal_plan.txt",
             "Planning + file save",
             expect_file_created=True),
    TestCase(86, Category.DATA_ANALYSIS,
             "List the top 10 most spoken languages in the world with approximate speaker counts",
             "Data compilation"),
    TestCase(87, Category.DATA_ANALYSIS,
             "Create a comparison matrix of 4 project management tools: Jira, Trello, Asana, Monday",
             "Tool comparison matrix"),

    # ── TASK-BASED / ACTIONABLE (8) ──────────────────────────────────────────
    TestCase(88, Category.TASK_BASED,
             "Create a to-do list for launching a new mobile app and save it to launch_checklist.txt",
             "Checklist generation",
             expect_file_created=True),
    TestCase(89, Category.TASK_BASED,
             "Draft an agenda for a 1-hour team meeting about Q1 goals",
             "Meeting agenda creation"),
    TestCase(90, Category.TASK_BASED,
             "Create interview questions for a senior backend developer position and save to interview_prep.txt",
             "Interview prep + save",
             expect_file_created=True),
    TestCase(91, Category.TASK_BASED,
             "Write a bug report template and save it to bug_template.md",
             "Template creation + save",
             expect_file_created=True),
    TestCase(92, Category.TASK_BASED,
             "Create a packing list for a 5-day business trip to New York",
             "Travel planning"),
    TestCase(93, Category.TASK_BASED,
             "Write a code review checklist with 15 items and save to code_review.txt",
             "Dev process + save",
             expect_file_created=True),
    TestCase(94, Category.TASK_BASED,
             "Create a troubleshooting guide for common Docker issues",
             "Technical documentation"),
    TestCase(95, Category.TASK_BASED,
             "Plan a team building event for 20 people with budget $500 and save the plan",
             "Event planning + save",
             expect_file_created=True),

    # ── EDGE CASES (5) ───────────────────────────────────────────────────────
    TestCase(96, Category.EDGE_CASES,
             "Hello, tell me something interesting about space",
             "Minimal query with an actual question",
             expect_min_tasks=1, expect_all_completed=True),
    TestCase(97, Category.EDGE_CASES,
             "What is 1+1?",
             "Trivial math question",
             expect_min_tasks=1),
    TestCase(98, Category.EDGE_CASES,
             "Search for absolutely nothing relevant and then search again for something else entirely unrelated and then calculate 2+2 and save everything to a file",
             "Vague multi-step with conflicting instructions",
             expect_min_tasks=3),
    TestCase(99, Category.EDGE_CASES,
             "Tell me a joke, then explain why it's funny, then rate it out of 10, then search for a better joke, and save the best one to joke.txt",
             "Long chain of dependent creative tasks",
             expect_min_tasks=4, expect_file_created=True),
]


# ── HTTP Helpers ─────────────────────────────────────────────────────────────

def api_post(path: str, body: dict) -> dict:
    req = Request(
        f"{BASE_URL}{path}",
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"},
        method="POST"
    )
    try:
        with urlopen(req, timeout=30) as resp:
            return json.loads(resp.read())
    except URLError as e:
        if hasattr(e, 'read'):
            return json.loads(e.read())
        raise


def api_get(path: str) -> dict:
    req = Request(f"{BASE_URL}{path}", method="GET")
    try:
        with urlopen(req, timeout=30) as resp:
            return json.loads(resp.read())
    except URLError as e:
        if hasattr(e, 'read'):
            return json.loads(e.read())
        raise


# ── Test Runner ──────────────────────────────────────────────────────────────

def run_test(tc: TestCase) -> TestResult:
    result = TestResult(test=tc)
    start = time.time()

    try:
        # 1. Submit query
        resp = api_post("/api/query", {"message": tc.query})
        result.run_id = resp.get("runId")
        if not result.run_id:
            result.verdict = Verdict.FAIL
            result.errors.append("No runId returned from /api/query")
            return result

        # 2. Poll for completion
        elapsed = 0
        while elapsed < MAX_WAIT:
            time.sleep(POLL_INTERVAL)
            elapsed = time.time() - start
            try:
                detail = api_get(f"/api/runs/{result.run_id}")
            except Exception:
                continue

            result.phase = detail.get("phase", "")
            if result.phase in ("succeeded", "failed"):
                break

        if result.phase not in ("succeeded", "failed"):
            result.verdict = Verdict.FAIL
            result.errors.append(f"Timed out after {MAX_WAIT}s — phase is still '{result.phase}'")
            result.duration_sec = time.time() - start
            return result

        # 3. Fetch final details
        detail = api_get(f"/api/runs/{result.run_id}")
        tasks = detail.get("tasks", [])
        result.task_count = len(tasks)
        result.completed_count = sum(1 for t in tasks if t.get("status") == "completed")
        result.failed_count = sum(1 for t in tasks if t.get("status") == "failed")

        # 4. Validate
        errors = []

        # Check task count
        if result.task_count < tc.expect_min_tasks:
            errors.append(f"Expected at least {tc.expect_min_tasks} tasks, got {result.task_count}")

        # Check all completed
        if tc.expect_all_completed and result.failed_count > 0:
            failed_names = [t.get("title", "?") for t in tasks if t.get("status") == "failed"]
            errors.append(f"{result.failed_count} task(s) failed: {failed_names}")

        # Check results are non-empty
        empty_results = []
        for t in tasks:
            rt = t.get("resultText", "")
            if t.get("status") == "completed" and (
                not rt or rt in ("(no result)", "(no result provided)", "(empty result)")
            ):
                empty_results.append(t.get("title", "?"))
        if empty_results:
            errors.append(f"Empty results for completed tasks: {empty_results}")

        # Check expected tool usage
        if tc.expect_tool_used:
            all_steps = []
            for t in tasks:
                for s in t.get("steps", []):
                    if s.get("kind") == "tool_call":
                        all_steps.append(s.get("toolName", ""))
            if tc.expect_tool_used not in all_steps:
                result.notes.append(f"Expected tool '{tc.expect_tool_used}' not found in steps (found: {set(all_steps) or 'none'})")

        # Check file creation
        if tc.expect_file_created:
            file_steps = [s for t in tasks for s in t.get("steps", [])
                          if s.get("toolName") == "file"]
            if not file_steps:
                file_mentions = any("file" in (t.get("resultText") or "").lower() or
                                    "save" in (t.get("resultText") or "").lower() or
                                    "wrote" in (t.get("resultText") or "").lower() or
                                    ".txt" in (t.get("resultText") or "").lower() or
                                    ".csv" in (t.get("resultText") or "").lower() or
                                    ".md" in (t.get("resultText") or "").lower() or
                                    ".json" in (t.get("resultText") or "").lower()
                                    for t in tasks)
                if not file_mentions:
                    result.notes.append("Expected file creation but no file tool usage or file mention found")

        # Check result contains expected text
        if tc.expect_result_contains:
            all_results = " ".join(t.get("resultText", "") or "" for t in tasks).lower()
            if tc.expect_result_contains.lower() not in all_results:
                result.notes.append(f"Expected result to contain '{tc.expect_result_contains}'")

        result.errors = errors
        if errors:
            result.verdict = Verdict.FAIL
        elif result.notes:
            result.verdict = Verdict.WARN
        else:
            result.verdict = Verdict.PASS

    except URLError as e:
        result.verdict = Verdict.FAIL
        result.errors.append(f"Connection error: {e}")
    except Exception as e:
        result.verdict = Verdict.FAIL
        result.errors.append(f"Unexpected error: {e}")

    result.duration_sec = time.time() - start
    return result


# ── Reporting ────────────────────────────────────────────────────────────────

def print_result(r: TestResult):
    icon = {"PASS": "\033[92m✓\033[0m", "FAIL": "\033[91m✗\033[0m",
            "WARN": "\033[93m⚠\033[0m", "SKIP": "\033[90m○\033[0m"}
    v = icon.get(r.verdict.value, "?")
    cat = r.test.category.value.ljust(16)
    dur = f"{r.duration_sec:.1f}s".rjust(7)
    tasks_info = f"tasks={r.task_count} done={r.completed_count} fail={r.failed_count}"

    print(f"  {v} [{r.test.index:3d}] {cat} {dur}  {tasks_info}")
    print(f"         Query: {r.test.query[:80]}{'...' if len(r.test.query) > 80 else ''}")
    if r.errors:
        for e in r.errors:
            print(f"         \033[91mERROR: {e}\033[0m")
    if r.notes:
        for n in r.notes:
            print(f"         \033[93mNOTE:  {n}\033[0m")
    print()


def print_summary(results: list[TestResult]):
    total = len(results)
    passed = sum(1 for r in results if r.verdict == Verdict.PASS)
    failed = sum(1 for r in results if r.verdict == Verdict.FAIL)
    warned = sum(1 for r in results if r.verdict == Verdict.WARN)
    skipped = sum(1 for r in results if r.verdict == Verdict.SKIP)
    total_dur = sum(r.duration_sec for r in results)

    print("=" * 78)
    print(f"  RESULTS:  {passed} passed  |  {failed} failed  |  {warned} warnings  |  {skipped} skipped")
    print(f"  TOTAL:    {total} tests in {total_dur:.0f}s ({total_dur/60:.1f}min)")
    print(f"  RATE:     {passed/total*100:.0f}% pass rate" if total > 0 else "")
    print("=" * 78)

    # Per-category breakdown
    print("\n  Per-category breakdown:")
    cats = {}
    for r in results:
        c = r.test.category.value
        if c not in cats:
            cats[c] = {"pass": 0, "fail": 0, "warn": 0, "total": 0}
        cats[c]["total"] += 1
        if r.verdict == Verdict.PASS:
            cats[c]["pass"] += 1
        elif r.verdict == Verdict.FAIL:
            cats[c]["fail"] += 1
        elif r.verdict == Verdict.WARN:
            cats[c]["warn"] += 1

    for cat, counts in sorted(cats.items()):
        rate = counts["pass"] / counts["total"] * 100 if counts["total"] > 0 else 0
        bar = "█" * int(rate / 10) + "░" * (10 - int(rate / 10))
        print(f"    {cat.ljust(18)} {bar} {rate:5.0f}%  ({counts['pass']}/{counts['total']})")

    # Save report
    report_path = "test_report.json"
    report = {
        "timestamp": datetime.now().isoformat(),
        "summary": {"total": total, "passed": passed, "failed": failed, "warned": warned, "duration_sec": total_dur},
        "results": [
            {
                "index": r.test.index,
                "category": r.test.category.value,
                "query": r.test.query,
                "verdict": r.verdict.value,
                "run_id": r.run_id,
                "phase": r.phase,
                "task_count": r.task_count,
                "completed": r.completed_count,
                "failed": r.failed_count,
                "duration_sec": round(r.duration_sec, 1),
                "errors": r.errors,
                "notes": r.notes,
            }
            for r in results
        ]
    }
    with open(report_path, "w") as f:
        json.dump(report, f, indent=2)
    print(f"\n  Report saved to: {report_path}")


# ── Main ─────────────────────────────────────────────────────────────────────

def _update_globals(url, delay):
    global BASE_URL, REQUEST_DELAY
    BASE_URL = url
    REQUEST_DELAY = delay


def main():
    parser = argparse.ArgumentParser(description="Test omenclawdemo2 with diverse queries")
    parser.add_argument("--url", default=BASE_URL, help="Backend base URL")
    parser.add_argument("--all", action="store_true", help="Run all 100 tests")
    parser.add_argument("--category", type=str, help="Run tests for a specific category")
    parser.add_argument("--sample", type=int, help="Run N random tests from each category")
    parser.add_argument("--index", type=int, help="Run a single test by index")
    parser.add_argument("--range", type=str, help="Run tests in a range, e.g. 0-9")
    parser.add_argument("--dry-run", action="store_true", help="List tests without running")
    parser.add_argument("--delay", type=float, default=REQUEST_DELAY, help="Delay between tests (seconds)")
    args = parser.parse_args()

    _update_globals(args.url, args.delay)

    # Select tests
    tests = TEST_CASES[:]

    if args.index is not None:
        tests = [t for t in tests if t.index == args.index]
    elif args.range:
        start, end = map(int, args.range.split("-"))
        tests = [t for t in tests if start <= t.index <= end]
    elif args.category:
        cat = args.category.lower()
        tests = [t for t in tests if t.category.value == cat]
    elif args.sample:
        by_cat = {}
        for t in tests:
            by_cat.setdefault(t.category, []).append(t)
        sampled = []
        for cat_tests in by_cat.values():
            sampled.extend(random.sample(cat_tests, min(args.sample, len(cat_tests))))
        tests = sorted(sampled, key=lambda t: t.index)
    elif not args.all:
        # Default: run 1 from each category
        by_cat = {}
        for t in TEST_CASES:
            by_cat.setdefault(t.category, []).append(t)
        tests = [cat_tests[0] for cat_tests in by_cat.values()]

    if not tests:
        print("No tests selected. Use --all, --category, --sample, --index, or --range.")
        sys.exit(1)

    # Dry run
    if args.dry_run:
        print(f"\n  {len(tests)} tests selected (dry run — not executing):\n")
        for t in tests:
            print(f"  [{t.index:3d}] {t.category.value.ljust(18)} {t.query[:70]}{'...' if len(t.query) > 70 else ''}")
        print()
        sys.exit(0)

    # Check connectivity by requesting the main page
    try:
        req = Request(f"{BASE_URL}/", method="GET")
        urlopen(req, timeout=5)
    except URLError as e:
        if hasattr(e, 'code'):
            pass  # HTTP error means server is reachable
        else:
            print(f"\n  \033[91mERROR: Cannot connect to {BASE_URL}. Is the backend running?\033[0m\n")
            sys.exit(1)
    except Exception:
        pass

    # Run tests
    print(f"\n{'=' * 78}")
    print(f"  omenclawdemo2 Test Suite — {len(tests)} tests")
    print(f"  Backend: {BASE_URL}")
    print(f"  Started: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"{'=' * 78}\n")

    results = []
    for i, tc in enumerate(tests):
        print(f"  Running test {i+1}/{len(tests)} [#{tc.index}] {tc.category.value}: {tc.query[:60]}...", flush=True)
        r = run_test(tc)
        results.append(r)
        print_result(r)
        sys.stdout.flush()
        if i < len(tests) - 1:
            time.sleep(REQUEST_DELAY)

    print_summary(results)


if __name__ == "__main__":
    main()
