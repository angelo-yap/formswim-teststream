package com.formswim.teststream.workspace.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.formswim.teststream.etl.model.TestCase;
import com.formswim.teststream.etl.service.ExcelExportService;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Controller
public class WorkspaceController {

    private final ExcelExportService excelExportService;

    public WorkspaceController(ExcelExportService excelExportService) {
        this.excelExportService = excelExportService;
    }

    @GetMapping("/workspace")
    public String workspace() {
        return "workspace";
    }

    @GetMapping("/workspace/export")
    @ResponseBody
    public ResponseEntity<List<TestCase>> export() {
        List<TestCase> testCases = excelExportService.getAllTestCases();
        return ResponseEntity.ok(testCases);
    }
}