package com.innowise.paymentservice.service;

import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.document.PaymentStatus;
import com.innowise.paymentservice.exception.PaymentAccessDeniedException;
import com.innowise.paymentservice.exception.PaymentNotFoundException;
import com.innowise.paymentservice.repository.PaymentRepository;
import com.innowise.paymentservice.service.dto.PaymentFilter;
import com.innowise.paymentservice.service.dto.PaymentRequest;
import com.innowise.paymentservice.service.dto.PaymentResponse;
import com.innowise.paymentservice.service.dto.PaymentSummaryResponse;
import com.innowise.paymentservice.service.impl.PaymentServiceImpl;
import com.innowise.paymentservice.service.mapper.PaymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final Instant FROM = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2024-12-31T23:59:59Z");
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Mock
    private PaymentRepository repo;

    @Mock
    private PaymentMapper mapper;

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(repo, mapper);
    }

    @Test
    @DisplayName("create: saves PENDING and returns the mapped response")
    void create_returnsPendingResponseAndSavesDocument() {
        PaymentRequest request = new PaymentRequest("order-1", AMOUNT);
        String userId = "u1";
        PaymentDocument pendingDoc = doc(null, userId);
        PaymentDocument savedDoc = doc("p1", userId);
        PaymentResponse expected = response("p1", userId);

        when(mapper.toPendingDocument(request, userId)).thenReturn(pendingDoc);
        when(repo.save(pendingDoc)).thenReturn(savedDoc);
        when(mapper.toResponse(savedDoc)).thenReturn(expected);

        PaymentResponse result = service.create(request, userId);

        assertThat(result).isEqualTo(expected);
        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        verify(repo).save(pendingDoc);
        verify(mapper).toPendingDocument(request, userId);
        verify(mapper).toResponse(savedDoc);
    }

    @Test
    @DisplayName("getById: missing payment throws PaymentNotFoundException")
    void getById_whenNotFound_throwsPaymentNotFoundException() {
        when(repo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("missing", "u1", false))
                .isInstanceOf(PaymentNotFoundException.class);

        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("getById: owner reads own payment")
    void getById_whenOwner_returnsResponse() {
        PaymentDocument payment = doc("p1", "u1");
        PaymentResponse resp = response("p1", "u1");

        when(repo.findById("p1")).thenReturn(Optional.of(payment));
        when(mapper.toResponse(payment)).thenReturn(resp);

        PaymentResponse result = service.getById("p1", "u1", false);

        assertThat(result).isEqualTo(resp);
    }

    @Test
    @DisplayName("getById: non-owner without admin throws PaymentAccessDeniedException")
    void getById_whenNonOwnerAndNotAdmin_throwsPaymentAccessDeniedException() {
        PaymentDocument payment = doc("p1", "owner-user");

        when(repo.findById("p1")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.getById("p1", "other-user", false))
                .isInstanceOf(PaymentAccessDeniedException.class);

        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("getById: admin reads another user's payment")
    void getById_whenAdmin_returnsOthersPayment() {
        PaymentDocument payment = doc("p1", "owner-user");
        PaymentResponse resp = response("p1", "owner-user");

        when(repo.findById("p1")).thenReturn(Optional.of(payment));
        when(mapper.toResponse(payment)).thenReturn(resp);

        PaymentResponse result = service.getById("p1", "other-user", true);

        assertThat(result).isEqualTo(resp);
    }

    @Test
    @DisplayName("list: non-admin is scoped to own userId and content is mapped")
    void list_whenUser_forcesCallerUserIdIgnoringFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        PaymentFilter filter = new PaymentFilter("order-1", PaymentStatus.PENDING, "someone-else");
        PaymentDocument own = doc("p1", "u1");
        PaymentResponse mapped = response("p1", "u1");
        Page<PaymentDocument> page = new PageImpl<>(List.of(own), pageable, 1);

        when(repo.findByFilters("u1", "order-1", PaymentStatus.PENDING, pageable)).thenReturn(page);
        when(mapper.toResponse(own)).thenReturn(mapped);

        Page<PaymentResponse> result = service.list(filter, "u1", false, pageable);

        assertThat(result.getContent()).containsExactly(mapped);
        verify(repo).findByFilters("u1", "order-1", PaymentStatus.PENDING, pageable);
    }

    @Test
    @DisplayName("list: admin uses the filter's userId")
    void list_whenAdmin_usesFilterUserId() {
        Pageable pageable = PageRequest.of(0, 10);
        PaymentFilter filter = new PaymentFilter(null, null, "target");
        Page<PaymentDocument> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(repo.findByFilters("target", null, null, pageable)).thenReturn(emptyPage);

        service.list(filter, "u1", true, pageable);

        verify(repo).findByFilters("target", null, null, pageable);
    }

    @Test
    @DisplayName("list: admin without filter userId lists all")
    void list_whenAdminAndNoFilterUserId_listsAll() {
        Pageable pageable = PageRequest.of(0, 10);
        PaymentFilter filter = new PaymentFilter(null, null, null);
        Page<PaymentDocument> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(repo.findByFilters(null, null, null, pageable)).thenReturn(emptyPage);

        service.list(filter, "u1", true, pageable);

        verify(repo).findByFilters(null, null, null, pageable);
    }

    @Test
    @DisplayName("userSummary: user reads own summary")
    void userSummary_whenUserRequestsOwn_returnsSummary() {
        BigDecimal total = new BigDecimal("250.00");
        when(repo.sumSuccessfulPaymentsForUser("u1", FROM, TO)).thenReturn(total);

        PaymentSummaryResponse result = service.userSummary("u1", FROM, TO, "u1", false);

        assertThat(result.userId()).isEqualTo("u1");
        assertThat(result.totalAmount()).isEqualByComparingTo(total);
        assertThat(result.from()).isEqualTo(FROM);
        assertThat(result.to()).isEqualTo(TO);
    }

    @Test
    @DisplayName("userSummary: user requesting another's summary is denied")
    void userSummary_whenUserRequestsOther_throwsPaymentAccessDeniedException() {
        assertThatThrownBy(() -> service.userSummary("u1", FROM, TO, "u2", false))
                .isInstanceOf(PaymentAccessDeniedException.class);

        verify(repo, never()).sumSuccessfulPaymentsForUser(any(), any(), any());
    }

    @Test
    @DisplayName("userSummary: admin reads any user's summary")
    void userSummary_whenAdmin_returnsAnyUserSummary() {
        BigDecimal total = new BigDecimal("500.00");
        when(repo.sumSuccessfulPaymentsForUser("u1", FROM, TO)).thenReturn(total);

        PaymentSummaryResponse result = service.userSummary("u1", FROM, TO, "admin-user", true);

        assertThat(result.userId()).isEqualTo("u1");
        assertThat(result.totalAmount()).isEqualByComparingTo(total);
        assertThat(result.from()).isEqualTo(FROM);
        assertThat(result.to()).isEqualTo(TO);
    }

    @Test
    @DisplayName("platformSummary: non-admin is denied")
    void platformSummary_whenNotAdmin_throwsPaymentAccessDeniedException() {
        assertThatThrownBy(() -> service.platformSummary(FROM, TO, false))
                .isInstanceOf(PaymentAccessDeniedException.class);

        verify(repo, never()).sumSuccessfulPaymentsForAllUsers(any(), any());
    }

    @Test
    @DisplayName("platformSummary: admin reads the platform summary")
    void platformSummary_whenAdmin_returnsPlatformSummary() {
        BigDecimal total = new BigDecimal("1000.00");
        when(repo.sumSuccessfulPaymentsForAllUsers(FROM, TO)).thenReturn(total);

        PaymentSummaryResponse result = service.platformSummary(FROM, TO, true);

        assertThat(result.userId()).isNull();
        assertThat(result.totalAmount()).isEqualByComparingTo(total);
        assertThat(result.from()).isEqualTo(FROM);
        assertThat(result.to()).isEqualTo(TO);
    }

    private static PaymentDocument doc(String id, String userId) {
        return new PaymentDocument(id, "order-1", userId, PaymentStatus.PENDING,
                AMOUNT, false, FROM, FROM);
    }

    private static PaymentResponse response(String id, String userId) {
        return new PaymentResponse(id, "order-1", userId, PaymentStatus.PENDING, AMOUNT, FROM);
    }
}
