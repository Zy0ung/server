package org.myteam.server.board.repository;

import static java.util.Optional.ofNullable;
import static org.myteam.server.board.domain.QBoard.board;
import static org.myteam.server.board.domain.QBoardCount.boardCount;
import static org.myteam.server.comment.domain.QBoardComment.boardComment;
import static org.myteam.server.member.entity.QMember.member;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.myteam.server.board.domain.BoardOrderType;
import org.myteam.server.board.domain.BoardSearchType;
import org.myteam.server.board.domain.CategoryType;
import org.myteam.server.board.dto.reponse.BoardDto;
import org.myteam.server.board.dto.reponse.BoardRankingDto;
import org.myteam.server.board.dto.reponse.CommentSearchDto;
import org.myteam.server.board.util.RedisBoardRankingReader;
import org.myteam.server.comment.domain.CommentType;
import org.myteam.server.comment.domain.QBoardComment;
import org.myteam.server.comment.domain.QComment;
import org.myteam.server.global.domain.Category;
import org.myteam.server.global.util.domain.TimePeriod;
import org.myteam.server.global.util.redis.CommonCountDto;
import org.myteam.server.global.util.redis.service.RedisCountService;
import org.myteam.server.global.util.redis.ServiceType;
import org.myteam.server.home.dto.HotBoardDto;
import org.myteam.server.home.dto.NewBoardDto;
import org.myteam.server.report.domain.DomainType;
import org.myteam.server.util.ClientUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class BoardQueryRepository {

    private final JPAQueryFactory queryFactory;
    private final RedisBoardRankingReader rankingReader;
    private final RedisCountService redisCountService;

    /**
     * 게시글 목록 조회
     */
    public Page<BoardDto> getBoardList(Category boardType, CategoryType categoryType,
                                       BoardOrderType orderType,
                                       BoardSearchType searchType, String search, Pageable pageable) {

        List<BoardDto> content = queryFactory
                .selectDistinct(Projections.constructor(BoardDto.class,
                        board.boardType,
                        board.categoryType,
                        board.id,
                        board.id.in(getHotBoardIdList()).as("isHot"),
                        board.id.in(getNewBoardIdList()).as("isNew"),
                        board.title,
                        board.createdIp,
                        board.thumbnail,
                        member.publicId,
                        member.nickname,
                        boardCount.commentCount,
                        boardCount.recommendCount,
                        board.createDate,
                        board.lastModifiedDate
                ))
                .from(board)
                .join(boardCount).on(boardCount.board.id.eq(board.id))
                .join(member).on(member.eq(board.member))
                .fetchJoin()
                .where(isBoardTypeEqualTo(boardType), isCategoryEqualTo(categoryType),
                        isSearchTypeLikeTo(searchType, search))
                .orderBy(isOrderByEqualToOrderCategory(orderType))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        long total = getTotalBoardCount(boardType, categoryType, searchType, search);

        for (BoardDto boardDto : content) {
            CommonCountDto commonCountDto = redisCountService.getCommonCount(ServiceType.CHECK, DomainType.BOARD,
                    boardDto.getId(), null);
            boardDto.setCommentCount(commonCountDto.getCommentCount());
            boardDto.setRecommendCount(commonCountDto.getRecommendCount());
        }

        // searchType이 COMMENT일 경우, 댓글 데이터 추가
        if (searchType == BoardSearchType.COMMENT) {
            content.forEach(boardDto -> {
                CommentSearchDto commentSearch = getSearchBoardComment(boardDto.getId(), search);
                boardDto.setBoardCommentSearchList(commentSearch);
            });
        }

        return new PageImpl<>(content, pageable, total);
    }

    public Page<BoardDto> getTotalList(TimePeriod timePeriod, BoardOrderType orderType,
                                       BoardSearchType searchType, String search, Pageable pageable) {
        List<BoardDto> content = queryFactory
                .selectDistinct(Projections.constructor(BoardDto.class,
                        board.boardType,
                        board.categoryType,
                        board.id,
                        board.id.in(getHotBoardIdList()).as("isHot"),
                        board.id.in(getNewBoardIdList()).as("isNew"),
                        board.title,
                        board.createdIp,
                        board.thumbnail,
                        member.publicId,
                        member.nickname,
                        boardCount.commentCount,
                        boardCount.recommendCount,
                        board.createDate,
                        board.lastModifiedDate
                ))
                .from(board)
                .join(boardCount).on(boardCount.board.id.eq(board.id))
                .join(member).on(member.eq(board.member))
                .fetchJoin()
                .where(
                        isSearchTypeLikeTo(searchType, search),
                        isCreatedAfterByTimePeriod(timePeriod)
                )
                .orderBy(isOrderByEqualToOrderCategory(orderType))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        long total = getTotalBoardCount(timePeriod, searchType, search);

        for (BoardDto dto : content) {
            dto.setCreatedIp(dto.getCreatedIp());
        }

        if (searchType == BoardSearchType.COMMENT) {
            content.forEach(boardDto -> {
                CommentSearchDto commentSearch = getSearchBoardComment(boardDto.getId(), search);
                boardDto.setBoardCommentSearchList(commentSearch);
            });
        }

        return new PageImpl<>(content, pageable, total);
    }

    private BooleanExpression isCreatedAfterByTimePeriod(TimePeriod timePeriod) {
        LocalDateTime start = timePeriod.getStartDateByTimePeriod(timePeriod);
        return start != null ? board.createDate.after(start) : null;
    }

    private CommentSearchDto getSearchBoardComment(Long boardId, String search) {
        JPQLQuery<CommentSearchDto> query = queryFactory
                .select(Projections.fields(CommentSearchDto.class,
                        boardComment.id.as("commentId"),
                        boardComment.comment,
                        boardComment.imageUrl
                ))
                .from(boardComment)
                .where(boardComment.board.id.eq(boardId));

        // 검색어가 있을 경우 해당 검색어를 포함하는 댓글만 조회
        if (search != null && !search.isEmpty()) {
            query.where(boardComment.comment.like("%" + search + "%"));
        }

        return query.orderBy(boardComment.createDate.desc(), boardComment.comment.asc())
                .fetchFirst();
    }


    private BooleanExpression isSearchTypeLikeTo(BoardSearchType searchType, String search) {
        if (searchType == null) {
            return null;
        }

        return switch (searchType) {
            case TITLE -> board.title.like("%" + search + "%");
            case CONTENT -> board.content.like("%" + search + "%");
            case TITLE_CONTENT -> board.title.like("%" + search + "%")
                    .or(board.content.like("%" + search + "%"));
            case NICKNAME -> board.member.nickname.like("%" + search + "%");
            case COMMENT -> JPAExpressions.selectOne()
                    .from(QComment.comment1)
                    .where(
                            QComment.comment1.comment.like("%" + search + "%")
                                    .and(QComment.comment1.commentType.eq(CommentType.BOARD))
                                    .and(QComment.comment1.as(QBoardComment.class).board.id.eq(
                                            board.id))
                    )
                    .exists();
            default -> null;
        };
    }

    private long getTotalBoardCount(Category boardType, CategoryType categoryType, BoardSearchType searchType,
                                    String search) {
        return ofNullable(
                queryFactory
                        .select(board.countDistinct())
                        .from(board)
                        .where(isBoardTypeEqualTo(boardType), isCategoryEqualTo(categoryType),
                                isSearchTypeLikeTo(searchType, search))
                        .fetchOne()
        ).orElse(0L);
    }

    private long getTotalBoardCount(TimePeriod timePeriod, BoardSearchType searchType, String search) {
        return ofNullable(
                queryFactory
                        .select(board.countDistinct())
                        .from(board)
                        .where(
                                isSearchTypeLikeTo(searchType, search),
                                isCreatedAfterByTimePeriod(timePeriod)
                        )
                        .fetchOne()
        ).orElse(0L);
    }

    private OrderSpecifier<?>[] isOrderByEqualToOrderCategory(BoardOrderType orderType) {
        // default 최신순
        BoardOrderType boardOrderType = Optional.ofNullable(orderType).orElse(BoardOrderType.CREATE);
        return switch (boardOrderType) {
            case CREATE -> new OrderSpecifier<?>[]{board.createDate.desc(), board.title.asc(), board.id.desc()};
            case RECOMMEND -> new OrderSpecifier<?>[]{boardCount.recommendCount.desc(),
                    boardCount.commentCount.add(boardCount.viewCount).desc(), board.title.asc(), board.id.desc()};
            case COMMENT -> new OrderSpecifier<?>[]{boardCount.commentCount.desc(), board.title.asc(), board.id.desc()};
        };
    }

    private BooleanExpression isCategoryEqualTo(CategoryType categoryType) {
        return categoryType != null ? board.categoryType.eq(categoryType) : null;
    }

    private BooleanExpression isBoardTypeEqualTo(Category boardType) {
        return boardType != null ? board.boardType.eq(boardType) : null;
    }

    /**
     * 내가 쓴 게시글 목록 조회
     */
    public Page<BoardDto> getMyBoardList(BoardOrderType orderType, BoardSearchType searchType, String search,
                                         Pageable pageable, UUID publicId) {

        List<BoardDto> content = queryFactory
                .selectDistinct(Projections.constructor(BoardDto.class,
                        board.boardType,
                        board.categoryType,
                        board.id,
                        board.id.in(getHotBoardIdList()).as("isHot"),
                        board.id.in(getNewBoardIdList()).as("isNew"),
                        board.title,
                        board.createdIp,
                        board.thumbnail,
                        member.publicId,
                        member.nickname,
                        boardCount.commentCount,
                        boardCount.recommendCount,
                        board.createDate,
                        board.lastModifiedDate
                ))
                .from(board)
                .join(boardCount).on(boardCount.board.id.eq(board.id))
                .join(member).on(member.eq(board.member))
                .fetchJoin()
                .where(member.publicId.eq(publicId), isSearchTypeLikeTo(searchType, search))
                .orderBy(isOrderByEqualToOrderCategory(orderType))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        long total = getTotalMyBoardCount(searchType, search, publicId);

        content.forEach(boardDto -> {
            boardDto.setCreatedIp(ClientUtils.maskIp(boardDto.getCreatedIp()));
        });

        // searchType이 COMMENT일 경우, 댓글 데이터 추가
        if (searchType == BoardSearchType.COMMENT) {
            content.forEach(boardDto -> {
                CommentSearchDto commentSearch = getSearchBoardComment(boardDto.getId(), search);
                boardDto.setBoardCommentSearchList(commentSearch);
            });
        }

        log.info("검색 완료 total: {}", total);

        return new PageImpl<>(content, pageable, total);
    }

    public int getMyBoard(UUID memberPublicId) {
        log.info("publicID: {} 포스트 수 조회", memberPublicId);
        return queryFactory
                .select(board.count())
                .from(board)
                .where(board.member.publicId.eq(memberPublicId))
                .fetchOne()
                .intValue();
    }

    /**
     * 내가 쓴 게시글 총 개수
     */
    private long getTotalMyBoardCount(BoardSearchType searchType, String search, UUID publicId) {
        return ofNullable(
                queryFactory
                        .select(board.countDistinct())
                        .from(board)
                        .join(member).on(member.eq(board.member))
                        .where(member.publicId.eq(publicId), isSearchTypeLikeTo(searchType, search))
                        .fetchOne()
        ).orElse(0L);
    }

    /**
     * 실시간 최신 게시글 ID 목록 조회
     */
    private List<Long> getNewBoardIdList() {
        // 전체 게시글 기준 생성일 내림차순으로 최신 10개 가져오기
        List<Long> boards = queryFactory
                .select(board.id)
                .from(board)
                .orderBy(board.createDate.desc(), board.id.desc())
                .limit(10)
                .fetch();

        return boards;
    }

    /**
     * 핫 게시글 ID 목록 조회
     */
    private List<Long> getHotBoardIdList() {
        // 전체 게시글 기준 추천순 내림차순 -> 조회수 + 댓글수 내림차순 -> 제목 오름차순 -> id 오름차순

        List<Tuple> boards = queryFactory
                .select(
                        board.id,
                        board.title
                )
                .from(board)
                .fetch();

        List<BoardRankingDto> rankingList = boards.stream()
                .map(tuple -> {
                    Long boardId = tuple.get(board.id);
                    String title = tuple.get(board.title);

                    // Redis에서 모든 카운트 조회
                    CommonCountDto countDto = redisCountService.getCommonCount(
                            ServiceType.CHECK, DomainType.BOARD, boardId, null);

                    int viewCount = countDto.getViewCount();
                    int recommendCount = countDto.getRecommendCount();
                    int commentCount = countDto.getCommentCount();
                    int totalScore = viewCount + commentCount;

                    // BoardRankingDto 생성
                    return new BoardRankingDto(boardId, viewCount, recommendCount, commentCount, title, totalScore);
                })
                .sorted((a, b) -> {
                    // 1. 추천수 비교 (내림차순)
                    int recommendCompare = Integer.compare(b.getRecommendCount(), a.getRecommendCount());
                    if (recommendCompare != 0) {
                        return recommendCompare;
                    }

                    // 2. 조회수 + 댓글수 비교 (내림차순)
                    int totalScoreCompare = Integer.compare(b.getTotalScore(), a.getTotalScore());
                    if (totalScoreCompare != 0) {
                        return totalScoreCompare;
                    }

                    // 3. 제목 비교 (오름차순)
                    int titleCompare = a.getTitle().compareTo(b.getTitle());
                    if (titleCompare != 0) {
                        return titleCompare;
                    }

                    // 4. ID 비교 (오름차순)
                    return Long.compare(a.getId(), b.getId());
                })
                .limit(10) // 최대 10개로 제한
                .collect(Collectors.toList());

        // 최종 정렬된 ID 리스트 반환
        return rankingList.stream()
                .map(BoardRankingDto::getId)
                .collect(Collectors.toList());
    }

    /**
     * 실시간 최신 게시글 목록
     */
    public List<NewBoardDto> getNewBoardList() {
        return queryFactory
                .select(Projections.fields(NewBoardDto.class,
                        board.id,
                        board.boardType,
                        board.categoryType,
                        board.title,
                        boardCount.commentCount,
                        board.id.in(getHotBoardIdList()).as("isHot"),
                        board.id.in(getNewBoardIdList()).as("isNew"),
                        new CaseBuilder()
                                .when(board.thumbnail.isNotEmpty()).then(true)
                                .otherwise(false)
                                .as("isImage")
                ))
                .from(board)
                .join(boardCount).on(boardCount.board.id.eq(board.id))
                .orderBy(
                        board.createDate.desc(),
                        board.title.asc(),
                        board.id.asc()
                )
                .limit(10)
                .fetch();
    }

    /**
     * 실시간 HOT 게시글 목록
     * <p>
     * 추천수가 제일많은 게시글 (추천수 동일시 조회수+댓글수, 조회수+댓글수도 동일할 경우 가나다 순)
     */
    public List<HotBoardDto> getHotBoardList() {

        //  Redis 기준으로 정렬된 핫 게시글 ID 조회
        List<Long> hotIds = getHotBoardIdList();

        List<HotBoardDto> hotBoardList = queryFactory
                .select(Projections.fields(HotBoardDto.class,
                        board.boardType,
                        board.categoryType,
                        board.id,
                        board.title,
                        board.id.in(hotIds).as("isHot"),
                        board.id.in(getNewBoardIdList()).as("isNew"),
                        new CaseBuilder()
                                .when(board.thumbnail.isNotEmpty()).then(true)
                                .otherwise(false)
                                .as("isImage")
                ))
                .from(board)
                .where(board.id.in(hotIds))
                .fetch();

        // 댓글 수 설정 (Redis에서 조회)
        hotBoardList.forEach(dto -> {
            CommonCountDto countDto = redisCountService.getCommonCount(
                    ServiceType.CHECK, DomainType.BOARD, dto.getId(), null);
            dto.setCommentCount(countDto != null ? countDto.getCommentCount() : 0);
        });

        // ID 순서 맞추기
        Map<Long, Integer> orderMap = IntStream.range(0, hotIds.size())
                .boxed()
                .collect(Collectors.toMap(hotIds::get, i -> i));

        hotBoardList.sort(Comparator.comparingInt(dto -> orderMap.getOrDefault(dto.getId(), Integer.MAX_VALUE)));

        // 순위 부여
        AtomicInteger rankCounter = new AtomicInteger(1);
        hotBoardList.forEach(dto -> dto.setRank(rankCounter.getAndIncrement()));

        return hotBoardList;
    }

    public Long findPreviousBoardId(Long boardId, Category boardType, CategoryType categoryType) {
        return queryFactory
                .select(board.id)
                .from(board)
                .where(board.id.lt(boardId), // 현재 게시글보다 작은 ID
                        board.boardType.eq(boardType),
                        board.categoryType.eq(categoryType))
                .orderBy(board.id.desc()) // 가장 큰 ID (즉, 이전 글)
                .limit(1)
                .fetchOne();
    }

    public Long findNextBoardId(Long boardId, Category boardType, CategoryType categoryType) {
        return queryFactory
                .select(board.id)
                .from(board)
                .where(board.id.gt(boardId), // 현재 게시글보다 큰 ID
                        board.boardType.eq(boardType),
                        board.categoryType.eq(categoryType))
                .orderBy(board.id.asc()) // 가장 작은 ID (즉, 다음 글)
                .limit(1)
                .fetchOne();
    }
}