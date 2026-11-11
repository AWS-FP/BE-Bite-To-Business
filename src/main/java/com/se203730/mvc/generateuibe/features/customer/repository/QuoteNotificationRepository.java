package com.se203730.mvc.generateuibe.features.customer.repository;

import com.se203730.mvc.generateuibe.features.customer.entity.QuoteNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuoteNotificationRepository extends JpaRepository<QuoteNotification, UUID> {}
