# Fees Calculation Logic — TechnoKraft TTS-SMS

> **Version:** v6.12  
> **Last Updated:** 2026-08-22  
> **Author:** Development Team

---

## Overview

The fees calculation system handles two types of students:

| Type | Registration Number Format | Description |
|:---|:---|:---|
| **New Students** | Starts with `REG` (e.g. `REG2024001`) | Admitted via the new CRM system |
| **Old Students** | Plain number (e.g. `849`, `5091`) | Imported from legacy CSV / old database |

---

## Core Tables Involved

| Table | Purpose |
|:---|:---|
| `fees` | Master fees ledger — stores `total_fees`, `total_paid`, `fees_due`, `status` per student |
| `fee_receipts` | Individual payment receipts created by staff |
| `fee_refunds` | Refund records for a student |
| `admissions` | Admission record — source of `total_payable_fees` |
| `fee_installments` | Installment schedule per student |
| `fee_collections` | **Legacy imported data — VIEW ONLY. Never used in calculation.** |

---

## Key Columns in `fee_receipts`

| Column | Description |
|:---|:---|
| `id` | Auto-increment primary key — used to identify the chronologically earliest receipt |
| `registration_number` | Links to `fees` and `admissions` |
| `amount_received` | The actual amount paid in this receipt |
| `previous_paid` | Snapshot of `fees.total_paid` **at the time this receipt was created** (UI-populated) |
| `pending_fees` | Snapshot of `fees.fees_due` **after** this payment |
| `receipt_date` | Date of payment (can be same for multiple receipts) |
| `is_deleted` | Soft delete flag |

---

## The Core Formula (Single Source of Truth)

All fees totals are computed **solely** by `recalculateFeesFromTransactions(regNo)` in `FeesManagerService.java`.

```
Total Paid  =  Opening Balance  +  SUM(fee_receipts.amount_received)  -  SUM(fee_refunds.refund_amount)
Fees Due    =  Total Fees  -  Total Paid   (minimum 0)
```

> ⚠️ **No frontend JS function may write to `fees.total_paid` directly** — the backend is the only authority.

---

## Opening Balance — Detailed Explanation

### What is the Opening Balance?

For **old students** (non-REG), the original fees already paid before the CRM was introduced are stored as the initial `fees.total_paid` at import time (e.g. a student imported with ₹19,000 already paid).

This value is called the **Opening Balance**.

### How Opening Balance Is Derived — `getOpeningBalance()`

```java
private Double getOpeningBalance(List<FeeReceipt> receipts) {
    return receipts.stream()
        .filter(r -> r.getId() != null)
        .min(Comparator.comparing(FeeReceipt::getId))   // earliest receipt by DB ID
        .map(r -> r.getPreviousPaid() != null ? r.getPreviousPaid() : 0.0)
        .orElse(0.0);
}
```

**Key Rules:**
- We use the **earliest receipt by `id`** (NOT by `receipt_date` — same-day receipts make date unreliable).
- The `previous_paid` of the **first-ever receipt** = `fees.total_paid` at the moment the very first new receipt was created by staff (populated by the UI form).
- For **new students** (REG): `previous_paid` = `0` (no prior import balance).
- For **old students**: `previous_paid` = their original imported balance (e.g. ₹19,000).
- **All subsequent receipts' `previous_paid` values are ignored** — they are running totals only.

### Worked Example — Reg. No. 5091 (Old Student)

**Initial State (Imported):**
- Total Fees: ₹19,000
- Total Paid: ₹19,000 (already paid before CRM)
- Fees Due: ₹0

**New Receipt Created:**

| Receipt ID | Amount | previous_paid (snapshot at form open time) |
|:---:|:---:|:---:|
| 989 | ₹1,000 | ₹19,000 ← **Opening Balance** |

**Calculation:**
```
Opening Balance          = previous_paid of receipt ID 989 = ₹19,000
Sum of all receipts      = ₹1,000
Total Paid               = ₹19,000 + ₹1,000 = ₹20,000   ✅
Fees Due                 = max(0, ₹19,000 - ₹20,000) = ₹0 ✅ (₹1,000 extra paid)
Status                   = Clear                           ✅
```

