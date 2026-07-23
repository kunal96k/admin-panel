# Implementation Plan: Django Python Server (`attendance.ttsnashik.com`)

This plan provides the exact code, file structures, and step-by-step instructions required to implement the receiving API endpoints in your **Django Python project** hosted at `attendance.ttsnashik.com`.

---

## Architecture & Data Flow

```
 ┌────────────────────────────────────────────────────────┐
 │            TTS-SMS Backend (Spring Boot)               │
 │                                                        │
 │   Sends POST Request with X-API-KEY header             │
 │   Endpoints called:                                    │
 │    - https://attendance.ttsnashik.com/api/v1/students/sync-single
 │    - https://attendance.ttsnashik.com/api/v1/students/sync-batch
 └───────────────────────────┬────────────────────────────┘
                             │
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │           Django Server (attendance.ttsnashik.com)     │
 │                                                        │
 │  1. urls.py ──> Routes API request                     │
 │  2. @require_api_key ──> Validates X-API-KEY header    │
 │  3. views.py ──> Processes JSON & update_or_create()   │
 │  4. models.py ──> Saves in StudentAttendanceRecord     │
 └────────────────────────────────────────────────────────┘
```

---

## Proposed Changes in Django Project

Assuming your Django app is named `students` or `attendance` (adjust app name as needed):

### 1. Database Model (`models.py`)
#### [MODIFY] `students/models.py`
Add `StudentAttendanceRecord` to store student registration details pushed from TTS-SMS:

```python
from django.db import models

class StudentAttendanceRecord(models.Model):
    reg_no = models.CharField(max_length=50, unique=True, db_index=True, help_text="Registration Number e.g. REG2026001")
    full_name = models.CharField(max_length=200)
    email = models.EmailField(max_length=120, null=True, blank=True)
    mobile_no = models.CharField(max_length=20, null=True, blank=True)
    course_name = models.CharField(max_length=200, null=True, blank=True)
    admission_date = models.DateField()
    synced_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = 'attendance_students'
        verbose_name = 'Student Attendance Record'
        verbose_name_plural = 'Student Attendance Records'
        ordering = ['-synced_at']

    def __str__(self):
        return f"{self.reg_no} - {self.full_name}"
```

---

### 2. API Key Authentication Decorator (`decorators.py`)
#### [NEW] `students/decorators.py`
Decorator to enforce `X-API-KEY` security header validation:

```python
import os
from functools import wraps
from django.http import JsonResponse
from django.conf import settings

def require_api_key(view_func):
    @wraps(view_func)
    def _wrapped_view(request, *args, **kwargs):
        expected_key = getattr(settings, 'ATTENDANCE_API_KEY', 'TTS_ATTENDANCE_SECRET_KEY_2026')
        
        # Django converts X-API-KEY header to HTTP_X_API_KEY in META
        provided_key = request.META.get('HTTP_X_API_KEY') or request.headers.get('X-API-KEY')
        
        if not provided_key or provided_key != expected_key:
            return JsonResponse({
                "success": False,
                "message": "Unauthorized: Invalid or missing X-API-KEY header.",
                "processedCount": 0
            }, status=401)
            
        return view_func(request, *args, **kwargs)
    return _wrapped_view
```

---

### 3. API Views (`views.py`)
#### [MODIFY] `students/views.py`
Add `@csrf_exempt` views for single student sync and batch date-range sync:

