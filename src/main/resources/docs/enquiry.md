// ============================================
// README.md Content
// ============================================

/*
# TechnoKraft Student Management System - Enquiry Module

## Overview
Production-ready Spring Boot implementation of the Enquiry module with comprehensive features including:
- Full CRUD operations with validation
- CSV import/export (both old and new formats)
- Advanced search and filtering
- Pagination and sorting
- HTTPS/SSL support
- CSRF protection
- Audit logging with SLF4J
- Soft delete functionality
- Comprehensive error handling

## Technology Stack
- **Java**: 21
- **Spring Boot**: 3.2.0
- **Database**: MySQL 8.0+ (with Hibernate/JPA)
- **Security**: Spring Security with HTTPS
- **Logging**: SLF4J + Logback
- **CSV Processing**: OpenCSV
- **Validation**: Jakarta Validation API
- **Build Tool**: Maven

## Prerequisites
1. Java JDK 21 or higher
2. MySQL 8.0 or higher
3. Maven 3.8+
4. SSL certificate (for production)

## Database Setup

### Create Database
```sql
CREATE DATABASE tts_database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Environment Variables (for production)
```bash
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=tts_database
export DB_USER=root
export DB_PASSWORD=your_password
export SPRING_PROFILE=prod
export SSL_ENABLED=true
export SSL_KEYSTORE_PATH=/path/to/keystore.p12
export SSL_KEYSTORE_PASSWORD=your_keystore_password
export SSL_KEY_ALIAS=tomcat
```

## Running the Application

### Development Mode
```bash
mvn spring-boot:run
```
Access at: http://localhost:8081

### Production Mode
```bash
export SPRING_PROFILE=prod
mvn clean package
java -jar target/sms-0.0.1-SNAPSHOT.jar
```
Access at: https://localhost:443 or https://team.ttsnashik.com

## API Endpoints

### Enquiry Management

#### Get All Enquiries (Paginated)
```http
GET /api/enquiries?page=0&size=25
```

#### Get Enquiry by ID
```http
GET /api/enquiries/{id}
```

#### Create New Enquiry
```http
POST /api/enquiries
Content-Type: application/json

{
  "firstName": "John",
  "middleName": "Kumar",
  "lastName": "Doe",
  "mobile": "9876543210",
  "email": "john@example.com",
  "courses": ["JAVA CORE AND ADVANCE", "WEB DEVELOPMENT"],
  "source": "Walk-in",
  "enquiryDate": "2025-11-23",
  "assignTo": "Charushila Wankhede",
  "status": "New"
}
```

#### Update Enquiry
```http
PUT /api/enquiries/{id}
Content-Type: application/json

{
  "firstName": "John",
  "mobile": "9876543210",
  // ... other fields
}
```

#### Delete Enquiry (Soft Delete)
```http
DELETE /api/enquiries/{id}
```

#### Search Enquiries
```http
POST /api/enquiries/search
Content-Type: application/json

{
  "searchTerm": "john",
  "status": "New",
  "source": "Walk-in",
  "fromDate": "2025-01-01",
  "toDate": "2025-12-31",
  "page": 0,
  "size": 25,
  "sortBy": "enquiryDate",
  "sortDirection": "DESC"
}
```

#### Bulk Import from CSV
```http
POST /api/enquiries/bulk-import?importType=OLD_FORMAT
Content-Type: multipart/form-data

file: enquiries.csv
```

Import Types:
- `OLD_FORMAT`: Enquiry No., Student Name, Mobile No., Course, Enquiry Source, Enquiry Date, Assign To, Enquiry Status
- `NEW_FORMAT`: Enquiry No., First Name, Middle Name, Last Name, Mobile Primary, Mobile Secondary, Email Primary, ...

#### Export to CSV
```http
GET /api/enquiries/export/csv
```

#### Get Statistics
```http
GET /api/enquiries/statistics
```

Response:
```json
{
  "total": 1500,
  "byStatus": {
    "New": 450,
    "Contacted": 300,
    "Converted": 250,
    "Closed": 500
  },
  "bySource": {
    "Walk-in": 600,
    "Website": 400,
    "Social Media": 300,
    "Referral": 200
  },
  "pendingFollowups": 75
}
```

## Project Structure
```
src/
├── main/
│   ├── java/com/tts/sms/
│   │   ├── SmsApplication.java          # Main application class
│   │   ├── config/
│   │   │   ├── SecurityConfig.java      # Security & CORS
│   │   │   ├── WebConfig.java           # MVC configuration
│   │   │   └── ApplicationConfig.java   # App config
│   │   ├── controller/
│   │   │   ├── LayoutController.java    # Thymeleaf pages
│   │   │   └── EnquiryController.java   # REST API
│   │   ├── dto/
│   │   │   ├── EnquiryRequestDTO.java
│   │   │   ├── EnquiryResponseDTO.java
│   │   │   ├── BulkImportRequestDTO.java
│   │   │   └── EnquirySearchDTO.java
│   │   ├── entity/
│   │   │   └── Enquiry.java             # JPA Entity
│   │   ├── repository/
│   │   │   └── EnquiryRepository.java   # Data access
│   │   ├── service/
│   │   │   ├── EnquiryService.java      # Business logic
│   │   │   ├── EnquiryMapper.java       # DTO mapping
│   │   │   └── CSVService.java          # CSV processing
│   │   └── exception/
│   │       ├── GlobalExceptionHandler.java
│   │       ├── ResourceNotFoundException.java
│   │       └── ErrorResponse.java
│   └── resources/
│       ├── application.yml              # Root config
│       ├── application-common.yml       # Shared config
│       ├── application-dev.yml          # Dev config
│       ├── application-prod.yml         # Prod config
│       ├── templates/                   # Thymeleaf templates
│       └── static/                      # CSS, JS, images
└── test/
    └── java/com/tts/sms/
        └── (test classes)
```

## Key Features

### 1. Dual CSV Format Support
- **Old Format**: Legacy import from previous system
- **New Format**: Enhanced format with detailed fields
- Automatic field mapping and validation

### 2. Comprehensive Validation
- Mobile number format validation
- Email validation
- Required field checks
- Date format validation
- Null-safe operations

### 3. Advanced Search
- Full-text search across name, mobile, email
- Filter by status, source, course, assignee
- Date range filtering
- Pagination and sorting

### 4. Security Features
- HTTPS/SSL support in production
- CSRF token protection
- CORS configuration
- Session management
- Password encryption with BCrypt

### 5. Audit & Logging
- SLF4J logging throughout
- Created/Updated timestamps
- Created/Updated by tracking
- Soft delete for data integrity
- Comprehensive error logging

### 6. Performance Optimizations
- Database indexing on searchable fields
- Connection pooling (HikariCP)
- Batch processing for bulk operations
- Pagination for large datasets
- JPA query optimization

## Validation Rules

### Mobile Number
- Must be 10 digits
- Must start with 6-9
- Pattern: `^[6-9]\\d{9}$`

### Email
- Standard email format validation
- Optional field

### Aadhaar
- Must be 12 digits if provided
- Optional field

### Required Fields
- At least one of: fullName OR (firstName + lastName)
- mobile (required)
- course (required)
- source (required)
- enquiryDate (required)

## Environment Configuration

### Development (application-dev.yml)
- Port: 8081
- CSRF: Disabled
- SQL Logging: Enabled
- Error Details: Full stack traces
- SSL: Disabled

### Production (application-prod.yml)
- Port: 443
- CSRF: Enabled with cookie tokens
- SQL Logging: Disabled
- Error Details: Minimal
- SSL: Enabled with certificate
- HTTPS redirect: Enabled
- Compression: Enabled

## Logging

Logs are written to:
- Console: All environments
- File: `logs/sms-application.log` (rotating, 10MB max, 30 days retention)

Log Levels:
- DEV: DEBUG
- PROD: INFO

## Error Handling

All errors return consistent JSON response:
```json
{
  "timestamp": "2025-11-23T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Detailed error message",
  "path": "/api/enquiries",
  "validationErrors": {
    "mobile": "Invalid mobile number format",
    "email": "Invalid email format"
  }
}
```

## Testing

Run tests:
```bash
mvn test
```

## Build & Deploy

### Create JAR
```bash
mvn clean package -DskipTests
```

### Deploy to Production
1. Copy JAR to server
2. Set environment variables
3. Run with systemd or supervisor
4. Configure nginx reverse proxy (optional)

## Troubleshooting

### Database Connection Issues
- Check MySQL is running
- Verify credentials in environment variables
- Ensure database exists and is accessible

### SSL Certificate Issues
- Verify certificate paths in application-prod.yml
- Check certificate validity
- Ensure keystore password is correct

### CSV Import Errors
- Check CSV encoding (UTF-8)
- Verify header format matches expected format
- Check for duplicate mobile numbers

## Future Enhancements
- Email notifications for follow-ups
- WhatsApp integration
- Dashboard analytics
- Export to Excel
- Advanced reporting
- Mobile app API

## Support
For issues or questions, contact: admin@ttsnashik.com

## License
Proprietary - TechnoKraft Training & Solutions Pvt Ltd
*/