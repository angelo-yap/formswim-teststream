package com.formswim.teststream.etl.controller;

import com.formswim.teststream.etl.dto.EtlResultSummary;
import com.formswim.teststream.etl.service.TestIngestionService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;





@RestController
@RequestMapping("/api/upload")
public class TestCaseController {

    private final TestIngestionService testIngestionService;




    public TestCaseController(TestIngestionService testIngestionService) {
        this.testIngestionService = testIngestionService;
    }


    /**
     * POST /api/upload
     * Accepts a multipart .xlsx file, parses it, and returns a summary.
     */
    @PostMapping
    public ResponseEntity<EtlResultSummary> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().build();
        EtlResultSummary result = testIngestionService.ingestFile(file);
        return ResponseEntity.ok(result);
    }

}
