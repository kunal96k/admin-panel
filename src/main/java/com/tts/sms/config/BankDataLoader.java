package com.tts.sms.config;

import com.tts.sms.model.Bank;
import com.tts.sms.repository.BankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BankDataLoader implements CommandLineRunner {

    private final BankRepository bankRepository;

    @Override
    public void run(String... args) {
        if (bankRepository.count() == 0) {
            log.info("Loading default bank data...");

            List<String> defaultBanks = Arrays.asList(
                    "THE RATNAKAR BANK LTD",
                    "THE AHMEDABAD MERCANTILE CO-OPERATIVE BANK LTD",
                    "STATE BANK OF TRAVANCORE",
                    "NUTAN NAGARIK SAHAKARI BANK LTD",
                    "NEW INDIA CO-OPERATIVE BANK LTD.",
                    "CORPORATION BANK",
                    "CITY UNION BANK LTD",
                    "CITIZENCREDIT CO-OPERATIVE BANK LTD",
                    "CITIBANK NA",
                    "CHINATRUST COMMERCIAL BANK",
                    "CATHOLIC SYRIAN BANK LTD.",
                    "CANARA BANK",
                    "CALYON BANK",
                    "BNP PARIBAS",
                    "BARCLAYS BANK PLC",
                    "BANK OF TOKYO-MITSUBISHI UFJ LTD.",
                    "BANK OF MAHARASHTRA",
                    "BANK OF BARODA",
                    "BANK OF BAHRAIN AND KUWAIT",
                    "BANK OF AMERICA",
                    "AXIS BANK",
                    "ANDHRA BANK",
                    "ALLAHABAD BANK",
                    "ABU DHABI COMMERCIAL BANK"
            );

            for (String bankName : defaultBanks) {
                Bank bank = new Bank();
                bank.setBankName(bankName);
                bank.setIsActive(true);
                bankRepository.save(bank);
            }

            log.info("Default bank data loaded successfully. Total banks: {}", defaultBanks.size());
        } else {
            log.info("Bank data already exists. Skipping default data load.");
        }
    }
}