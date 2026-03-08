package com.formswim.teststream.etl.controller;

import com.formswim.teststream.etl.dto.EtlResultSummary;
import com.formswim.teststream.etl.service.TestIngestionService;
// import com.formswim.teststream.etl.service.ExcelExportService;

// import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

//export
// import org.springframework.http.HttpHeaders;
// import org.springframework.http.MediaType;
// import org.springframework.http.ResponseEntity;

// import java.io.IOException;





@RestController
@RequestMapping("/api/upload")
public class TestCaseController {

    private final TestIngestionService testIngestionService;
    // private final ExcelExportService excelExportService;
    



    public TestCaseController(TestIngestionService testIngestionService, ExcelExportService excelExportService) {
        this.testIngestionService = testIngestionService;
        // this.excelExportService = excelExportService;
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

    // @GetMapping("/workspace/export")
    // public ResponseEntity<byte[]> export() throws IOException {
    //     byte[] xlsx = excelExportService.exportAllToXlsx();
    //     return ResponseEntity.ok()
    //             .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"testcases_export.xlsx\"")
    //             .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    //             .body(xlsx);
    // }
}
