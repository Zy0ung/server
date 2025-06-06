package org.myteam.server.match.match.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.myteam.server.global.exception.ErrorCode;
import org.myteam.server.global.exception.PlayHiveException;
import org.myteam.server.match.match.domain.Match;
import org.myteam.server.match.match.domain.MatchCategory;
import org.myteam.server.match.match.dto.service.response.MatchEsportsScheduleResponse;
import org.myteam.server.match.match.dto.service.response.MatchEsportsYoutubeResponse;
import org.myteam.server.match.match.dto.service.response.MatchResponse;
import org.myteam.server.match.match.dto.service.response.MatchScheduleListResponse;
import org.myteam.server.match.match.repository.MatchQueryRepository;
import org.myteam.server.match.match.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchReadService {

	private final MatchQueryRepository matchQueryRepository;
	private final MatchRepository matchRepository;
	private final MatchYoutubeService matchYoutubeService;

	public Match findById(Long id) {
		return matchRepository.findById(id)
			.orElseThrow(() -> new PlayHiveException(ErrorCode.MATCH_NOT_FOUND));
	}

	@SuppressWarnings("unchecked")
	public <T> T findSchedulesBetweenDate(MatchCategory matchCategory) {
		LocalDate today = LocalDate.now();
		LocalDateTime startOfDay = LocalDateTime.now();
		LocalDateTime endTime = today.plusWeeks(1).atTime(LocalTime.of(6, 0));

		// ESPORTS 카테고리의 경우
		if (matchCategory.equals(MatchCategory.ESPORTS)) {
			return (T)matchQueryRepository.findEsportsSchedulesBetweenDate(startOfDay, endTime);
		}

		// 그 외 카테고리의 경우
		return (T) MatchScheduleListResponse.createResponse(
			matchQueryRepository.findSchedulesBetweenDate(startOfDay, endTime, matchCategory)
				.stream()
				.map(MatchResponse::createResponse)
				.toList());
	}

	public MatchResponse findOne(Long id) {
		return MatchResponse.createResponse(findById(id));
	}

	public MatchEsportsYoutubeResponse confirmEsportsYoutube() {
		LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
		LocalDateTime startDate = matchRepository.findMostRecentMatchStartTime(today, MatchCategory.ESPORTS);
		LocalDateTime todayDate = LocalDateTime.now();

		if (startDate == null || startDate.minusHours(1).isAfter(todayDate)) {
			return MatchEsportsYoutubeResponse.createResponse(false, null);
		}
		String videoId = matchYoutubeService.getVideoId();
		return MatchEsportsYoutubeResponse.createResponse(videoId != null, videoId);
	}

}
