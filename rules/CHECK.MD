This is a pre-PR check protocol.
Trigger: "Run CHECK.MD protocol"

---

## Step 1. Get diff

Get the diff between remote `dev` and the current branch:

```
gh diff dev...HEAD
```

Save the result — it will be used in the following steps.

---

## Step 2. Overview

Using the diff, generate a change overview following the rules in `@rules/OVERVIEW.MD`.
Display the overview to the user.

---

## Step 3. Code Review

Using the same diff, perform a code review following the rules in `@rules/REVIEW.MD`.
Display the review results to the user.

---

## Step 4. Fix issues

If the review found any problems:

1. Fix all issues in the code.
2. Ask the user to confirm before continuing.
3. Go back to **Step 3** — re-run the review on the updated diff.

Repeat the Step 3 → Step 4 cycle until the review passes with no issues.

---

## Step 5. Done

When the review passes clean, inform the user:

> CHECK protocol passed. Code is ready for PR.
