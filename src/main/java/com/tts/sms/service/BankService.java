package com.tts.sms.service;

import com.tts.sms.dto.BankDTO;
import com.tts.sms.model.Bank;
import com.tts.sms.repository.BankRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BankService {

    private final BankRepository bankRepository;

    @Transactional(readOnly = true)
    public Page<BankDTO> getAllBanks(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        Page<Bank> banks;
        if (search != null && !search.trim().isEmpty()) {
            banks = bankRepository.findAllActiveWithSearch(search.trim(), pageable);
        } else {
            banks = bankRepository.findAllActive(pageable);
        }

        return banks.map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public BankDTO getBankById(Long id) {
        Bank bank = bankRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bank not found with id: " + id));
        return convertToDTO(bank);
    }

    @Transactional
    public BankDTO createBank(BankDTO bankDTO) {
        if (bankRepository.existsByBankNameIgnoreCaseAndIsActiveTrue(bankDTO.getBankName())) {
            throw new RuntimeException("Bank name already exists");
        }

        Bank bank = new Bank();
        bank.setBankName(bankDTO.getBankName().trim());
        bank.setIsActive(true);

        Bank savedBank = bankRepository.save(bank);
        return convertToDTO(savedBank);
    }

    @Transactional
    public BankDTO updateBank(Long id, BankDTO bankDTO) {
        Bank bank = bankRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bank not found with id: " + id));

        if (bankRepository.existsByBankNameIgnoreCaseAndIdNotAndIsActiveTrue(
                bankDTO.getBankName(), id)) {
            throw new RuntimeException("Bank name already exists");
        }

        bank.setBankName(bankDTO.getBankName().trim());
        Bank updatedBank = bankRepository.save(bank);
        return convertToDTO(updatedBank);
    }

    @Transactional
    public void deleteBank(Long id) {
        Bank bank = bankRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bank not found with id: " + id));

        bankRepository.delete(bank);
    }

    @Transactional(readOnly = true)
    public void exportToCSV(HttpServletResponse response, String search) throws IOException {
        List<Bank> banks;

        if (search != null && !search.trim().isEmpty()) {
            Pageable pageable = Pageable.unpaged();
            banks = bankRepository.findAllActiveWithSearch(search.trim(), pageable).getContent();
        } else {
            banks = bankRepository.findAllActive(Pageable.unpaged()).getContent();
        }

        if (banks.isEmpty()) {
            throw new RuntimeException("No data available to export");
        }

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=banks_export.csv");

        PrintWriter writer = response.getWriter();
        writer.println("SR. NO.,BANK NAME,CREATED DATE,UPDATED DATE");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

        for (int i = 0; i < banks.size(); i++) {
            Bank bank = banks.get(i);
            writer.printf("%d,\"%s\",\"%s\",\"%s\"%n",
                    i + 1,
                    bank.getBankName(),
                    bank.getCreatedDate().format(formatter),
                    bank.getUpdatedDate().format(formatter));
        }

        writer.flush();
    }

    private BankDTO convertToDTO(Bank bank) {
        BankDTO dto = new BankDTO();
        dto.setId(bank.getId());
        dto.setBankName(bank.getBankName());
        dto.setIsActive(bank.getIsActive());
        return dto;
    }
}