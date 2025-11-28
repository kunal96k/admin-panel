package com.tts.sms.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "menus")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "main_menu", nullable = false, length = 100)
    private String mainMenu;

    @Column(name = "submenu", nullable = false, length = 100)
    private String submenu;

    @Column(name = "menu_url", length = 200)
    private String menuUrl;

    @Column(name = "menu_order")
    private Integer menuOrder;

    @Column(name = "is_active")
    private Boolean isActive = true;
}