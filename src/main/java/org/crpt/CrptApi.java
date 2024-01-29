package org.crpt;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    private static final Logger logger = LoggerFactory.getLogger(CrptApi.class);

    private final TimeUnit timeUnit;
    private final int requestLimit;

    private final Set<Long> requests;
    private final Object lock = new Object();


    private static final String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.requests = Collections.synchronizedSet(new TreeSet<>());
    }

    public String createDocument(Document document, String signature) {
        try {
            while (requests.size() >= requestLimit) {
                long diff = new Date().getTime() - getOldestRequest();

                if (diff < timeUnit.toMillis(1)) {
                    lock.wait(timeUnit.toMillis(1) - diff);
                }

                deleteOldRequests();
            }

            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Document> entity = new HttpEntity<>(document, headers);
            String url = UriComponentsBuilder.fromHttpUrl(URL).
                    queryParam("signature", signature)
                    .encode()
                    .toUriString();

            String answer = restTemplate.postForObject(url, entity, String.class);
            requests.add(new Date().getTime());

            return answer;
        } catch (Exception e) {
            logger.error("Document creation error", e);
            throw new CreateDocumentException(e);
        }
    }

    private long getOldestRequest() {
        Optional<Long> min = requests.stream().min(Long::compareTo);

        return min.orElse(0L);
    }

    private void deleteOldRequests() {
        requests.removeIf(requestTime -> new Date().getTime() - requestTime >= timeUnit.toMillis(1));
    }

    @Getter
    @RequiredArgsConstructor
    private static class Document {

        @JsonProperty("description")
        private final Description description;

        @JsonProperty("doc_type")
        private final String docType = "LP_INTRODUCE_GOODS";

        @JsonProperty("doc_id")
        private final String docId;

        @JsonProperty("doc_status")
        private final String docStatus;

        @JsonProperty("importRequest")
        private final boolean importRequest;

        @JsonProperty("owner_inn")
        private final String ownerInn;

        @JsonProperty("participant_inn")
        private final String participantInn;

        @JsonProperty("producer_inn")
        private final String producerInn;

        @JsonProperty("production_date")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private final LocalDateTime productionDate;

        @JsonProperty("production_type")
        private final String productionType;

        @JsonProperty("products")
        private final List<Product> products;

        @JsonProperty("reg_date")
        private final String regDate;

        @JsonProperty("reg_number")
        private final String regNumber;

    }

    @Getter
    @AllArgsConstructor
    private static class Description {
        @JsonProperty("participantInn")
        private final String participantInn;
    }

    @Getter
    @RequiredArgsConstructor
    private static class Product {

        @JsonProperty("certificate_document")
        private final String certificateDocument;

        @JsonProperty("certificate_document_date")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private final LocalDateTime certificateDocumentDate;

        @JsonProperty("certificate_document_number")
        private final String certificateDocumentNumber;

        @JsonProperty("owner_inn")
        private final String ownerInn;

        @JsonProperty("producer_inn")
        private final String producerInn;

        @JsonProperty("production_date")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private final LocalDateTime productionDate;

        @JsonProperty("tnved_code")
        private final String tnvedCode;

        @JsonProperty("uit_code")
        private final String uitCode;

        @JsonProperty("uitu_code")
        private final String uituCode;
    }

    private static class CreateDocumentException extends RuntimeException {

        public CreateDocumentException(Throwable throwable) {
            super(throwable);
        }

    }

}
