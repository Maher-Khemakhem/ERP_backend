package com.example.erp;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gestion centralisée des erreurs pour TOUTE l'application.
 *
 * Transforme les exceptions internes (IllegalArgumentException,
 * IllegalStateException, nos exceptions métier comme
 * InsufficientStockException) en réponses JSON propres, au lieu de
 * laisser Spring renvoyer une stack trace brute avec un statut 500
 * générique. C'est ce qui rend l'API réellement utilisable par une UI.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    /**
     * Requête invalide côté métier — mauvais UUID, produit introuvable,
     * état incohérent, etc. La grande majorité des exceptions qu'on a
     * levées dans le domaine (IllegalArgumentException) tombent ici.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Opération invalide dans l'état actuel de l'aggregate — ex.
     * finaliser un ticket déjà complété, clôturer une session déjà
     * fermée. Sémantiquement plus proche d'un 409 Conflict que d'un
     * 400 Bad Request : la requête est bien formée, mais l'état du
     * système ne permet pas cette action maintenant.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Erreur de validation Bean Validation (@Valid sur les DTOs) —
     * regroupe tous les champs en erreur en un seul message lisible,
     * plutôt que de renvoyer la structure verbeuse par défaut de Spring.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " : " + err.getDefaultMessage())
                .reduce((a, b) -> a + " ; " + b)
                .orElse("Requête invalide");
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * Filet de sécurité pour tout le reste — bugs réels, erreurs
     * inattendues. Ne JAMAIS exposer le message d'exception brut ici
     * (pourrait fuiter des détails internes) ; on logge côté serveur
     * et on renvoie un message générique au client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        // TODO : brancher un vrai logger (SLF4J) ici une fois la
        // stratégie de logging de l'app définie.
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur inattendue est survenue");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}