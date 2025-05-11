package org.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {

    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final long timeWindowMillis;
    private final Deque<Long> requestTimestamps = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock(true);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String API_URL = "https://ismp.crpt.ru/v3/lk/documents/commissioning/contract/create";

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) throw new IllegalArgumentException("Лимит запроса должен быть положительным");
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.timeWindowMillis = timeUnit.toMillis(1);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public void createDocument(Document document, String signature) throws IOException, InterruptedException {
        throttle();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("document", document);
        requestBody.put("signature", signature);

        String json = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new IOException("API ответ с ошибкой: " + response.statusCode() + " - " + response.body());
        }
    }

    private void throttle() throws InterruptedException {
        lock.lock();
        try {
            long now = Instant.now().toEpochMilli();

            while (!requestTimestamps.isEmpty() && now - requestTimestamps.peekFirst() >= timeWindowMillis) {
                requestTimestamps.pollFirst();
            }

            if (requestTimestamps.size() >= requestLimit) {
                long waitTime = timeWindowMillis - (now - requestTimestamps.peekFirst());
                if (waitTime > 0) {
                    Thread.sleep(waitTime);
                }
                throttle(); // Рекурсивный вызов после ожидания
                return;
            }

            requestTimestamps.addLast(now);
        } finally {
            lock.unlock();
        }
    }

    // ======= Вложенные классы =======

    public static class Document {
        @JsonProperty("description")
        public Description description;

        @JsonProperty("doc_id")
        public String docId;

        @JsonProperty("doc_status")
        public String docStatus;

        @JsonProperty("doc_type")
        public String docType;

        @JsonProperty("importRequest")
        public Boolean importRequest;

        @JsonProperty("owner_inn")
        public String ownerInn;

        @JsonProperty("participant_inn")
        public String participantInn;

        @JsonProperty("producer_inn")
        public String producerInn;

        @JsonProperty("production_date")
        public String productionDate;

        @JsonProperty("production_type")
        public String productionType;

        @JsonProperty("products")
        public List<Product> products;

        @JsonProperty("reg_date")
        public String regDate;

        @JsonProperty("reg_number")
        public String regNumber;
    }

    public static class Description {
        @JsonProperty("participantInn")
        public String participantInn;
    }

    public static class Product {
        @JsonProperty("certificate_document")
        public String certificateDocument;

        @JsonProperty("certificate_document_date")
        public String certificateDocumentDate;

        @JsonProperty("certificate_document_number")
        public String certificateDocumentNumber;

        @JsonProperty("owner_inn")
        public String ownerInn;

        @JsonProperty("producer_inn")
        public String producerInn;

        @JsonProperty("production_date")
        public String productionDate;

        @JsonProperty("tnved_code")
        public String tnvedCode;

        @JsonProperty("uit_code")
        public String uitCode;

        @JsonProperty("uitu_code")
        public String uituCode;
    }
}
