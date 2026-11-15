package com.se203730.mvc.generateuibe.features.customer.invoice.service;

import com.se203730.mvc.generateuibe.common.exception.BusinessException;
import com.se203730.mvc.generateuibe.common.exception.ErrorCode;
import com.se203730.mvc.generateuibe.features.customer.invoice.dto.response.CustomerInvoiceResponse;
import com.se203730.mvc.generateuibe.features.customer.invoice.dto.response.OrderVatPreviewResponse;
import com.se203730.mvc.generateuibe.features.customer.invoice.repository.CustomerInvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomerInvoiceService {
    private final CustomerInvoiceRepository repository;

    public CustomerInvoiceService(CustomerInvoiceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CustomerInvoiceResponse getInvoice(UUID customerId, UUID orderId) {
        return repository.findInvoice(customerId, orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * This is an order VAT calculation snapshot for display/verification only.
     * Only an authorized e-invoicing provider may issue an official VAT e-invoice.
     */
    @Transactional(readOnly = true)
    public OrderVatPreviewResponse previewVat(UUID customerId, UUID orderId) {
        CustomerInvoiceResponse invoice = getInvoice(customerId, orderId);
        return new OrderVatPreviewResponse(
                invoice.orderId(), invoice.orderNumber(),
                invoice.vatAmount(), invoice.totalAmount(),
                repository.findVatLines(orderId),
                "Preview from order snapshot; not an issued electronic VAT invoice."
        );
    }
}
