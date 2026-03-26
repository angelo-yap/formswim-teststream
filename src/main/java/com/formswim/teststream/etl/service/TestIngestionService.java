package com.formswim.teststream.etl.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.formswim.teststream.etl.dto.EtlResultSummary;
import com.formswim.teststream.etl.dto.ReviewCaseSnapshot;
import com.formswim.teststream.etl.dto.ReviewConflictCandidate;
import com.formswim.teststream.etl.model.TestCase;
import com.formswim.teststream.etl.model.UploadHistory;
import com.formswim.teststream.etl.model.UploadReviewSession;
import com.formswim.teststream.etl.repository.TestCaseRepository;
import com.formswim.teststream.etl.repository.UploadHistoryRepository;
import com.formswim.teststream.etl.repository.UploadReviewSessionRepository;

@Service
public class TestIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TestIngestionService.class);

    private final ExcelParserService excelParserService;
    private final CsvParserService csvParserService;
    private final TestCaseRepository testCaseRepository;
    private final UploadHistoryRepository uploadHistoryRepository;
    private final UploadReviewSessionRepository uploadReviewSessionRepository;
    private final FileHashService fileHashService;
    private final UploadDiffService uploadDiffService;
    private final UploadReviewService uploadReviewService;
    private final TagResolutionService tagResolutionService;
    private final TransactionTemplate transactionTemplate;

    public TestIngestionService(ExcelParserService excelParserService,
                                CsvParserService csvParserService,
                                TestCaseRepository testCaseRepository,
                                UploadHistoryRepository uploadHistoryRepository,
                                UploadReviewSessionRepository uploadReviewSessionRepository,
                                FileHashService fileHashService,
                                UploadDiffService uploadDiffService,
                                UploadReviewService uploadReviewService,
                                TagResolutionService tagResolutionService,
                                PlatformTransactionManager transactionManager) {
        this.excelParserService = excelParserService;
        this.csvParserService = csvParserService;
        this.testCaseRepository = testCaseRepository;
        this.uploadHistoryRepository = uploadHistoryRepository;
        this.uploadReviewSessionRepository = uploadReviewSessionRepository;
        this.fileHashService = fileHashService;
        this.uploadDiffService = uploadDiffService;
        this.uploadReviewService = uploadReviewService;
        this.tagResolutionService = tagResolutionService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public EtlResultSummary ingestFile(MultipartFile file, String teamKey) {
        try {
            EtlResultSummary result = transactionTemplate.execute(status -> ingestFileCore(file, teamKey));
            return result == null ? new EtlResultSummary(0, 0, List.of("Import failed."), List.of()) : result;
        } catch (DataIntegrityViolationException exception) {
            log.error("Upload failed due to data integrity violation (teamKey={})", teamKey, exception);
            EtlResultSummary result = new EtlResultSummary(0, 0, List.of("Import failed due to a data constraint."), List.of());
            result.setMessage("Import failed.");
            return result;
        } catch (Exception exception) {
            log.error("Upload failed unexpectedly (teamKey={})", teamKey, exception);
            EtlResultSummary result = new EtlResultSummary(0, 0, List.of("An unexpected error occurred."), List.of());
            result.setMessage("Import failed.");
            return result;
        }
    }

    private EtlResultSummary ingestFileCore(MultipartFile file, String teamKey) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();

        if (teamKey == null || teamKey.isBlank()) {
            return new EtlResultSummary(0, 0, List.of("A valid team is required."), List.of());
        }

        if (file.isEmpty()) {
            return new EtlResultSummary(0, 0, List.of("File is empty."), List.of());
        }
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".csv")) {
            return new EtlResultSummary(0, 0, List.of("Only .xlsx and .csv files are supported."), List.of());
        }

        String fileHash;
        try {
            fileHash = fileHashService.sha256(file);
        } catch (IllegalStateException exception) {
            return new EtlResultSummary(0, 0, List.of("Failed to hash uploaded file."), List.of());
        }

        if (uploadHistoryRepository.existsByTeamKeyAndFileHash(teamKey, fileHash)
            || uploadReviewSessionRepository.existsByTeamKeyAndFileHashAndStatus(teamKey, fileHash, UploadReviewSession.STATUS_OPEN)) {
            EtlResultSummary result = new EtlResultSummary(0, 0, List.of(), List.of());
            result.setExactDuplicateFile(true);
            result.setMessage("This exact file was already uploaded for your workspace.");
            result.addError("This exact file was already uploaded for your workspace.");
            return result;
        }

        EtlResultSummary parsed = filename.endsWith(".csv")
            ? csvParserService.parse(file, teamKey)
            : excelParserService.parse(file, teamKey);

        if (!parsed.getErrors().isEmpty() || parsed.getTestCases().isEmpty()) {
            return parsed;
        }

        List<TestCase> parsedCases = parsed.getTestCases();
        Map<String, Long> uploadWorkKeyCounts = parsedCases.stream()
            .collect(Collectors.groupingBy(TestCase::getWorkKey, LinkedHashMap::new, Collectors.counting()));
        List<String> repeatedUploadKeys = uploadWorkKeyCounts.entrySet().stream()
            .filter(entry -> entry.getValue() > 1)
            .map(Map.Entry::getKey)
            .toList();
        if (!repeatedUploadKeys.isEmpty()) {
            parsed.addError("Upload contains duplicate workKey values: " + String.join(", ", repeatedUploadKeys));
            return parsed;
        }

        Map<String, TestCase> existingByWorkKey = testCaseRepository
            .findAllWithStepsByTeamKeyAndWorkKeyIn(teamKey, parsedCases.stream().map(TestCase::getWorkKey).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(TestCase::getWorkKey, testCase -> testCase, (left, right) -> left, LinkedHashMap::new));

        List<TestCase> newCasesToSave = new ArrayList<>();
        List<ReviewCaseSnapshot> stagedNewCases = new ArrayList<>();
        List<ReviewConflictCandidate> changedConflicts = new ArrayList<>();
        int unchangedDuplicates = 0;

        for (TestCase testCase : parsedCases) {
            TestCase existing = existingByWorkKey.get(testCase.getWorkKey());
            if (existing == null) {
                tagResolutionService.resolveTagsFromImplicitFields(teamKey, testCase.getTestCaseType(), testCase.getComponents())
                    .forEach(testCase::addTag);
                newCasesToSave.add(testCase);
                stagedNewCases.add(uploadDiffService.toSnapshot(testCase));
                continue;
            }

            if (uploadDiffService.isEquivalent(existing, testCase)) {
                unchangedDuplicates++;
                continue;
            }

            changedConflicts.add(uploadDiffService.buildConflict(existing, testCase));
        }

        parsed.setDuplicateUnchangedCount(unchangedDuplicates);
        parsed.setDuplicateChangedCount(changedConflicts.size());
        parsed.setStagedNewCount(stagedNewCases.size());

        if (changedConflicts.isEmpty()) {
            testCaseRepository.saveAll(newCasesToSave);
            uploadHistoryRepository.save(new UploadHistory(teamKey, file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename(), fileHash));
            parsed.setImportedCount(newCasesToSave.size());
            parsed.setMessage(buildDirectImportMessage(newCasesToSave.size(), unchangedDuplicates));
            return parsed;
        }

        UploadReviewSession session = uploadReviewService.createReviewSession(
            teamKey,
            file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename(),
            fileHash,
            parsed.getTestCasesParsed(),
            parsed.getTotalStepsParsed(),
            stagedNewCases,
            unchangedDuplicates,
            changedConflicts
        );

        parsed.setReviewRequired(true);
        parsed.setReviewSessionId(session.getId());
        parsed.setReviewUrl("/workspace/import/review/" + session.getId());
        parsed.setMessage("Upload needs review before saving. " + stagedNewCases.size() + " new test case(s) staged and " + changedConflicts.size() + " duplicate conflict(s) found.");
        return parsed;
    }

    private String buildDirectImportMessage(int importedCount, int unchangedDuplicates) {
        if (unchangedDuplicates > 0) {
            return "Imported " + importedCount + " new test case(s). Skipped " + unchangedDuplicates + " unchanged duplicate(s).";
        }
        return "Imported " + importedCount + " test case(s).";
    }
}
