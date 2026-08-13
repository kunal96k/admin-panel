# Production Database Cleanup & Migration Guide: New Student Fees Isolation

**Date:** 2026-08-13  
**Target Component:** Fees Manager & Database (`fees`, `fee_receipts`, `fee_collections`)  
**SQL File:** [db-cleanup-2026-08-13.sql](file:///c:/Users/TechnoKraft/Desktop/backup/project_tts_backup/tts-sms/sms/src/main/resources/docs/db-cleanup-2026-08-13.sql)

---

## 1. Issue Overview

For new student entries (registration numbers starting with `REG`, e.g. `REG8353` - Varun Jagdish Deore), the system was incorrectly displaying old imported CSV receipts (`INV-OLD-12251` & `INV-OLD-12312`) from 2024 because the old student record in `fee_collections` shared the same mobile number (`9665505911`).

This caused:
1. Old CSV receipt amounts (e.g. ₹11,000) to be added to `total_paid` in the `fees` table.
2. `total_paid` showing **₹29,000** instead of actual paid **₹18,000** (`REC0918`: ₹10k, `REC0691`: ₹4k, `REC0636`: ₹4k).
3. `fees_due` showing **₹9,000** instead of actual due **₹20,000** (Total Fees: ₹38,000).

---

## 2. Java Code Fix Summary ([FeesManagerService.java](file:///c:/Users/TechnoKraft/Desktop/backup/project_tts_backup/tts-sms/sms/src/main/java/com/tts/sms/service/FeesManagerService.java))

1. **`getReceiptsByRegistrationNumber(String regNo)`**: Added `isOldStudent` check (`!regNo.startsWith("REG")`) before querying `fee_collections`. New students (`REG...`) will ONLY read from `fee_receipts`.
2. **`getReceiptsFromFeeCollections(String regNo)`**: Added guard condition to return an empty list immediately for `REG...` registration numbers.

---

## 3. Production Database Instructions

Run the following steps on your production MySQL server database (`teamtts` / `tts_crm`):

### Step 1: Audit Mismatched Rows (SELECT)
Execute this query to inspect all new students who had old CSV amounts added to their stored totals:

```sql
SELECT 
    f.registration_number, 
    f.student_name, 
    f.total_fees, 
    f.total_paid AS current_stored_paid, 
    COALESCE(r.receipts_sum, 0) AS actual_receipts_sum, 
    (f.total_paid - COALESCE(r.receipts_sum, 0)) AS excess_imported_paid, 
    f.fees_due AS current_stored_due, 
    GREATEST(0, f.total_fees - COALESCE(r.receipts_sum, 0)) AS calculated_correct_due 
FROM fees f 
LEFT JOIN (
    SELECT registration_number, COALESCE(SUM(amount_received), 0) AS receipts_sum 
    FROM fee_receipts 
    WHERE is_deleted = 0 
    GROUP BY registration_number
) r ON f.registration_number = r.registration_number 
WHERE f.registration_number LIKE 'REG%' 
  AND ABS(f.total_paid - COALESCE(r.receipts_sum, 0)) > 0;
```

### Step 2: Apply Database Fix (UPDATE)
Execute the update statement to set `total_paid` to the exact sum of valid `fee_receipts` for all `REG` students, and recompute `fees_due`:

```sql
UPDATE fees f 
LEFT JOIN (
    SELECT registration_number, COALESCE(SUM(amount_received), 0) AS receipts_sum 
    FROM fee_receipts 
    WHERE is_deleted = 0 
    GROUP BY registration_number
) r ON f.registration_number = r.registration_number 
SET f.total_paid = COALESCE(r.receipts_sum, 0), 
    f.fees_due = GREATEST(0, f.total_fees - COALESCE(r.receipts_sum, 0)) 
WHERE f.registration_number LIKE 'REG%';
```

### Step 3: Verify Zero Mismatches Remain
Confirm all `REG` students have accurate figures:

```sql
SELECT COUNT(*) AS remaining_mismatches 
FROM fees f 
LEFT JOIN (
    SELECT registration_number, COALESCE(SUM(amount_received), 0) AS receipts_sum 
    FROM fee_receipts 
    WHERE is_deleted = 0 
    GROUP BY registration_number
) r ON f.registration_number = r.registration_number 
WHERE f.registration_number LIKE 'REG%' 
  AND ABS(f.total_paid - COALESCE(r.receipts_sum, 0)) > 0;
```

Expected result: `0` remaining mismatches.
