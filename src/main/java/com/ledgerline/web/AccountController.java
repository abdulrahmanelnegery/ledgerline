package com.ledgerline.web;

import com.ledgerline.domain.Account;
import com.ledgerline.service.LedgerService;
import com.ledgerline.web.AccountDtos.AccountResponse;
import com.ledgerline.web.AccountDtos.BalanceResponse;
import com.ledgerline.web.AccountDtos.CreateAccountRequest;
import com.ledgerline.web.JournalDtos.LedgerResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
public class AccountController {

    private final LedgerService ledger;

    public AccountController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request,
                                                  UriComponentsBuilder uri) {
        Account account = ledger.createAccount(request.name(), request.currency(), request.type());
        URI location = uri.path("/accounts/{id}").buildAndExpand(account.getId()).toUri();
        return ResponseEntity.created(location).body(AccountResponse.of(account));
    }

    @GetMapping("/accounts/{id}/balance")
    public BalanceResponse balance(@PathVariable Long id) {
        return BalanceResponse.of(ledger.getAccount(id), ledger.balanceOf(id));
    }

    @GetMapping("/accounts/{id}/ledger")
    public LedgerResponse ledger(@PathVariable Long id) {
        return new LedgerResponse(id, ledger.ledgerOf(id));
    }
}
