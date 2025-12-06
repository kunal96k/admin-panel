package com.tts.sms.repository;

import com.tts.sms.model.Package;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PackageRepository extends JpaRepository<Package, Long> {

    List<Package> findByIsActiveTrue();

    Page<Package> findByIsActiveTrue(Pageable pageable);

    @Query("SELECT p FROM Package p LEFT JOIN FETCH p.courses WHERE p.isActive = true ORDER BY p.packageName ASC")
    List<Package> findByIsActiveTrueOrderByPackageNameAsc();

    Optional<Package> findByIdAndIsActiveTrue(Long id);

    boolean existsByPackageNameIgnoreCaseAndIsActiveTrue(String packageName);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
            "FROM Package p WHERE LOWER(p.packageName) = LOWER(:packageName) " +
            "AND p.id <> :id")
    boolean existsByPackageNameExcludingId(@Param("packageName") String packageName,
                                           @Param("id") Long id);

    @Query("SELECT p FROM Package p WHERE " +
            "(LOWER(p.packageName) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Package> searchPackages(@Param("searchTerm") String searchTerm, Pageable pageable);
}