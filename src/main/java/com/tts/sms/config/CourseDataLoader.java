package com.tts.sms.config;

import com.tts.sms.model.Course;
import com.tts.sms.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * DataLoader for initializing default course data
 * TechnoKraft Training & Solutions - Course Management System
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class CourseDataLoader {

    private final CourseRepository courseRepository;

    @Bean
    @Order(1)
    public CommandLineRunner loadCourseData() {
        return args -> {
            // Check if courses already exist
            if (courseRepository.countByIsActiveTrue() > 0) {
                log.info("[OK] Courses already exist in database. Skipping data initialization.");
                return;
            }

            log.info("[SYNC] Initializing default course data...");

            List<CourseData> defaultCourses = Arrays.asList(
                    new CourseData("SPRING BOOT", 6000),
                    new CourseData("MongoDB", 4000),
                    new CourseData("AI CRASH COURSE", 1500),
                    new CourseData("ENGLISH AND MARATHI TYPING", 4000),
                    new CourseData("CCNA Switching", 5000),
                    new CourseData("PYTHON - DATA ANALYTICS", 7000),
                    new CourseData("Professional English Communication", 3000),
                    new CourseData("SQL - Data Analytics", 3000),
                    new CourseData("GRAPHICS DESIGN", 12000),
                    new CourseData("MEDICAL CODING", 20000),
                    new CourseData("ARTIFICIAL INTELLIGENCE", 45000),
                    new CourseData("FULL STACK JavaScript DEVELOPMENT", 40000),
                    new CourseData("FULL STACK PYTHON DEVELOPMENT", 40000),
                    new CourseData("FULL STACK JAVA DEVELOPMENT", 40000),
                    new CourseData("FULL STACK PHP DEVELOPMENT", 40000),
                    new CourseData("MEAN STACK", 20000),
                    new CourseData("FULL STACK BACKEND JavaScript", 20000),
                    new CourseData("FULL STACK BACKEND PYTHON", 20000),
                    new CourseData("FULL STACK BACKEND PHP", 20000),
                    new CourseData("FULL STACK BACKEND JAVA CORE & ADVANCE", 20000),
                    new CourseData("EXPRESS JS", 7000),
                    new CourseData("DATA ENGINEERING", 40000),
                    new CourseData("FULL STACK", 40000),
                    new CourseData("FULL STACK BACKEND", 20000),
                    new CourseData("FULL STACK FRONTEND", 20000),
                    new CourseData("SAP - ABAP", 25000),
                    new CourseData("TABLEAU", 7000),
                    new CourseData("MERN STACK", 25000),
                    new CourseData("ASP.NET", 9000),
                    new CourseData("AUTO CAD", 8000),
                    new CourseData("SAP-MM", 50000),
                    new CourseData("ANIMATION", 100000),
                    new CourseData("SAP PP", 50000),
                    new CourseData("SAP SD", 50000),
                    new CourseData("SAP FICO", 40000),
                    new CourseData("UGNX", 20000),
                    new CourseData("CATIA", 15000),
                    new CourseData("SOLID-WORKS", 10000),
                    new CourseData("CREO", 5000),
                    new CourseData("DATA ANALYTICS", 15000),
                    new CourseData("BUSINESS ANALYTICS", 20000),
                    new CourseData("C PANEL AND WHM", 10000),
                    new CourseData("LARAVEL", 12000),
                    new CourseData("AUTOMATION TESTING", 10000),
                    new CourseData("MANUAL TESTING", 10000),
                    new CourseData("KUBERNETES", 6000),
                    new CourseData("DJANGO", 5000),
                    new CourseData("WEB DEVELOPMENT", 7000),
                    new CourseData("CSS", 2000),
                    new CourseData("POWER BI", 5000),
                    new CourseData("HARDWARE", 5000),
                    new CourseData("Data Structures Using C++", 5000),
                    new CourseData("HTML", 3000),
                    new CourseData("ANSIBLE", 5000),
                    new CourseData("CYBER SECURITY", 20000),
                    new CourseData("PALOALTO", 10000),
                    new CourseData("REACT JS", 18000),
                    new CourseData("HACKING", 10000),
                    new CourseData("DATA SCIENCE", 18000),
                    new CourseData("PENETRATION TESTING", 15000),
                    new CourseData("PYTHON + MACHINE LEARNING", 20000),
                    new CourseData("JAVA CORE & ADV + JAVA MVC", 20000),
                    new CourseData("MYSQL", 12000),
                    new CourseData("SOFTWARE TESTING", 18000),
                    new CourseData("WEB DESIGNING AND DEVELOPMENT", 18000),
                    new CourseData("C & C++ PROGRAMMING", 6000),
                    new CourseData(".NET", 14000),
                    new CourseData("JAVA FRAMEWORK", 9000),
                    new CourseData("CISCO CERTIFIED NETWORK PROFESSIONAL", 18000),
                    new CourseData("JAVASCRIPT", 5000),
                    new CourseData("JAVA CORE AND ADVANCE", 12000),
                    new CourseData("WEBSITE DESIGNING", 7000),
                    new CourseData("MACHINE LEARNING", 15000),
                    new CourseData("RDBMS", 4000),
                    new CourseData("MCSA", 7500),
                    new CourseData("ADVANCE EXCEL", 4000),
                    new CourseData("DATA STRUCTURE USING C", 4000),
                    new CourseData("DBMS", 4000),
                    new CourseData("MCSE", 7500),
                    new CourseData("NODE JS", 10000),
                    new CourseData("Angular JS", 10000),
                    new CourseData(".NET MVC", 20000),
                    new CourseData("JAVA MVC", 20000),
                    new CourseData("DEVOPS", 18000),
                    new CourseData("ORACLE DBA", 12000),
                    new CourseData("DIGITAL MARKETING", 16000),
                    new CourseData("ANDROID", 15000),
                    new CourseData("SHELL SCRIPTING", 7000),
                    new CourseData("PHP", 7000),
                    new CourseData("REDHAT (RHCVA)", 15000),
                    new CourseData("REDHAT (OpenStack)", 15000),
                    new CourseData("REDHAT (RHCSA)", 7000),
                    new CourseData("REDHAT (RHCE)", 7000),
                    new CourseData("REDHAT (RHCSA & RHCE)", 12000),
                    new CourseData("PYTHON", 10000),
                    new CourseData("VMWARE", 15000),
                    new CourseData("MCSA & MCSE", 15000),
                    new CourseData("BIG DATA HADOOP", 15000),
                    new CourseData("AMAZON WEB SERVICES", 15000),
                    new CourseData("Azure", 15000),
                    new CourseData("JAVA ADVANCE", 6000),
                    new CourseData("JAVA CORE", 6000),
                    new CourseData("C++ PROGRAMMING", 3000),
                    new CourseData("C PROGRAMMING", 3000),
                    new CourseData("CISCO CERTIFIED NETWORK ASSOCIATE", 10000),
                    new CourseData("NETWORKING (N+)", 4000)
            );

            int savedCount = 0;
            for (CourseData courseData : defaultCourses) {
                try {
                    // Check if course already exists
                    if (!courseRepository.existsByCourseNameIgnoreCaseAndIsActiveTrue(courseData.name)) {
                        Course course = new Course();
                        course.setCourseName(courseData.name);
                        course.setCourseFees(BigDecimal.valueOf(courseData.fees));
                        course.setIsActive(true);
                        course.setCreatedBy("System");

                        courseRepository.save(course);
                        savedCount++;
                    }
                } catch (Exception e) {
                    log.error("[FAIL] Failed to save course: {} - {}", courseData.name, e.getMessage());
                }
            }

            log.info("[OK] Successfully initialized {} default courses", savedCount);
        };
    }

    /**
     * Inner class to hold course data
     */
    private static class CourseData {
        String name;
        double fees;

        CourseData(String name, double fees) {
            this.name = name;
            this.fees = fees;
        }
    }
}