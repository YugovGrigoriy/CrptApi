package ru.edu;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CrptApi {
    private final HttpClient httpClient;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;
    private final ObjectMapper objectMapper;
    private String apiUrl = "http://localhost:8080/";
    private final String authToken;

    public CrptApi(TimeUnit timeUnit, int requestLimit, String authToken) {
        this.httpClient = HttpClient.newHttpClient();
        this.semaphore = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.objectMapper = new ObjectMapper();
        this.authToken = authToken;

        scheduler.scheduleAtFixedRate(() -> {
            semaphore.release(requestLimit - semaphore.availablePermits());
        }, 0, 1, timeUnit);
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public CompletableFuture<HttpResponse<String>> createDocument(Document document, String signature) throws InterruptedException, IOException {
        semaphore.acquire();
        try {
            String json = objectMapper.writeValueAsString(document);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            // Асинхронный запрос
            System.out.println("перед отправкой");
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, throwable) -> semaphore.release());
        } catch (IOException e) {
            semaphore.release();
            throw e;
        }
    }

    public static class Document {
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type = "LP_INTRODUCE_GOODS";
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public Product[] products;
        public String reg_date;
        public String reg_number;

        public static class Description {
            public String participantInn;
        }

        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;
        }
    }

    public static void main(String[] args) {
        String authToken = "your_auth_token_here";
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5, authToken);
        Document document = new Document();
        // Заполнение документа необходимыми данными

        try {
            CompletableFuture<HttpResponse<String>> responseFuture = api.createDocument(document, "signature");
            responseFuture.thenAccept(response -> {
                System.out.println("Response: " + response.body());

            }).exceptionally(ex -> {
                ex.printStackTrace();
                return null;
            });
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }
}
