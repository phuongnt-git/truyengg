package com.truyengg.model.response;

import java.util.List;

public record RankingResponse(
    String type,
    List<ComicResponse> comics,
    Long total
) {
}