---

## Execution Flow — Creating a New Receipt

```
[User clicks Save Receipt in UI]
         │
         ▼
[POST /api/fees-manager/receipts]
         │
         ▼
[FeesManagerService.createFeeReceipt()]
         │
         ├─1─► Save receipt to fee_receipts table
         │     (stored with amount_received and previous_paid)
         │
         ├─2─► Update installment status (if installment_id present)
         │
         ├─3─► updateFeesTableAfterReceipt(regNo)
         │     └─► ONLY ensures fees record exists in fees table
         │         Creates it from admissions if missing (for old students)
         │         Does NOT do any calculation ← intentional
         │
         └─4─► recalculateFeesFromTransactions(regNo)   ← SINGLE SOURCE OF TRUTH
               ├─► Fetch all fee_receipts for this regNo (not deleted)
               ├─► Fetch all fee_refunds for this regNo (not deleted)
               ├─► Sum all amountReceived from receipts
               ├─► getOpeningBalance() → first receipt's previousPaid (by min id)
               ├─► Total Paid = openingBalance + SUM(receipts) - SUM(refunds)
               ├─► Fees Due = Total Fees - Total Paid (min 0)
               ├─► Determine status: Clear / Pending / Overdue
               └─► Save fees record ← correct value written to DB

[Frontend JS]
         └─► loadFeesFromBackend() to refresh the table view
         ← (updateFeesTotalPaid() has been REMOVED — see Bug 3 below)
```

---

## `updateFeesTableAfterReceipt` — What It Does

This method **only** ensures a `fees` row exists in the database:

```java
// If fees record does NOT exist for this student → create it from admission data
// If fees record ALREADY exists → do nothing
// All calculation is done by recalculateFeesFromTransactions()
```

It does **no math** at all. This prevents double-calculation.

---

## Old Students vs New Students — Opening Balance Rules

| Condition | Opening Balance |
|:---|:---|
| Old student (e.g. `5091`) — first receipt's `previous_paid` = ₹19,000 | **₹19,000** |
| New student (e.g. `REG2024001`) — first receipt's `previous_paid` = ₹0 | **₹0** |
| No receipts yet (no new payments made) | `fees.total_paid` preserved as-is from import |

---

## `fee_collections` — Strictly View-Only

Records in `fee_collections` (legacy imported table) for old students:

- ✅ **Shown** in the "View Receipts" modal for historical reference
- ✅ Matched by **primary mobile number + student name** (case-insensitive)
- ❌ Do **NOT** update `fees.total_paid` or `fees.fees_due`
- ❌ Do **NOT** appear in `fee_receipts`
- ❌ Have **zero impact** on any fees calculation

---

## Refund Handling

```
Net Total Paid = Opening Balance + SUM(receipts.amount_received) - SUM(refunds.refund_amount)
```

Net Total Paid is never allowed to go below ₹0.

---

## Status Logic

| Condition | Status |
|:---|:---|
| `fees_due <= 0.01` | `Clear` |
| `fees_due > 0.01` AND no overdue installment | `Pending` |
| `fees_due > 0.01` AND installment `due_date < today` AND `status != Paid` | `Overdue` |
| `total_refund > 0` AND `fees_due > 0.01` | `Refund` |

---

## Key Methods in FeesManagerService.java

| Method | Purpose |
|:---|:---|
| `createFeeReceipt()` | Saves new receipt, calls `updateFeesTableAfterReceipt` then `recalculateFeesFromTransactions` |
| `updateFeesTableAfterReceipt()` | **Only** ensures a `fees` row exists. No calculation at all. |
| `recalculateFeesFromTransactions()` | **Single source of truth.** Computes and saves `total_paid`, `fees_due`, status |
| `getOpeningBalance()` | Returns `previous_paid` of the receipt with the smallest `id` (earliest by DB ID) |
| `deleteFeeReceipt()` | Soft-deletes a receipt, then calls `recalculateFeesFromTransactions` to recompute |
| `getReceiptsByRegNo()` | Returns all `fee_receipts` + matching `fee_collections` for VIEW only — no calc impact |
| `updateInstallmentStatus(id, amount, user)` | Marks installment as Paid (used in createFeeReceipt) |
| `updateInstallmentStatus(id, amount)` | Overload used in other update paths |
| `updateInstallmentStatus(id, status)` | Overload for status-only updates |

