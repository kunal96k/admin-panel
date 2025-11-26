package com.tts.sms.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for Course Management Page
 */
@Controller
@RequestMapping("/master/course")
public class CourseController {

    // Course Master
    @GetMapping("/")
    public String course() {
        return "master/course";
    }
}