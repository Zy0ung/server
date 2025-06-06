package org.myteam.server.comment.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.myteam.server.comment.domain.Comment;
import org.myteam.server.comment.domain.CommentType;
import org.myteam.server.comment.dto.request.CommentRequest.CommentListRequest;
import org.myteam.server.comment.dto.response.CommentResponse.BestCommentResponse;
import org.myteam.server.comment.dto.response.CommentResponse.BestCommentSaveListResponse;
import org.myteam.server.comment.dto.response.CommentResponse.CommentSaveListResponse;
import org.myteam.server.comment.dto.response.CommentResponse.CommentSaveResponse;
import org.myteam.server.comment.repository.CommentQueryRepository;
import org.myteam.server.comment.repository.CommentRepository;
import org.myteam.server.global.exception.ErrorCode;
import org.myteam.server.global.exception.PlayHiveException;
import org.myteam.server.global.page.response.PageCustomResponse;
import org.myteam.server.member.entity.Member;
import org.myteam.server.member.service.SecurityReadService;
import org.myteam.server.mypage.dto.request.MyCommentServiceRequest;
import org.myteam.server.mypage.dto.response.MyCommentDto;
import org.myteam.server.mypage.dto.response.MyCommentListResponse;
import org.myteam.server.util.ClientUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentReadService {

    private final CommentRepository commentRepository;
    private final CommentQueryRepository commentQueryRepository;
    private final SecurityReadService securityReadService;
    private final CommentRecommendReadService commentRecommendReadService;

    public Comment findById(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new PlayHiveException(ErrorCode.COMMENT_NOT_FOUND));
    }

    public Comment findByIdAndCommentType(Long commentId, CommentType commentType) {
        return commentRepository.findByIdAndCommentType(commentId, commentType)
                .orElseThrow(() -> new PlayHiveException(ErrorCode.COMMENT_NOT_FOUND));
    }

    public CommentSaveListResponse getComments(Long contentId, CommentListRequest request) {
        log.info("댓글 목록 조회 요청 - type: {}, contentId: {}, page: {}, size: {}",
                request.getType(), contentId,
                request.getPage(), request.getSize());

        UUID loginUser = securityReadService.getAuthenticatedPublicId();
        Page<CommentSaveResponse> list = commentQueryRepository.getCommentList(
                request.getType(),
                contentId,
                request.toServiceRequest().toPageable(),
                loginUser
        );

        log.info("댓글 목록 조회 완료 - contentId: {}, 조회된 댓글 수: {}", contentId, list);

        return CommentSaveListResponse.createResponse(PageCustomResponse.of(list));
    }

    public CommentSaveResponse getCommentDetail(Long commentId) {
        log.info("댓글 상세 조회 요청 - commentId: {}", commentId);

        Comment comment = findById(commentId);
        UUID loginUser = securityReadService.getAuthenticatedPublicId();

        CommentSaveResponse response = CommentSaveResponse.createResponse(comment, false);
        commentQueryRepository.getCommentReply(response, loginUser);

        log.info("댓글 상세 조회 완료 - commentId: {}, 작성자: {}, 추천수: {}",
                response.getCommentId(), response.getNickname(), response.getRecommendCount());

        return response;
    }

    public BestCommentSaveListResponse getBestComments(Long contentId, CommentListRequest request) {
        log.info("베스트 댓글 목록 조회 요청 - type: {}, contentId: {}, page: {}, size: {}",
                request.getType(), contentId,
                request.getPage(), request.getSize());

        Page<BestCommentResponse> list = commentQueryRepository.getBestCommentList(
                request.getType(),
                contentId,
                request.toServiceRequest().toPageable()
        );

        UUID loginUser = securityReadService.getAuthenticatedPublicId();
        for (BestCommentResponse response : list) {
            response.setCreatedIp(ClientUtils.maskIp(response.getCreatedIp()));
            if (loginUser != null) {
                boolean isRecommend = commentRecommendReadService.isRecommended(response.getCommentId(),
                        loginUser);
                response.setRecommended(isRecommend);
            }
        }

        log.info("베스트 댓글 목록 조회 완료 - contentId: {}, 조회된 댓글 수: {}", contentId);

        return BestCommentSaveListResponse.createResponse(PageCustomResponse.of(list));
    }

    /**
     * 내가 쓴 댓글 목록 조회
     */
    public MyCommentListResponse getMyCommentList(MyCommentServiceRequest request) {
        Member member = securityReadService.getMember();
        UUID loginUser = member.getPublicId();
        Page<MyCommentDto> list = commentQueryRepository.getMyCommentList(
                loginUser,
                request.getCommentType(),
                request.getOrderType(),
                request.getSearchType(),
                request.getSearch(),
                request.toPageable()
        );

        return MyCommentListResponse.createResponse(PageCustomResponse.of(list));
    }

    public long getMyCommentCount() {
        UUID publicId = securityReadService.getMember().getPublicId();
        return commentRepository.countByMemberPublicId(publicId);
    }

    public boolean existsById(Long commentId) {
        return commentRepository.existsById(commentId);
    }
}
