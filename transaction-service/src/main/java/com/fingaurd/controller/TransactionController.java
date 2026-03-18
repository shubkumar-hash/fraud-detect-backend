package com.fingaurd.controller;

import com.fingaurd.dto.TransactionEvent;
import com.fingaurd.entity.Transaction;
import com.fingaurd.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService service;

    @PostMapping
    public ResponseEntity<Transaction> submit(@Valid @RequestBody TransactionEvent event) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(event));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<Transaction>> getByAccount(@PathVariable String accountId) {
        return ResponseEntity.ok(service.getByAccount(accountId));
    }
}
