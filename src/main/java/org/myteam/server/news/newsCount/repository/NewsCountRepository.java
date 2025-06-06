package org.myteam.server.news.newsCount.repository;

import io.lettuce.core.dynamic.annotation.Param;
import java.util.Optional;
import org.myteam.server.news.newsCount.domain.NewsCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface NewsCountRepository extends JpaRepository<NewsCount, Long> {
    Optional<NewsCount> findByNewsId(Long newsId);

    @Modifying
    @Query("UPDATE p_news_count n SET n.viewCount = :viewCount WHERE n.id = :newsId")
    void updateViewCount(@Param("newsId") Long newsId, @Param("viewCount") int viewCount);

    @Transactional
    @Modifying
    @Query(value = "UPDATE p_news_count SET view_count = :view, comment_count = :comment, recommend_count = :recommend "
            + "WHERE news_id = :newsId", nativeQuery = true)
    void updateAllCounts(@Param("newsId") Long newsId,
                         @Param("view") int view,
                         @Param("comment") int comment,
                         @Param("recommend") int recommend);
}