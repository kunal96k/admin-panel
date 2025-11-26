package com.tts.sms.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("printing/certificate")
public class CertificateController {

    @GetMapping("/")
    public String certificateManagement() {
        return "printing/certificate";
    }
}
