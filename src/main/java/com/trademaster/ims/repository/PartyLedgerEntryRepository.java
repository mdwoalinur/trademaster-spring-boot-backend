package com.trademaster.ims.repository;

import com.trademaster.ims.model.PartyLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface PartyLedgerEntryRepository extends JpaRepository<PartyLedgerEntry, Long> {
    Page<PartyLedgerEntry> findByPartyTypeAndPartyIdOrderByPostedAtDesc(PartyLedgerEntry.PartyType partyType, Long partyId, Pageable pageable);
    Page<PartyLedgerEntry> findByPartyTypeAndPartyIdAndPostedAtBetweenOrderByPostedAtDesc(PartyLedgerEntry.PartyType partyType, Long partyId, LocalDateTime start, LocalDateTime end, Pageable pageable);
}
