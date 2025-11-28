# Complete Project Structure for Course Management System

## 📁 Full Directory Structure

```
tts-sms/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── tts/
│   │   │           └── sms/
│   │   │               ├── SmsApplication.java (Main Application Class)
│   │   │               ├── config/
│   │   │               │   └── WebConfig.java ✅ (Updated with course upload)
│   │   │               ├── controller/
│   │   │               │   ├── CourseController.java ✅ (Existing - Thymeleaf)
│   │   │               │   ├── CourseRestController.java ✅ NEW
│   │   │               │   └── SubjectRestController.java ✅ NEW
│   │   │               ├── dto/
│   │   │               │   ├── CourseDTO.java ✅ NEW
│   │   │               │   └── SubjectDTO.java ✅ NEW
│   │   │               ├── entity/
│   │   │               │   ├── Course.java ✅ NEW
│   │   │               │   └── Subject.java ✅ NEW
│   │   │               ├── exception/
│   │   │               │   └── GlobalExceptionHandler.java ✅ NEW
│   │   │               ├── repository/
│   │   │               │   ├── CourseRepository.java ✅ NEW
│   │   │               │   └── SubjectRepository.java ✅ NEW
│   │   │               └── service/
│   │   │                   ├── CourseService.java ✅ NEW
│   │   │                   ├── SubjectService.java ✅ NEW
│   │   │                   └── FileStorageService.java ✅ NEW
│   │   ├── resources/
│   │   │   ├── application.yml ✅ (Updated)
│   │   │   ├── application-dev.yml ✅ (Updated)
│   │   │   ├── application-prod.yml ✅ (Updated)
│   │   │   ├── static/
│   │   │   │   ├── assets/
│   │   │   │   │   ├── css/
│   │   │   │   │   ├── js/
│   │   │   │   │   └── images/
│   │   │   │   └── ...
│   │   │   └── templates/
│   │   │       └── master/
│   │   │           └── course.html (Your existing HTML)
│   │   └── webapp/
│   └── test/
│       └── java/
│           └── com/
│               └── tts/
│                   └── sms/
├── uploads/ ✅ (Created automatically)
│   ├── courses/ ✅ (Course images stored here)
│   └── csv/ (CSV uploads)
├── logs/
│   └── sms-application.log
├── certificate/
│   └── SSL/
│       └── team.ttsnashik.com.pfx
├── pom.xml
└── README.md
```

## 📋 Files Created/Modified

### ✅ New Files Created

1. **Entity Layer**
    - `Course.java` - Course entity with JPA annotations
    - `Subject.java` - Subject entity with relationship to Course

2. **DTO Layer**
    - `CourseDTO.java` - All course-related DTOs
    - `SubjectDTO.java` - All subject-related DTOs

3. **Repository Layer**
    - `CourseRepository.java` - Course data access with custom queries
    - `SubjectRepository.java` - Subject data access with custom queries

4. **Service Layer**
    - `CourseService.java` - Business logic for courses
    - `SubjectService.java` - Business logic for subjects
    - `FileStorageService.java` - File upload/delete operations

5. **Controller Layer**
    - `CourseRestController.java` - REST API endpoints for courses
    - `SubjectRestController.java` - REST API endpoints for subjects
    - `CourseController.java` - (Existing) Thymeleaf page controller

6. **Exception Handling**
    - `GlobalExceptionHandler.java` - Centralized exception handling

### ✅ Files Updated

1. **Configuration**
    - `WebConfig.java` - Added course image serving configuration
    - `application.yml` - Added upload directory configurations
    - `application-dev.yml` - Added dev-specific settings
    - `application-prod.yml` - Added prod-specific settings

## 🗄️ Database Tables Created

The application will automatically create these tables:

```sql
-- Courses Table
CREATE TABLE courses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_name VARCHAR(100) NOT NULL,
    course_fees DECIMAL(10, 2) NOT NULL,
    course_image_path VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Subjects Table
CREATE TABLE subjects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subject_name VARCHAR(100) NOT NULL,
    course_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
);
```

## 📦 Maven Dependencies Required

Ensure these dependencies are in your `pom.xml`:

