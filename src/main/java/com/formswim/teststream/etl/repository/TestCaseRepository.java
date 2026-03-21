package com.formswim.teststream.etl.repository;

import com.formswim.teststream.etl.model.TestCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    @Query("""
            select distinct testCase
            from TestCase testCase
            left join fetch testCase.steps
            where testCase.teamKey = :teamKey
              and (:status is null or :status = '' or lower(coalesce(testCase.status, '')) = lower(:status))
              and (:component is null or :component = '' or lower(coalesce(testCase.components, '')) like lower(concat('%', :component, '%')))
              and (:tag is null or :tag = ''
                   or lower(coalesce(testCase.components, '')) like lower(concat('%', :tag, '%'))
                   or lower(coalesce(testCase.testCaseType, '')) like lower(concat('%', :tag, '%')))
              and (
                    :search is null or :search = ''
                    or lower(coalesce(testCase.workKey, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(testCase.summary, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(testCase.components, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(testCase.testCaseType, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(testCase.folder, '')) like lower(concat('%', :search, '%'))
                    or exists (
                        select 1
                        from TestStep testStep
                        where testStep.testCase = testCase
                          and lower(coalesce(testStep.stepSummary, '')) like lower(concat('%', :search, '%'))
                    )
                )
            """)
    List<TestCase> findWorkspaceCasesByFilters(@Param("teamKey") String teamKey,
                                               @Param("search") String search,
                                               @Param("status") String status,
                                               @Param("component") String component,
                                               @Param("tag") String tag);

    @Query(
        value = """
                select distinct testCase.id
                from TestCase testCase
                where testCase.teamKey = :teamKey
                  and (:status is null or :status = '' or lower(coalesce(testCase.status, '')) = lower(:status))
                  and (:component is null or :component = '' or lower(coalesce(testCase.components, '')) like lower(concat('%', :component, '%')))
                  and (:tag is null or :tag = ''
                       or lower(coalesce(testCase.components, '')) like lower(concat('%', :tag, '%'))
                       or lower(coalesce(testCase.testCaseType, '')) like lower(concat('%', :tag, '%')))
                        and (
                                :folder is null or :folder = ''
                                or lower(coalesce(testCase.folder, '')) = lower(:folder)
                                or lower(coalesce(testCase.folder, '')) like lower(concat(:folder, '/%'))
                          )
                  and (
                        :search is null or :search = ''
                        or lower(coalesce(testCase.workKey, '')) like lower(concat('%', :search, '%'))
                        or lower(coalesce(testCase.summary, '')) like lower(concat('%', :search, '%'))
                        or lower(coalesce(testCase.components, '')) like lower(concat('%', :search, '%'))
                        or lower(coalesce(testCase.testCaseType, '')) like lower(concat('%', :search, '%'))
                        or lower(coalesce(testCase.folder, '')) like lower(concat('%', :search, '%'))
                        or exists (
                            select 1
                            from TestStep testStep
                            where testStep.testCase = testCase
                              and lower(coalesce(testStep.stepSummary, '')) like lower(concat('%', :search, '%'))
                        )
                    )
                order by testCase.id desc
                """,
        countQuery = """
                select count(distinct testCase.id)
                from TestCase testCase
                where testCase.teamKey = :teamKey
                  and (:status is null or :status = '' or lower(coalesce(testCase.status, '')) = lower(:status))
                  and (:component is null or :component = '' or lower(coalesce(testCase.components, '')) like lower(concat('%', :component, '%')))
                  and (:tag is null or :tag = ''
                       or lower(coalesce(testCase.components, '')) like lower(concat('%', :tag, '%'))
                       or lower(coalesce(testCase.testCaseType, '')) like lower(concat('%', :tag, '%')))
                        and (
                                :folder is null or :folder = ''
                                or lower(coalesce(testCase.folder, '')) = lower(:folder)
                                or lower(coalesce(testCase.folder, '')) like lower(concat(:folder, '/%'))
                          )
                  and (
                        :search is null or :search = ''
                        or lower(coalesce(testCase.workKey, '')) like lower(concat('%', :search, '%'))
                        or lower(coalesce(testCase.summary, '')) like lower(concat('%', :search, '%'))
                        or lower(coalesce(testCase.components, '')) like lower(concat('%', :search, '%'))
                        or lower(coalesce(testCase.testCaseType, '')) like lower(concat('%', :search, '%'))
                        or lower(coalesce(testCase.folder, '')) like lower(concat('%', :search, '%'))
                        or exists (
                            select 1
                            from TestStep testStep
                            where testStep.testCase = testCase
                              and lower(coalesce(testStep.stepSummary, '')) like lower(concat('%', :search, '%'))
                        )
                    )
                """
    )
    Page<Long> findWorkspaceCaseIdsByFilters(@Param("teamKey") String teamKey,
                                             @Param("search") String search,
                                             @Param("status") String status,
                                             @Param("component") String component,
                                             @Param("tag") String tag,
                                             @Param("folder") String folder,
                                             Pageable pageable);

    @Query("""
            select distinct testCase
            from TestCase testCase
            left join fetch testCase.steps
            where testCase.id in :ids
            """)
    List<TestCase> findAllWithStepsByIdIn(@Param("ids") Collection<Long> ids);

    @Query("select distinct testCase from TestCase testCase left join fetch testCase.steps where testCase.teamKey = :teamKey")
    List<TestCase> findAllWithStepsByTeamKey(String teamKey);

    @Query("""
            select testCase.workKey
            from TestCase testCase
            where testCase.teamKey = :teamKey
              and testCase.workKey in :workKeys
            """)
    List<String> findOwnedWorkKeysIn(@Param("teamKey") String teamKey,
                                     @Param("workKeys") Collection<String> workKeys);

    @Query("""
            select distinct testCase.workKey
            from TestCase testCase
            where testCase.workKey in :workKeys
            """)
    List<String> findExistingWorkKeysIn(@Param("workKeys") Collection<String> workKeys);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TestCase testCase
            set testCase.folder = :targetFolder
            where testCase.teamKey = :teamKey
              and testCase.workKey in :workKeys
            """)
    int bulkMoveToFolderByTeamKeyAndWorkKeys(@Param("teamKey") String teamKey,
                                             @Param("workKeys") Collection<String> workKeys,
                                             @Param("targetFolder") String targetFolder);

    @Query("""
            select distinct testCase
            from TestCase testCase
            left join fetch testCase.steps
            where testCase.teamKey = :teamKey and testCase.workKey in :workKeys
            """)
    List<TestCase> findAllWithStepsByTeamKeyAndWorkKeyIn(String teamKey,
                                                         Collection<String> workKeys);

    @Query("""
            select distinct trim(testCase.folder)
            from TestCase testCase
            where testCase.teamKey = :teamKey
              and testCase.folder is not null
              and trim(testCase.folder) <> ''
            order by trim(testCase.folder)
            """)
    List<String> findDistinctFolderByTeamKey(String teamKey);

    @Query("""
            select distinct trim(testCase.components)
            from TestCase testCase
            where testCase.teamKey = :teamKey
              and testCase.components is not null
              and trim(testCase.components) <> ''
            order by trim(testCase.components)
            """)
    List<String> findDistinctComponentsByTeamKey(String teamKey);

    @Query("""
            select distinct trim(testCase.testCaseType)
            from TestCase testCase
            where testCase.teamKey = :teamKey
              and testCase.testCaseType is not null
              and trim(testCase.testCaseType) <> ''
            order by trim(testCase.testCaseType)
            """)
    List<String> findDistinctTestCaseTypeByTeamKey(String teamKey);

    @Query("""
            select distinct trim(testCase.status)
            from TestCase testCase
            where testCase.teamKey = :teamKey
              and testCase.status is not null
              and trim(testCase.status) <> ''
            order by trim(testCase.status)
            """)
    List<String> findDistinctStatusByTeamKey(String teamKey);

    List<TestCase> findByTeamKey(String teamKey);

    long countByTeamKey(String teamKey);

    long countByTeamKeyAndWorkKeyIn(String teamKey, Collection<String> workKeys);

    long countByWorkKeyIn(Collection<String> workKeys);

    Optional<TestCase> findByTeamKeyAndWorkKey(String teamKey, String workKey);

    boolean existsByTeamKeyAndWorkKey(String teamKey, String workKey);

    List<TestCase> findByTeamKeyAndFolder(String teamKey, String folder);

    List<TestCase> findByTeamKeyAndStatus(String teamKey, String status);
}
