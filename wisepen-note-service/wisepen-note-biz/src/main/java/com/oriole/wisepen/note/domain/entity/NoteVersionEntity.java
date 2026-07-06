package com.oriole.wisepen.note.domain.entity;

import com.oriole.wisepen.note.api.domain.base.NoteVersionBase;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.bson.types.Binary;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "note_versions")
@CompoundIndex(name = "idx_resource_version", def = "{'resourceId': 1, 'version': 1}", unique = true)
public class NoteVersionEntity extends NoteVersionBase {
    @Id
    private String id;

    private String resourceId;

    private Integer version;

    private Binary data;

    @CreatedDate
    private LocalDateTime createTime;
}