```python
import json
from datetime import datetime
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_POST
from django.db import transaction
from .models import StudentAttendanceRecord
from .decorators import require_api_key

@csrf_exempt
@require_POST
@require_api_key
def sync_single_student(request):
    try:
        data = json.loads(request.body.decode('utf-8'))
        reg_no = data.get('regNo')

        if not reg_no:
            return JsonResponse({'success': False, 'message': 'regNo is required'}, status=400)

        admission_date_str = data.get('admissionDate')
        adm_date = datetime.strptime(admission_date_str, '%Y-%m-%d').date() if admission_date_str else None
        
        courses_list = data.get('courses') or []
        course_name = data.get('courseName') or (", ".join(courses_list) if courses_list else "")

        student, created = StudentAttendanceRecord.objects.update_or_create(
            reg_no=reg_no,
            defaults={
                'full_name': data.get('fullName', ''),
                'email': data.get('email'),
                'mobile_no': data.get('mobileNo'),
                'course_name': course_name,
                'admission_date': adm_date,
            }
        )

        status_text = "created" if created else "updated"
        return JsonResponse({
            'success': True,
            'message': f"Student {reg_no} successfully {status_text}.",
            'processedCount': 1,
            'timestamp': datetime.now().isoformat()
        }, status=200)

    except json.JSONDecodeError:
        return JsonResponse({'success': False, 'message': 'Invalid JSON body'}, status=400)
    except Exception as e:
        return JsonResponse({'success': False, 'message': str(e)}, status=500)


@csrf_exempt
@require_POST
@require_api_key
def sync_batch_students(request):
    try:
        data = json.loads(request.body.decode('utf-8'))
        students_list = data.get('students', [])

        processed = 0
        with transaction.atomic():
            for student_data in students_list:
                reg_no = student_data.get('regNo')
                if not reg_no:
                    continue

                adm_date_str = student_data.get('admissionDate')
                adm_date = datetime.strptime(adm_date_str, '%Y-%m-%d').date() if adm_date_str else None
                
                courses_list = student_data.get('courses') or []
                course_name = student_data.get('courseName') or (", ".join(courses_list) if courses_list else "")

                StudentAttendanceRecord.objects.update_or_create(
                    reg_no=reg_no,
                    defaults={
                        'full_name': student_data.get('fullName', ''),
                        'email': student_data.get('email'),
                        'mobile_no': student_data.get('mobileNo'),
                        'course_name': course_name,
                        'admission_date': adm_date,
                    }
                )
                processed += 1

        return JsonResponse({
            'success': True,
            'message': f"Successfully synced {processed} student records.",
            'processedCount': processed,
            'timestamp': datetime.now().isoformat()
        }, status=200)

    except json.JSONDecodeError:
        return JsonResponse({'success': False, 'message': 'Invalid JSON body'}, status=400)
    except Exception as e:
        return JsonResponse({'success': False, 'message': str(e)}, status=500)
```

---

### 4. URL Routing (`urls.py`)
#### [MODIFY] `students/urls.py` (or project `urls.py`)
Add API endpoints to your Django routes:

```python
from django.urls import path
from . import views

urlpatterns = [
    # Existing routes...
    path('api/v1/students/sync-single', views.sync_single_student, name='sync_single_student'),
    path('api/v1/students/sync-batch', views.sync_batch_students, name='sync_batch_students'),
]
```

---

### 5. Settings Configuration (`settings.py`)
#### [MODIFY] `project/settings.py`
Add API Key configuration and ensure CORS headers permit `X-API-KEY`:

```python
import os

# Secret key shared with Spring Boot TTS-SMS
ATTENDANCE_API_KEY = os.getenv('ATTENDANCE_API_KEY', 'TTS_ATTENDANCE_SECRET_KEY_2026')

# Allow X-API-KEY header if django-cors-headers is installed
CORS_ALLOW_HEADERS = [
    'accept',
    'accept-encoding',
    'authorization',
    'content-type',
    'dnt',
    'origin',
    'user-agent',
    'x-csrftoken',
    'x-requested-with',
    'x-api-key',  # <--- Added
]
```

---

## Migration Commands to Run on Django Server

Once the `models.py` file is updated on `attendance.ttsnashik.com`:

```bash
# Generate migration file
python manage.py makemigrations

# Apply database migration
python manage.py migrate

# Restart Django Gunicorn / Uwsgi / Nginx service
sudo systemctl restart gunicorn  # or your webserver service
```

---

## Verification & Testing (cURL)

### 1. Test Single Student Sync API
```bash
curl -X POST https://attendance.ttsnashik.com/api/v1/students/sync-single \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: TTS_ATTENDANCE_SECRET_KEY_2026" \
  -d '{
    "regNo": "TTS2026001",
    "fullName": "Kunal R. Patil",
    "email": "kunal@example.com",
    "mobileNo": "9876543210",
    "courseName": "Java Fullstack",
    "admissionDate": "2026-07-15"
  }'
```

### 2. Test Batch Date-Range Sync API
```bash
curl -X POST https://attendance.ttsnashik.com/api/v1/students/sync-batch \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: TTS_ATTENDANCE_SECRET_KEY_2026" \
  -d '{
    "fromDate": "2026-01-01",
    "toDate": "2026-07-20",
    "totalRecords": 1,
    "students": [
      {
        "regNo": "TTS2026001",
        "fullName": "Kunal R. Patil",
        "email": "kunal@example.com",
        "mobileNo": "9876543210",
        "courseName": "Java Fullstack",
        "admissionDate": "2026-07-15"
      }
    ]
  }'
```
