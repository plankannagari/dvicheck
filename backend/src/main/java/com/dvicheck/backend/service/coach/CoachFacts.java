package com.dvicheck.backend.service.coach;

/**
 * Pre-formatted, anonymous weekly spending facts handed to the AI coach.
 * Every amount/percent is already a display string (currency CODE + number, like the
 * Day 34 PDF), so the model never does arithmetic or formatting itself.
 *
 * Deliberately contains NO user id, phone number, receipt text or item names.
 * JSON field order = component order (Jackson serialises records in declaration order).
 */
public record CoachFacts(
        String weekLabel,              // "Oct 27 - Nov 2"
        String totalSpent,             // "USD 142.30"
        String prevWeekTotal,          // nullable — no spend last week
        String changePercent,          // nullable — only when prevWeekTotal > 0; "+12%" / "-8%"
        int receiptCount,
        Category topCategory,          // nullable — no categorised line items this week
        RisingCategory risingCategory, // nullable — nothing rose vs last week
        Store topStore,                // nullable — defensive; non-null whenever receipts exist
        String avoidableSpend,         // nullable — zero avoidable spend
        Budget budget,                 // nullable — no monthly budget set
        String currency                // "USD"
) {
    /** sharePercent = share of this week's categorised (line-item) spend, e.g. "34%". */
    public record Category(String name, String amount, String sharePercent) {}

    /** deltaPercent = change vs the same category last week, e.g. "+40%". */
    public record RisingCategory(String name, String deltaPercent) {}

    public record Store(String name, String amount) {}

    /** Current calendar month, from MonthlyReportService — not the coached week's month. */
    public record Budget(String monthlyBudget, String monthUsedPercent) {}
}