```xml
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring Boot JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- Spring Boot Thymeleaf -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>

    <!-- MySQL Driver -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Spring Boot Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
</dependencies>
```

## 🚀 Quick Start Guide

### 1. Update Your Configuration

Edit `src/main/resources/application-dev.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/tts_database?createDatabaseIfNotExist=true
    username: root
    password: your_password
```

### 2. Build the Project

```bash
mvn clean install
```

### 3. Run the Application

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Or set the active profile in your IDE:
```
VM Options: -Dspring.profiles.active=dev
```

### 4. Access the Application

- **Web UI**: http://localhost:8081/master/course
- **API Base**: http://localhost:8081/api/courses

## 📸 Image Storage Details

### Storage Location

- **Development**: `./uploads/courses/`
- **Production**: `/var/www/tts-sms/uploads/courses/`

### Access URLs

- **Web UI Access**: The HTML page will automatically display images
- **Direct Access**: `http://localhost:8081/uploads/courses/{filename}`
- **API Response**: Returns filename, front-end constructs full URL

### Example Image URL

After uploading an image named `abc.jpg`, it's stored as:
- **Filename**: `550e8400-e29b-41d4-a716-446655440000.jpg` (UUID)
- **Full URL**: `http://localhost:8081/uploads/courses/550e8400-e29b-41d4-a716-446655440000.jpg`

## 🔧 Configuration Options

### Upload Directory

Change in `application.yml`:

```yaml
app:
  upload:
    courses:
      dir: ./uploads/courses  # Relative path
      # or
      dir: /var/www/uploads/courses  # Absolute path
```

### File Size Limits

```yaml
app:
  upload:
    courses:
      max-file-size: 2MB  # Change as needed
      allowed-extensions: jpg,jpeg,png,gif
```

### Multipart Configuration

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

## 🔍 Verifying Installation

### 1. Check Upload Directory

```bash
ls -la uploads/courses/
```

Should show directory with write permissions.

### 2. Test API Endpoints

```bash
# Get all courses
curl http://localhost:8081/api/courses

# Upload a course with image
curl -X POST http://localhost:8081/api/courses \
  -F "courseName=Test Course" \
  -F "courseFees=5000" \
  -F "courseImage=@test.jpg"
```

### 3. Check Logs

```bash
tail -f logs/sms-application.log
```

Look for:
- ✅ Created upload directory
- ✅ Resource handlers configured successfully
- ✅ Course images directory: /path/to/uploads/courses

## 🐛 Troubleshooting

### Issue: Upload directory not created

**Solution**:
```bash
mkdir -p uploads/courses
chmod 755 uploads/courses
```

### Issue: Images not accessible

**Check**:
1. WebConfig has correct mapping for `/uploads/courses/**`
2. Directory path is correct in `application.yml`
3. Files exist in the upload directory
4. No permission issues

### Issue: File upload fails

**Check**:
1. File size under limit (2MB default)
2. File type is allowed (jpg, jpeg, png, gif)
3. Upload directory has write permissions

## 📝 Notes

1. **Auto-generated Filenames**: Files are renamed using UUID to prevent conflicts
2. **Original Filename**: Not stored, only UUID filename
3. **Soft Delete**: Courses/Subjects are marked inactive, not deleted from DB
4. **Image Deletion**: When course is updated with new image, old image is deleted
5. **Security**: File paths are validated to prevent directory traversal attacks

## 🔐 Production Deployment Notes

1. **Change Upload Directory**:
   ```yaml
   app:
     upload:
       courses:
         dir: /var/www/tts-sms/uploads/courses
   ```

2. **Set Proper Permissions**:
   ```bash
   sudo chown -R tomcat:tomcat /var/www/tts-sms/uploads
   sudo chmod -R 755 /var/www/tts-sms/uploads
   ```

3. **Use Environment Variables**:
   ```bash
   export COURSES_UPLOAD_DIR=/var/www/tts-sms/uploads/courses
   ```

4. **Backup Strategy**: Regularly backup the `uploads/` directory

5. **Storage Monitoring**: Monitor disk space in upload directory