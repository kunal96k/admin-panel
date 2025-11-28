package com.tts.sms.repository;

import com.tts.sms.model.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Long> {

    List<Menu> findByIsActiveTrueOrderByMenuOrderAsc();

    List<Menu> findByMainMenuOrderByMenuOrderAsc(String mainMenu);
}