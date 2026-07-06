package com.oriole.wisepen.note.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.note.api.domain.base.NoteSnapshotBase;
import com.oriole.wisepen.note.api.domain.dto.res.NoteSearchTextResponse;
import com.oriole.wisepen.note.api.domain.dto.res.NoteSnapshotResponse;
import com.oriole.wisepen.note.api.domain.dto.res.NoteVersionInfoResponse;
import com.oriole.wisepen.note.domain.entity.NoteContentEntity;
import com.oriole.wisepen.note.domain.entity.NoteInfoEntity;
import com.oriole.wisepen.note.domain.entity.NoteVersionEntity;
import com.oriole.wisepen.note.api.domain.enums.VersionType;
import com.oriole.wisepen.note.exception.NoteError;
import com.oriole.wisepen.note.repository.NoteContentRepository;
import com.oriole.wisepen.note.repository.NoteInfoRepository;
import com.oriole.wisepen.note.repository.NoteVersionRepository;
import com.oriole.wisepen.note.service.INoteVersionService;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.Binary;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteVersionServiceImpl implements INoteVersionService {

    private final NoteVersionRepository noteVersionRepository;
    private final NoteInfoRepository noteInfoRepository;
    private final NoteContentRepository noteContentRepository;

    @Override
    public void createVersion(NoteSnapshotBase msg, List<Long> authors, ResourceType resourceType) {
        NoteInfoEntity noteInfo = noteInfoRepository.findByResourceId(msg.getResourceId())
                .orElseThrow(() -> new ServiceException(NoteError.NOTE_NOT_FOUND));
        if (noteInfo.getResourceType() != resourceType) {
            throw new ServiceException(NoteError.CANNOT_SUPPORT_NOTE_RESOURCE_TYPE);
        }

        NoteVersionEntity noteVersionEntity = NoteVersionEntity.builder()
                .type(msg.getType())
                .data(new Binary(Base64.getDecoder().decode(msg.getData())))
                .createdBy(authors)
                .build();
        BeanUtils.copyProperties(msg, noteVersionEntity, "type","data");
        noteVersionRepository.save(noteVersionEntity);

        // 更新最后修改时间
        noteInfo.setUpdateTime(LocalDateTime.now());
        noteInfo.setVersion(noteVersionEntity.getVersion());
        if (authors != null && !authors.isEmpty()) {
            // 将现有的作者和当前的作者合并，利用 CollUtil.distinct 自动去重
            List<Long> existing = noteInfo.getAuthors() == null ? CollUtil.newArrayList() : noteInfo.getAuthors();
            existing.addAll(authors);
            noteInfo.setAuthors(CollUtil.distinct(existing));
        }
        // 如果是 FULL 快照，顺便更新纯文本供 ES/全文检索使用
        if (VersionType.FULL == msg.getType() && msg.getPlainText() != null) {
            noteContentRepository.save(NoteContentEntity.builder()
                    .resourceId(noteVersionEntity.getResourceId())
                    .version(noteVersionEntity.getVersion())
                    .rawText(msg.getPlainText())
                    .build());
        }
        noteInfoRepository.save(noteInfo);
    }

    @Override
    public NoteSnapshotResponse getLatestVersion(String resourceId) {
        NoteInfoEntity noteInfoEntity = noteInfoRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(NoteError.NOTE_NOT_FOUND));
        // 获取最后一个Full版本
        Optional<NoteVersionEntity> latestFullVersionEntity = noteVersionRepository
                .findFirstByResourceIdAndTypeOrderByVersionDesc(resourceId, VersionType.FULL);
        // 完整版本的版本号
        Integer latestFullVersion = latestFullVersionEntity.map(NoteVersionEntity::getVersion).orElse(0);
        // 获取在该 Full 版本之后的所有 DELTA 版本
        List<NoteVersionEntity> deltaVersionEntityList = noteVersionRepository
                .findByResourceIdAndVersionGreaterThanAndTypeOrderByVersionAsc(resourceId, latestFullVersion, VersionType.DELTA);
        // 处理增量数据
        List<String> deltasBase64 = deltaVersionEntityList.stream()
                .map(e -> Base64.getEncoder().encodeToString(e.getData().getData()))
                .toList();
        // 获取当前版本号
        Integer currentVersion = latestFullVersion;
        if (!deltaVersionEntityList.isEmpty()) {
            currentVersion = deltaVersionEntityList.getLast().getVersion();
        }
        // 处理全量数据
        String fullSnapshotBase64 = latestFullVersionEntity
                .map(e -> Base64.getEncoder().encodeToString(e.getData().getData()))
                .orElse(null);
        return NoteSnapshotResponse.builder()
                .resourceId(resourceId)
                .fullSnapshot(fullSnapshotBase64)
                .version(currentVersion)
                .deltas(deltasBase64.isEmpty() ? null : deltasBase64)
                .build();
    }

    @Override
    public NoteSearchTextResponse getSearchText(String resourceId, Integer targetVersion) {
        NoteVersionEntity versionEntity = noteVersionRepository.findFirstByResourceIdAndTypeAndVersionLessThanEqualOrderByVersionDesc(resourceId, VersionType.FULL, targetVersion)
                .orElseThrow(() -> new ServiceException(NoteError.NOTE_NOT_FOUND));
        NoteContentEntity contentEntity = noteContentRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(NoteError.NOTE_NOT_FOUND));
        String searchText = contentEntity.getRawText();
        return NoteSearchTextResponse.builder()
                .resourceId(resourceId)
                .version(versionEntity.getVersion())
                .searchText(searchText)
                .build();
    }

    @Override
    public void deleteAllVersionsByResourceIds(List<String> resourceIds) {
        noteVersionRepository.deleteByResourceIdIn(resourceIds);
    }

    @Override
    public PageR<NoteVersionInfoResponse> listVersions(String resourceId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size);
        Page<NoteVersionEntity> entityPage = noteVersionRepository.findByResourceIdOrderByVersionDesc(resourceId, pageable);

        PageR<NoteVersionInfoResponse> pageR = new PageR<>(entityPage.getTotalElements(), page, size);

        List<NoteVersionInfoResponse> responses = entityPage.getContent().stream().map(entity -> {
            NoteVersionInfoResponse response = new NoteVersionInfoResponse();
            BeanUtils.copyProperties(entity, response);
            return response;
        }).toList();

        pageR.addAll(responses);
        return pageR;
    }
}
