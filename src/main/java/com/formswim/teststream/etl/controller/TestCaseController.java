package com.formswim.teststream.etl.controller;

import com.formswim.teststream.etl.dto.EtlResultSummary;
import com.formswim.teststream.etl.service.ExcelParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
public class TestCaseController {

    @Autowired
    private ExcelParserService excelParserService;

    /**
     * POST /api/upload
     * Accepts a multipart .xlsx file, parses it, and returns a summary.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EtlResultSummary> upload(@RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        EtlResultSummary result = excelParserService.parse(file);
        return ResponseEntity.ok(result);
    }
}
