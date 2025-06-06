package org.myteam.server.comment.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CommentType {
    ALL(null),
    BOARD(BoardComment.class),
    IMPROVEMENT(ImprovementComment.class),
    INQUIRY(InquiryComment.class),
    NEWS(NewsComment.class),
    NOTICE(NoticeComment.class),
    MATCH(MatchComment.class);

    private final Class<? extends Comment> entityClass;

    public Class<? extends Comment> getEntityClass() {
        return entityClass;
    }
}