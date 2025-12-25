package com.migratehero.dto.response;

import com.migratehero.model.MvpFolderProgress;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 文件夹迁移进度响应
 */
@Data
@Builder
public class FolderProgressResponse {

    private Long id;
    private String folderName;
    private String displayName;
    private String folderPath;
    private Long totalEmails;
    private Long migratedEmails;
    private Long failedEmails;
    private String status;
    private Integer progressPercent;
    private Instant startedAt;
    private Instant completedAt;

    public static FolderProgressResponse from(MvpFolderProgress progress) {
        return FolderProgressResponse.builder()
                .id(progress.getId())
                .folderName(progress.getFolderName())
                .displayName(progress.getDisplayName())
                .folderPath(progress.getFolderPath())
                .totalEmails(progress.getTotalEmails())
                .migratedEmails(progress.getMigratedEmails())
                .failedEmails(progress.getFailedEmails())
                .status(progress.getStatus())
                .progressPercent(progress.getProgressPercent())
                .startedAt(progress.getStartedAt())
                .completedAt(progress.getCompletedAt())
                .build();
    }
}
