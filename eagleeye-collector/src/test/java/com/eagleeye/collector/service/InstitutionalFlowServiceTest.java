package com.eagleeye.collector.service;

import com.eagleeye.collector.twse.InstitutionalFlowParser;
import com.eagleeye.collector.twse.TwseClient;
import com.eagleeye.domain.entity.InstitutionalFlow;
import com.eagleeye.domain.repository.InstitutionalFlowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstitutionalFlowServiceTest {

    @Mock private TwseClient twseClient;
    @Mock private InstitutionalFlowParser flowParser;
    @Mock private InstitutionalFlowRepository repository;

    private InstitutionalFlowService service;

    private static final LocalDate DATE = LocalDate.of(2026, 3, 19);
    private static final String FLOW_JSON = "{\"stat\":\"OK\",\"tables\":[{\"data\":[]}]}";

    @BeforeEach
    void setUp() {
        service = new InstitutionalFlowService(twseClient, flowParser, repository);
    }

    @Test
    void collectDate_success_savesAndReturnsCollected() {
        InstitutionalFlow flow = new InstitutionalFlow(DATE);
        when(twseClient.fetchInstitutionalFlowJson(DATE)).thenReturn(FLOW_JSON);
        when(flowParser.parse(FLOW_JSON, DATE)).thenReturn(flow);
        when(repository.findByTradeDate(DATE)).thenReturn(Optional.empty());

        InstitutionalFlowResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.COLLECTED);
        assertThat(result.tradeDate()).isEqualTo(DATE);
        verify(repository).save(any(InstitutionalFlow.class));
    }

    @Test
    void collectDate_noData_returnsNoData() {
        when(twseClient.fetchInstitutionalFlowJson(DATE)).thenReturn("{\"stat\":\"NO DATA\"}");
        when(flowParser.parse(any(), eq(DATE))).thenReturn(null);

        InstitutionalFlowResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.NO_DATA);
        verify(repository, never()).save(any());
    }

    @Test
    void collectDate_clientThrows_returnsError() {
        when(twseClient.fetchInstitutionalFlowJson(DATE)).thenThrow(new RuntimeException("timeout"));

        InstitutionalFlowResult result = service.collectDate(DATE);

        assertThat(result.status()).isEqualTo(CollectionStatus.ERROR);
        assertThat(result.errorMessage()).contains("timeout");
        verify(repository, never()).save(any());
    }

    @Test
    void collectDate_existingRecord_upserts() {
        InstitutionalFlow existing = new InstitutionalFlow(DATE);
        InstitutionalFlow parsed   = new InstitutionalFlow(DATE);
        parsed.setForeignBuy(100_000_000_000L);
        parsed.setForeignNet(-5_000_000_000L);

        when(twseClient.fetchInstitutionalFlowJson(DATE)).thenReturn(FLOW_JSON);
        when(flowParser.parse(FLOW_JSON, DATE)).thenReturn(parsed);
        when(repository.findByTradeDate(DATE)).thenReturn(Optional.of(existing));

        service.collectDate(DATE);

        ArgumentCaptor<InstitutionalFlow> captor = ArgumentCaptor.forClass(InstitutionalFlow.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getForeignBuy()).isEqualTo(100_000_000_000L);
        assertThat(captor.getValue().getForeignNet()).isEqualTo(-5_000_000_000L);
    }
}
