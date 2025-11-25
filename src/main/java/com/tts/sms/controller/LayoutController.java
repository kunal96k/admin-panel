package com.tts.sms.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LayoutController {

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("activePage", "dashboard");
        return "dashboard/dashboard";  // Loads dashboard/dashboard.html
    }

    @GetMapping("/students/enquiry")
    public String enquiry(Model model) {
        model.addAttribute("pageTitle", "Student Enquiry");
        model.addAttribute("activePage", "enquiry");
        return "student/enquiry";
    }

    // Student Admission Page
    @GetMapping("/students/admission")
    public String admission(Model model) {
        model.addAttribute("pageTitle", "Student Admission - TechnoKraft");
        model.addAttribute("activePage", "admission");
        return "student/admission";
    }

    // Fees Manager
    @GetMapping("/accounts/fees-manager")
    public String feesManager() {
        return "accounts/fees-manager";
    }

    // Certificate Printing
    @GetMapping("/printing/certificate")
    public String certificate(Model model) {
        model.addAttribute("pageTitle", "Certificate Printing - TechnoKraft");
        model.addAttribute("activePage", "certificate");
        return "printing/certificate";
    }

    // Course Master
    @GetMapping("/master/course")
    public String course(Model model) {
        model.addAttribute("pageTitle", "Course Master - TechnoKraft");
        model.addAttribute("activePage", "course");
        return "master/course";
    }

    // Employee Master
    @GetMapping("/master/employee")
    public String employee(Model model) {
        model.addAttribute("pageTitle", "Employee Master - TechnoKraft");
        model.addAttribute("activePage", "employee");
        return "master/employee";
    }

    // Role Management
    @GetMapping("/master/role")
    public String role(Model model) {
        model.addAttribute("pageTitle", "Role Management - TechnoKraft");
        model.addAttribute("activePage", "role");
        return "master/role";
    }

    // Bank Master
    @GetMapping("/master/bank")
    public String bank(Model model) {
        model.addAttribute("pageTitle", "Bank Master - TechnoKraft");
        model.addAttribute("activePage", "bank");
        return "master/bank";
    }

    // Lead Source
    @GetMapping("/master/lead-source")
    public String leadSource(Model model) {
        model.addAttribute("pageTitle", "Lead Source - TechnoKraft");
        model.addAttribute("activePage", "lead-source");
        return "master/lead-source";
    }

    // Create Package
    @GetMapping("/master/package")
    public String createPackage(Model model) {
        model.addAttribute("pageTitle", "Create Package - TechnoKraft");
        model.addAttribute("activePage", "create-package");
        return "master/package";
    }

    // Online Payment Mode
    @GetMapping("/master/online-payment")
    public String onlinePayment(Model model) {
        model.addAttribute("pageTitle", "Online Payment Mode - TechnoKraft");
        model.addAttribute("activePage", "online-payment");
        return "master/online-payment";
    }

    // Course-wise Sales Report
    @GetMapping("/reports/course-wise-sales")
    public String courseWiseSales(Model model) {
        model.addAttribute("pageTitle", "Course-wise Sales Report - TechnoKraft");
        model.addAttribute("activePage", "course-wise-sales");
        return "reports/course-wise-sales";
    }

    // Fees Collection Report
    @GetMapping("/reports/fees-collection")
    public String feesCollection(Model model) {
        model.addAttribute("pageTitle", "Fees Collection Report - TechnoKraft");
        model.addAttribute("activePage", "fees-collection");
        return "reports/fees-collection";
    }
}