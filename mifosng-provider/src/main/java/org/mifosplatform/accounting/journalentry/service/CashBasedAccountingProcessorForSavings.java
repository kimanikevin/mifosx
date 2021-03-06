/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mifosplatform.accounting.journalentry.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.mifosplatform.accounting.closure.domain.GLClosure;
import org.mifosplatform.accounting.common.AccountingConstants.CASH_ACCOUNTS_FOR_SAVINGS;
import org.mifosplatform.accounting.journalentry.data.ChargePaymentDTO;
import org.mifosplatform.accounting.journalentry.data.SavingsDTO;
import org.mifosplatform.accounting.journalentry.data.SavingsTransactionDTO;
import org.mifosplatform.organisation.office.domain.Office;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CashBasedAccountingProcessorForSavings implements AccountingProcessorForSavings {

    private final AccountingProcessorHelper helper;

    @Autowired
    public CashBasedAccountingProcessorForSavings(final AccountingProcessorHelper accountingProcessorHelper) {
        this.helper = accountingProcessorHelper;
    }

    @Override
    public void createJournalEntriesForSavings(final SavingsDTO savingsDTO) {
        final GLClosure latestGLClosure = this.helper.getLatestClosureByBranch(savingsDTO.getOfficeId());
        final Long savingsProductId = savingsDTO.getSavingsProductId();
        final Long savingsId = savingsDTO.getSavingsId();
        final String currencyCode = savingsDTO.getCurrencyCode();
        for (final SavingsTransactionDTO savingsTransactionDTO : savingsDTO.getNewSavingsTransactions()) {
            final Date transactionDate = savingsTransactionDTO.getTransactionDate();
            final String transactionId = savingsTransactionDTO.getTransactionId();
            final Office office = this.helper.getOfficeById(savingsTransactionDTO.getOfficeId());
            final Long paymentTypeId = savingsTransactionDTO.getPaymentTypeId();
            final boolean isReversal = savingsTransactionDTO.isReversed();
            final BigDecimal amount = savingsTransactionDTO.getAmount();
            final BigDecimal overdraftAmount = savingsTransactionDTO.getOverdraftAmount();
            final List<ChargePaymentDTO> feePayments = savingsTransactionDTO.getFeePayments();
            final List<ChargePaymentDTO> penaltyPayments = savingsTransactionDTO.getPenaltyPayments();

            this.helper.checkForBranchClosures(latestGLClosure, transactionDate);

            if (savingsTransactionDTO.getTransactionType().isWithdrawal() && savingsTransactionDTO.isOverdraftTransaction()) {
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        CASH_ACCOUNTS_FOR_SAVINGS.OVERDRAFT_PORTFOLIO_CONTROL, CASH_ACCOUNTS_FOR_SAVINGS.SAVINGS_REFERENCE,
                        savingsProductId, paymentTypeId, savingsId, transactionId, transactionDate, overdraftAmount, isReversal);
                if (amount.subtract(overdraftAmount).compareTo(BigDecimal.ZERO) == 1) {
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                            CASH_ACCOUNTS_FOR_SAVINGS.SAVINGS_CONTROL, CASH_ACCOUNTS_FOR_SAVINGS.SAVINGS_REFERENCE, savingsProductId,
                            paymentTypeId, savingsId, transactionId, transactionDate, amount.subtract(overdraftAmount), isReversal);
                }
            } else if (savingsTransactionDTO.getTransactionType().isDeposit() && savingsTransactionDTO.isOverdraftTransaction()) {
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        CASH_ACCOUNTS_FOR_SAVINGS.SAVINGS_REFERENCE, CASH_ACCOUNTS_FOR_SAVINGS.OVERDRAFT_PORTFOLIO_CONTROL,
                        savingsProductId, paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal);
                if (amount.subtract(overdraftAmount).compareTo(BigDecimal.ZERO) == 1) {
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                            CASH_ACCOUNTS_FOR_SAVINGS.SAVINGS_REFERENCE, CASH_ACCOUNTS_FOR_SAVINGS.SAVINGS_CONTROL, savingsProductId,
                            paymentTypeId, savingsId, transactionId, transactionDate, amount.subtract(overdraftAmount), isReversal);
                }
            }

            /** Handle Deposits and reversals of deposits **/
            else if (savingsTransactionDTO.getTransactionType().isDeposit()) {
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        CASH_ACCOUNTS_FOR_SAVINGS.SAVINGS_REFERENCE, CASH_ACCOUNTS_FOR_SAVINGS.SAVINGS_CONTROL, savingsProductId,
                        paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal);
            }

            /** Handle withdrawals and reversals of withdrawals **/
            else if (savingsTransactionDTO.getTransactionType().isWithdrawal()) {
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        CASH_ACCOUNTS_FOR_SAVINGS.SAVINGS_CONTROL, CASH_ACCOUNTS_FOR_SAVINGS.SAVINGS_REFERENCE, savingsProductId,
                        paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal);
            }

            /**
             * Handle Interest Applications and reversals of Interest
             * Applications
             **/
            else if (savingsTransactionDTO.getTransactionType().isInterestPosting()) {
                //Post journal entry if earned interest amount is greater than zero
                if (savingsTransactionDTO.getAmount().compareTo(BigDecimal.ZERO) == 1) {
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                            CASH_ACCOUNTS_FOR_SAVINGS.INTEREST_ON_SAVINGS, CASH_ACCOUNTS_FOR_SAVINGS.SAVINGS_CONTROL, savingsProductId,
                            paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal);
                }
            }

            /** Handle Fees Deductions and reversals of Fees Deductions **/
            else if (savingsTransactionDTO.getTransactionType().isFeeDeduction()) {
                // Is the Charge a penalty?
                if (penaltyPayments.size() > 0) {
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavingsCharges(office, currencyCode,
                            CASH_ACCOUNTS_FOR_SAVINGS.SAVINGS_CONTROL, CASH_ACCOUNTS_FOR_SAVINGS.INCOME_FROM_PENALTIES, savingsProductId,
                            paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal, penaltyPayments);
                } else {
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavingsCharges(office, currencyCode,
                            CASH_ACCOUNTS_FOR_SAVINGS.SAVINGS_CONTROL, CASH_ACCOUNTS_FOR_SAVINGS.INCOME_FROM_FEES, savingsProductId,
                            paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal, feePayments);
                }
            }

            /** Handle Transfers proposal **/
            else if (savingsTransactionDTO.getTransactionType().isInitiateTransfer()) {
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        CASH_ACCOUNTS_FOR_SAVINGS.SAVINGS_CONTROL, CASH_ACCOUNTS_FOR_SAVINGS.TRANSFERS_SUSPENSE, savingsProductId,
                        paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal);
            }

            /** Handle Transfer Withdrawal or Acceptance **/
            else if (savingsTransactionDTO.getTransactionType().isWithdrawTransfer()
                    || savingsTransactionDTO.getTransactionType().isApproveTransfer()) {
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        CASH_ACCOUNTS_FOR_SAVINGS.TRANSFERS_SUSPENSE, CASH_ACCOUNTS_FOR_SAVINGS.SAVINGS_CONTROL, savingsProductId,
                        paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal);
            }

            /** overdraft **/
            else if (savingsTransactionDTO.getTransactionType().isOverdraftInterest()) {
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        CASH_ACCOUNTS_FOR_SAVINGS.SAVINGS_REFERENCE, CASH_ACCOUNTS_FOR_SAVINGS.INCOME_FROM_INTEREST, savingsProductId,
                        paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal);
            } else if (savingsTransactionDTO.getTransactionType().isWrittenoff()) {
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        CASH_ACCOUNTS_FOR_SAVINGS.LOSSES_WRITTEN_OFF, CASH_ACCOUNTS_FOR_SAVINGS.OVERDRAFT_PORTFOLIO_CONTROL,
                        savingsProductId, paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal);
            } else if (savingsTransactionDTO.getTransactionType().isOverdraftFee()) {
                this.helper.createCashBasedJournalEntriesAndReversalsForSavingsCharges(office, currencyCode,
                        CASH_ACCOUNTS_FOR_SAVINGS.SAVINGS_REFERENCE, CASH_ACCOUNTS_FOR_SAVINGS.INCOME_FROM_FEES, savingsProductId,
                        paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal, feePayments);
            }
        }
    }
}
