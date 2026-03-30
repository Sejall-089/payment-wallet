package com.wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.BaseIntegrationTest;
import com.wallet.dto.request.RegisterRequest;
import com.wallet.dto.request.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.junit.jupiter.api.Disabled;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@Disabled("Run manually with Docker Desktop configured - docker context use desktop-linux")
class WalletControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String rahulToken;
    private String priyaToken;

    // register two users before each test
    @BeforeEach
    void setUpUsers() throws Exception {
        rahulToken = registerAndGetToken("Rahul", "rahul@test.com", "password123");
        priyaToken = registerAndGetToken("Priya", "priya@test.com", "password123");
    }

    // --- balance tests ---

    @Test
    void getBalance_newUser_returnsZero() throws Exception {
        mockMvc.perform(get("/api/wallet/balance")
                        .header("Authorization", "Bearer " + rahulToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.0000))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void getBalance_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/wallet/balance"))
                .andExpect(status().isUnauthorized());
    }

    // --- credit tests ---

    @Test
    void credit_addsToBalance() throws Exception {
        credit(rahulToken, "500.00");

        mockMvc.perform(get("/api/wallet/balance")
                        .header("Authorization", "Bearer " + rahulToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500.0000));
    }

    // --- transfer tests ---

    @Test
    void transfer_movesMoneyCorrectly() throws Exception {
        credit(rahulToken, "1000.00");

        TransferRequest request = new TransferRequest();
        request.setToEmail("priya@test.com");
        request.setAmount(new BigDecimal("300.00"));
        request.setDescription("test transfer");

        mockMvc.perform(post("/api/wallet/transfer")
                        .header("Authorization", "Bearer " + rahulToken)
                        .header("Idempotency-Key", "txn-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.direction").value("SENT"))
                .andExpect(jsonPath("$.counterpartyName").value("Priya"));

        // verify sender balance
        mockMvc.perform(get("/api/wallet/balance")
                        .header("Authorization", "Bearer " + rahulToken))
                .andExpect(jsonPath("$.balance").value(700.0000));

        // verify recipient balance
        mockMvc.perform(get("/api/wallet/balance")
                        .header("Authorization", "Bearer " + priyaToken))
                .andExpect(jsonPath("$.balance").value(300.0000));
    }

    @Test
    void transfer_insufficientBalance_returns400() throws Exception {
        // Rahul has 0 balance
        TransferRequest request = new TransferRequest();
        request.setToEmail("priya@test.com");
        request.setAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/wallet/transfer")
                        .header("Authorization", "Bearer " + rahulToken)
                        .header("Idempotency-Key", "txn-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Insufficient balance"));

        // verify balance unchanged — atomicity check
        mockMvc.perform(get("/api/wallet/balance")
                        .header("Authorization", "Bearer " + rahulToken))
                .andExpect(jsonPath("$.balance").value(0.0000));
    }

    @Test
    void transfer_idempotency_duplicateRequestReturnsSameTransaction()
            throws Exception {
        credit(rahulToken, "1000.00");

        TransferRequest request = new TransferRequest();
        request.setToEmail("priya@test.com");
        request.setAmount(new BigDecimal("100.00"));
        request.setDescription("idempotency test");

        // first request
        MvcResult first = mockMvc.perform(post("/api/wallet/transfer")
                        .header("Authorization", "Bearer " + rahulToken)
                        .header("Idempotency-Key", "idem-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // second request — same key
        MvcResult second = mockMvc.perform(post("/api/wallet/transfer")
                        .header("Authorization", "Bearer " + rahulToken)
                        .header("Idempotency-Key", "idem-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String firstId = objectMapper.readTree(
                        first.getResponse().getContentAsString())
                .get("transactionId").asText();

        String secondId = objectMapper.readTree(
                        second.getResponse().getContentAsString())
                .get("transactionId").asText();

        // same transaction ID — no duplicate processing
        assert firstId.equals(secondId) :
                "Duplicate request returned different transaction IDs";

        // money moved exactly once — not twice
        mockMvc.perform(get("/api/wallet/balance")
                        .header("Authorization", "Bearer " + rahulToken))
                .andExpect(jsonPath("$.balance").value(900.0000));
    }

    @Test
    void transfer_toSelf_returns400() throws Exception {
        credit(rahulToken, "500.00");

        TransferRequest request = new TransferRequest();
        request.setToEmail("rahul@test.com");
        request.setAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/wallet/transfer")
                        .header("Authorization", "Bearer " + rahulToken)
                        .header("Idempotency-Key", "txn-self-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot transfer to yourself"));
    }

    @Test
    void transfer_missingIdempotencyKey_returns400() throws Exception {
        TransferRequest request = new TransferRequest();
        request.setToEmail("priya@test.com");
        request.setAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/wallet/transfer")
                        .header("Authorization", "Bearer " + rahulToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTransactionHistory_showsCounterparty() throws Exception {
        credit(rahulToken, "500.00");

        TransferRequest request = new TransferRequest();
        request.setToEmail("priya@test.com");
        request.setAmount(new BigDecimal("200.00"));
        request.setDescription("history test");

        mockMvc.perform(post("/api/wallet/transfer")
                        .header("Authorization", "Bearer " + rahulToken)
                        .header("Idempotency-Key", "txn-hist-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // check sender's history
        mockMvc.perform(get("/api/wallet/transactions")
                        .header("Authorization", "Bearer " + rahulToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].direction").value("SENT"))
                .andExpect(jsonPath("$[0].counterpartyName").value("Priya"))
                .andExpect(jsonPath("$[0].amount").value(200.0000));

        // check recipient's history
        mockMvc.perform(get("/api/wallet/transactions")
                        .header("Authorization", "Bearer " + priyaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].direction").value("RECEIVED"))
                .andExpect(jsonPath("$[0].counterpartyName").value("Rahul"));
    }

    // --- helper methods ---

    private String registerAndGetToken(String name, String email,
                                       String password) throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName(name);
        request.setEmail(email);
        request.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(
                        result.getResponse().getContentAsString())
                .get("token").asText();
    }

    private void credit(String token, String amount) throws Exception {
        mockMvc.perform(post("/api/wallet/credit")
                        .header("Authorization", "Bearer " + token)
                        .param("amount", amount))
                .andExpect(status().isOk());
    }
}