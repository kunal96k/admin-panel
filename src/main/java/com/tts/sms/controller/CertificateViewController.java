package com.tts.sms.controller;

import com.tts.sms.dto.CertificateDTO;
import com.tts.sms.service.CertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/certificates")
@RequiredArgsConstructor
@Slf4j
public class CertificateViewController {

    private final CertificateService certificateService;

    @GetMapping("/view/{id}")
    public String viewCertificate(@PathVariable Long id, Model model) {
        try {
            CertificateDTO certificate = certificateService.getCertificateById(id);

            if (!"Issued".equals(certificate.getStatus())) {
                model.addAttribute("error", "Certificate has not been issued yet.");
                return "error";
            }

            model.addAttribute("certificate", certificate);
            model.addAttribute("studentName", certificate.getStudentName());
            model.addAttribute("courseName", certificate.getCourseName());
            model.addAttribute("certificateNo", certificate.getCertificateNo());
            model.addAttribute("grade", certificate.getGrade());
            model.addAttribute("issueDate", certificate.getIssueDate());
            model.addAttribute("batch", certificate.getBatch());
            model.addAttribute("courseFromDate", certificate.getCourseFromDate());
            model.addAttribute("courseToDate", certificate.getCourseToDate());

            return "printing/view-certificate";

        } catch (RuntimeException e) {
            log.error("Certificate not found: {}", id, e);
            model.addAttribute("error", "Certificate not found.");
            return "error";
        }
    }

    @GetMapping("/print/{id}")
    public String printCertificate(@PathVariable Long id, Model model) {
        return viewCertificate(id, model); // Same view for both
    }
}