---

## Bug History & Root Causes

### Bug 1: `₹17,000` shown instead of correct total (Reg. 849)

**Root Cause:** Running server loaded OLD bytecode compiled before our fix. Old code used the **latest receipt's** `previous_paid` (a running total) as opening balance, then added all receipts again.

**Fix:** `getOpeningBalance()` now uses `MIN(id)` — always picks the **first receipt's** `previous_paid`.

### Bug 2: Double calculation inflating values

**Root Cause:** `updateFeesTableAfterReceipt` AND `recalculateFeesFromTransactions` both independently wrote to `fees.total_paid`, running two separate (sometimes conflicting) calculations.

**Fix:** `updateFeesTableAfterReceipt` now does **zero calculation** — it only creates the fees record if missing.

### Bug 3: `₹47,000` shown for Reg. 5091 (correct should be ₹20,000)  ← **MOST RECENT BUG**

**Root Cause:** A frontend JS function `updateFeesTotalPaid(regNo)` was called after every receipt save. It:
1. Called `GET /api/fees-manager/receipts/{regNo}` which returns **new receipts + old fee_collections combined**
2. Summed `amountReceived` from ALL returned records — including 8 old `fee_collections` (₹46,000) + ₹1,000 new receipt = **₹47,000**
3. Called `PUT /api/fees-manager/update-total-paid` with ₹47,000 — **overwriting the correct ₹20,000** that the backend had just computed

**Fix:** Removed `await updateFeesTotalPaid(regNo)` call from [`fees-manager.js`](src/main/resources/static/assets/js/fees-manager.js) line ~1962. The backend `recalculateFeesFromTransactions` is fully responsible for all fee total writes.

### Bug 4: Server running old code after Java changes

**Root Cause:** Java `.class` bytecode changes are **not hot-reloaded**. The JVM keeps old files in memory.

**Rule:** Always restart the server (`Ctrl+C` → `mvn spring-boot:run`) after **any Java source file change**.  
JS and HTML changes are served immediately — no restart needed.

---

## Developer Checklist — After Any Fees Logic Change

### After Java changes:
- [ ] Run `mvn compile` → confirm `BUILD SUCCESS`
- [ ] **Restart** `spring-boot:run` to load new bytecode
- [ ] Test: create at least 3 consecutive receipts for the same old (non-REG) student

### After JS/HTML changes:
- [ ] Hard-refresh browser (`Ctrl+F5`) — no server restart needed
- [ ] Test create receipt, verify UI values match DB values

### Verification Checks:
- [ ] `fees.total_paid` = Opening Balance + SUM of all `fee_receipts.amount_received` - refunds
- [ ] `fees.fees_due` = `total_fees - total_paid` (≥ 0)
- [ ] Old `fee_collections` records do **NOT** appear in `fees.total_paid`
- [ ] Delete a receipt → verify `fees.total_paid` correctly decreases
- [ ] Create 2nd, 3rd receipt → total grows correctly each time

---

## Version History

| Version | Date | Change |
|:---:|:---:|:---|
| v6.12 | 2026-08-22 | **Removed `updateFeesTotalPaid()` frontend call** — was overwriting correct backend value with wrong sum including fee_collections data |
| v6.12 | 2026-08-22 | Fixed broken Javadoc comment in `updateFeesTableAfterReceipt` |
| v6.12 | 2026-08-22 | Introduced `getOpeningBalance()` using `MIN(id)`, simplified `updateFeesTableAfterReceipt` to delegate all math to `recalculateFeesFromTransactions` — single source of truth |
| v6.11 | 2026-08-21 | Initial Solution 1 opening balance approach |
