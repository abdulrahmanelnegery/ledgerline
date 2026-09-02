package com.ledgerline.web;

import com.ledgerline.domain.Account;
import com.ledgerline.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/** Request and response shapes for the account endpoints. */
final class AccountDtos {

    private AccountDtos() {
    }

    record CreateAccountRequest(
            @NotBlank String name,
            @NotNull @Pattern(regexp = "[A-Z]{3}", message = "currency must be an ISO 4217 alpha-3 code") String currency,
            @NotNull AccountType type) {
    }

    record AccountResponse(Long id, String name, String currency, AccountType type) {
        static AccountResponse of(Account account) {
            return new AccountResponse(
                    account.getId(), account.getName(), account.getCurrency(), account.getType());
        }
    }

    record BalanceResponse(Long accountId, String currency, BigDecimal balance) {
        static BalanceResponse of(Account account, BigDecimal balance) {
            return new BalanceResponse(account.getId(), account.getCurrency(), balance);
        }
    }
}
