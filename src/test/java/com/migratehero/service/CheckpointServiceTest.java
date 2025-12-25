package com.migratehero.service;

import com.migratehero.model.EmailAccount;
import com.migratehero.model.MigrationJob;
import com.migratehero.model.SyncCheckpoint;
import com.migratehero.model.enums.DataType;
import com.migratehero.model.enums.ProviderType;
import com.migratehero.repository.SyncCheckpointRepository;
import com.migratehero.service.migration.CheckpointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckpointServiceTest {

    @Mock
    private SyncCheckpointRepository checkpointRepository;

    @InjectMocks
    private CheckpointService checkpointService;

    private MigrationJob job;
    private EmailAccount sourceAccount;

    @BeforeEach
    void setUp() {
        sourceAccount = new EmailAccount();
        sourceAccount.setId(1L);
        sourceAccount.setProvider(ProviderType.GOOGLE);

        job = new MigrationJob();
        job.setId(1L);
        job.setSourceAccount(sourceAccount);
    }

    @Test
    void getOrCreateCheckpoint_shouldReturnExistingCheckpoint() {
        SyncCheckpoint existing = SyncCheckpoint.builder()
                .id(1L)
                .job(job)
                .dataType(DataType.EMAILS)
                .build();

        when(checkpointRepository.findByJobAndDataType(job, DataType.EMAILS))
                .thenReturn(Optional.of(existing));

        SyncCheckpoint result = checkpointService.getOrCreateCheckpoint(job, DataType.EMAILS);

        assertEquals(existing, result);
        verify(checkpointRepository, never()).save(any());
    }

    @Test
    void getOrCreateCheckpoint_shouldCreateNewCheckpoint() {
        when(checkpointRepository.findByJobAndDataType(job, DataType.EMAILS))
                .thenReturn(Optional.empty());
        when(checkpointRepository.save(any(SyncCheckpoint.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SyncCheckpoint result = checkpointService.getOrCreateCheckpoint(job, DataType.EMAILS);

        assertNotNull(result);
        assertEquals(job, result.getJob());
        assertEquals(DataType.EMAILS, result.getDataType());
        verify(checkpointRepository).save(any(SyncCheckpoint.class));
    }

    @Test
    void updatePageToken_shouldUpdateCheckpoint() {
        SyncCheckpoint checkpoint = SyncCheckpoint.builder()
                .job(job)
                .dataType(DataType.EMAILS)
                .build();

        when(checkpointRepository.findByJobAndDataType(job, DataType.EMAILS))
                .thenReturn(Optional.of(checkpoint));
        when(checkpointRepository.save(any(SyncCheckpoint.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        checkpointService.updatePageToken(job, DataType.EMAILS, "next-page-token");

        assertEquals("next-page-token", checkpoint.getNextPageToken());
        assertNotNull(checkpoint.getLastSyncTime());
        verify(checkpointRepository).save(checkpoint);
    }

    @Test
    void updateSyncToken_shouldUpdateHistoryIdForGoogle() {
        sourceAccount.setProvider(ProviderType.GOOGLE);

        SyncCheckpoint checkpoint = SyncCheckpoint.builder()
                .job(job)
                .dataType(DataType.EMAILS)
                .build();

        when(checkpointRepository.findByJobAndDataType(job, DataType.EMAILS))
                .thenReturn(Optional.of(checkpoint));
        when(checkpointRepository.save(any(SyncCheckpoint.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        checkpointService.updateSyncToken(job, DataType.EMAILS, "history-123");

        assertEquals("history-123", checkpoint.getHistoryId());
        verify(checkpointRepository).save(checkpoint);
    }

    @Test
    void updateSyncToken_shouldUpdateDeltaTokenForMicrosoft() {
        sourceAccount.setProvider(ProviderType.MICROSOFT);

        SyncCheckpoint checkpoint = SyncCheckpoint.builder()
                .job(job)
                .dataType(DataType.EMAILS)
                .build();

        when(checkpointRepository.findByJobAndDataType(job, DataType.EMAILS))
                .thenReturn(Optional.of(checkpoint));
        when(checkpointRepository.save(any(SyncCheckpoint.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        checkpointService.updateSyncToken(job, DataType.EMAILS, "delta-token-abc");

        assertEquals("delta-token-abc", checkpoint.getDeltaToken());
        verify(checkpointRepository).save(checkpoint);
    }

    @Test
    void getSyncToken_shouldReturnHistoryIdForGoogle() {
        sourceAccount.setProvider(ProviderType.GOOGLE);

        SyncCheckpoint checkpoint = SyncCheckpoint.builder()
                .job(job)
                .dataType(DataType.EMAILS)
                .historyId("history-456")
                .build();

        when(checkpointRepository.findByJobAndDataType(job, DataType.EMAILS))
                .thenReturn(Optional.of(checkpoint));

        String result = checkpointService.getSyncToken(job, DataType.EMAILS);

        assertEquals("history-456", result);
    }

    @Test
    void getSyncToken_shouldReturnDeltaTokenForMicrosoft() {
        sourceAccount.setProvider(ProviderType.MICROSOFT);

        SyncCheckpoint checkpoint = SyncCheckpoint.builder()
                .job(job)
                .dataType(DataType.EMAILS)
                .deltaToken("delta-token-xyz")
                .build();

        when(checkpointRepository.findByJobAndDataType(job, DataType.EMAILS))
                .thenReturn(Optional.of(checkpoint));

        String result = checkpointService.getSyncToken(job, DataType.EMAILS);

        assertEquals("delta-token-xyz", result);
    }

    @Test
    void isInitialSyncComplete_shouldReturnTrue() {
        SyncCheckpoint checkpoint = SyncCheckpoint.builder()
                .job(job)
                .dataType(DataType.EMAILS)
                .initialSyncComplete(true)
                .build();

        when(checkpointRepository.findByJobAndDataType(job, DataType.EMAILS))
                .thenReturn(Optional.of(checkpoint));

        assertTrue(checkpointService.isInitialSyncComplete(job, DataType.EMAILS));
    }

    @Test
    void isInitialSyncComplete_shouldReturnFalse() {
        when(checkpointRepository.findByJobAndDataType(job, DataType.EMAILS))
                .thenReturn(Optional.empty());

        assertFalse(checkpointService.isInitialSyncComplete(job, DataType.EMAILS));
    }

    @Test
    void clearCheckpoints_shouldDeleteAllCheckpoints() {
        checkpointService.clearCheckpoints(job);

        verify(checkpointRepository).deleteByJob(job);
    }
}
