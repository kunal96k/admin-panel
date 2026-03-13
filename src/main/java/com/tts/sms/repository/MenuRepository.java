package com.tts.sms.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tts.sms.model.Menu;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Long> {

    List<Menu> findByIsActiveTrueOrderByMenuOrderAsc();

    Page<Menu> findByIsActiveTrue(Pageable pageable);

    List<Menu> findByMainMenuOrderByMenuOrderAsc(String mainMenu);
}