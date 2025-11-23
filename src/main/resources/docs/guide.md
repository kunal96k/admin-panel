# Complete Student Management System - Thymeleaf Layout

## Architecture Overview

```
┌─────────────────────────────────────────────┐
│          layout/layout.html                 │
│  ┌────────────┐  ┌─────────────────────┐   │
│  │  Sidebar   │  │     Topbar          │   │
│  │  (Fixed)   │  └─────────────────────┘   │
│  │            │  ┌─────────────────────┐   │
│  │  - Dashboard│ │   CONTENT AREA      │   │
│  │  - Students │  │   (Changes based    │   │
│  │  - Accounts │  │    on page)         │   │
│  │  - Master   │  │                     │   │
│  │  - Reports  │  └─────────────────────┘   │
│  │            │  ┌─────────────────────┐   │
│  │  (Fixed)   │  │     Footer          │   │
│  └────────────┘  └─────────────────────┘   │
└─────────────────────────────────────────────┘

When user clicks "Dashboard" → loads dashboard/dashboard.html into CONTENT AREA
When user clicks "Enquiry" → loads student/enquiry.html into CONTENT AREA
When user clicks "Admission" → loads student/admission.html into CONTENT AREA
```

---

## File Structure

```
src/main/resources/
├── templates/
│   ├── layout/
│   │   └── layout.html           ← Main layout (sidebar, topbar, footer)
│   │
│   ├── dashboard/
│   │   └── dashboard.html        ← Dashboard stats & charts
│   │
│   ├── student/
│   │   ├── enquiry.html          ← Enquiry page
│   │   └── admission.html        ← Admission page
│   │
│   ├── accounts/
│   │   └── fees-manager.html     ← Fees management
│   │
│   ├── printing/
│   │   └── certificate.html      ← Certificate printing
│   │
│   ├── master/
│   │   ├── course.html           ← Course master
│   │   ├── employee.html         ← Employee master
│   │   ├── role.html             ← Role management
│   │   ├── bank.html             ← Bank master
│   │   ├── lead-source.html      ← Lead source
│   │   ├── package.html          ← Package creation
│   │   └── online-payment.html   ← Payment modes
│   │
│   └── reports/
│       ├── course-wise-sales.html ← Sales report
│       └── fees-collection.html   ← Collection report
│
└── static/
    └── assets/
        ├── css/styles.css
        ├── js/script.js
        └── ...
```

---

## How It Works

### 1. User visits `/dashboard`
```
Controller → Returns "dashboard/dashboard"
             ↓
Thymeleaf finds dashboard/dashboard.html
             ↓
Sees: layout:decorate="~{layout/layout}"
             ↓
Loads layout/layout.html
             ↓
Replaces layout:fragment="content" with dashboard content
             ↓
User sees: Sidebar + Topbar + Dashboard Stats + Footer
```

### 2. User clicks "Enquiry" link
```
Browser → GET /students/enquiry
          ↓
Controller → Returns "student/enquiry"
             ↓
Thymeleaf finds student/enquiry.html
             ↓
Sees: layout:decorate="~{layout/layout}"
             ↓
Loads layout/layout.html (SAME layout!)
             ↓
Replaces layout:fragment="content" with enquiry content
             ↓
User sees: Sidebar + Topbar + Enquiry Form + Footer
```
