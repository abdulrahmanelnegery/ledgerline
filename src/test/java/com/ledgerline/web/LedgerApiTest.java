package com.ledgerline.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End to end over the real Spring context and an in-memory database. No Docker
 * needed, so this always runs under {@code verify}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LedgerApiTest {

    @Autowired
    private TestRestTemplate rest;

    private long createAccount(String name, String type) {
        ResponseEntity<JsonNode> response = rest.postForEntity(
                "/accounts",
                json("{\"name\":\"" + name + "\",\"currency\":\"USD\",\"type\":\"" + type + "\"}"),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asLong();
    }

    @Test
    void postsABalancedEntryAndReadsItBack() {
        long cash = createAccount("Cash", "ASSET");
        long sales = createAccount("Sales", "REVENUE");

        ResponseEntity<JsonNode> posted = rest.postForEntity(
                "/journal-entries",
                json(entryBody(cash, "100.00", sales, "-100.00")),
                JsonNode.class);

        assertThat(posted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long entryId = posted.getBody().get("id").asLong();
        assertThat(posted.getBody().get("lines")).hasSize(2);

        ResponseEntity<JsonNode> fetched = rest.getForEntity("/journal-entries/" + entryId, JsonNode.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().get("currency").asText()).isEqualTo("USD");

        ResponseEntity<JsonNode> balance = rest.getForEntity("/accounts/" + cash + "/balance", JsonNode.class);
        assertThat(balance.getBody().get("balance").decimalValue()).isEqualByComparingTo("100.00");

        ResponseEntity<JsonNode> ledger = rest.getForEntity("/accounts/" + cash + "/ledger", JsonNode.class);
        assertThat(ledger.getBody().get("lines")).hasSize(1);
        assertThat(ledger.getBody().get("lines").get(0).get("runningBalance").decimalValue())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void rejectsAnUnbalancedEntryWith422() {
        long cash = createAccount("Cash", "ASSET");
        long sales = createAccount("Sales", "REVENUE");

        ResponseEntity<JsonNode> response = rest.postForEntity(
                "/journal-entries",
                json(entryBody(cash, "100.00", sales, "-90.00")),
                JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("error").get("code").asText()).isEqualTo("entry_not_balanced");
    }

    @Test
    void rejectsAnEntryWithFewerThanTwoLinesWith400() {
        long cash = createAccount("Cash", "ASSET");

        ResponseEntity<JsonNode> response = rest.postForEntity(
                "/journal-entries",
                json("{\"description\":\"x\",\"lines\":[{\"accountId\":" + cash
                        + ",\"amount\":\"0.00\",\"currency\":\"USD\"}]}"),
                JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void repeatingAnIdempotencyKeyReturnsTheSameEntryAndPostsOnce() {
        long cash = createAccount("Cash", "ASSET");
        long sales = createAccount("Sales", "REVENUE");
        String body = entryBody(cash, "100.00", sales, "-100.00");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "abc-123");

        ResponseEntity<JsonNode> first = rest.exchange(
                "/journal-entries", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
        ResponseEntity<JsonNode> second = rest.exchange(
                "/journal-entries", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().get("id").asLong()).isEqualTo(second.getBody().get("id").asLong());

        ResponseEntity<JsonNode> balance = rest.getForEntity("/accounts/" + cash + "/balance", JsonNode.class);
        assertThat(balance.getBody().get("balance").decimalValue()).isEqualByComparingTo("100.00");
    }

    private static String entryBody(long debitAccount, String debitAmount, long creditAccount, String creditAmount) {
        return "{\"description\":\"sale\",\"lines\":["
                + "{\"accountId\":" + debitAccount + ",\"amount\":\"" + debitAmount + "\",\"currency\":\"USD\"},"
                + "{\"accountId\":" + creditAccount + ",\"amount\":\"" + creditAmount + "\",\"currency\":\"USD\"}"
                + "]}";
    }

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
