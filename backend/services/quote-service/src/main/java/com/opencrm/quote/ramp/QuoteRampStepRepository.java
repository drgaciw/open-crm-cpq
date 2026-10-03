package com.opencrm.quote.ramp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuoteRampStepRepository extends JpaRepository<QuoteRampStep, Long> {

    List<QuoteRampStep> findByLineItemIdAndDeletedFalseOrderBySequenceAsc(Long lineItemId);
}
