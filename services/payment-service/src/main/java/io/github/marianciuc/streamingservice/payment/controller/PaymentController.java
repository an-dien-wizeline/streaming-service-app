package io.github.marianciuc.streamingservice.payment.controller;

import io.github.marianciuc.streamingservice.payment.dto.common.AddressDto;
import io.github.marianciuc.streamingservice.payment.dto.common.CardHolderDto;
import io.github.marianciuc.streamingservice.payment.dto.requests.CreateCartHolderRequest;
import io.github.marianciuc.streamingservice.payment.dto.requests.UpdateCardHolderRequest;
import io.github.marianciuc.streamingservice.payment.service.AddressService;
import io.github.marianciuc.streamingservice.payment.service.CardHolderService;
import io.github.marianciuc.streamingservice.payment.service.UserService;
import io.github.marianciuc.streamingservice.payment.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final CardHolderService cardHolderService;
    private final AddressService addressService;

    @PostMapping("/card-holder")
    public ResponseEntity<CardHolderDto> createCardHolder(@Valid @RequestBody CreateCartHolderRequest request) {
        return ResponseEntity.ok(cardHolderService.createCardHolder(request));
    }

    @PutMapping("/card-holder")
    public ResponseEntity<Void> updateCardHolder(@RequestBody UpdateCardHolderRequest request) {
        cardHolderService.updateCardHolder(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/address")
    public ResponseEntity<Void> updateAddress(@RequestBody AddressDto request) {
        addressService.updateAddress(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/payment-method")
    public ResponseEntity<Void> updatePaymentMethod(@RequestParam("token") String token) {
        UUID authenticatedUserId = SecurityUtils.extractJwtUserPrincipals().getId();
        cardHolderService.updatePaymentMethodForUser(token, authenticatedUserId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/card-holder")
    public ResponseEntity<CardHolderDto> getCardHolder(@RequestParam(value = "cardHolderId", required = false) UUID cardHolderId) {
        UUID authenticatedUserId = SecurityUtils.extractJwtUserPrincipals().getId();
        CardHolderDto cardHolder = cardHolderService.findCardHolder(cardHolderId);
        if (!cardHolder.getUserId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("You do not have permission to access this card holder");
        }
        return ResponseEntity.ok(cardHolder);
    }
}
