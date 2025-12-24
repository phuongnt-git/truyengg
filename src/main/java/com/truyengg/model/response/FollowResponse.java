package com.truyengg.model.response;

public record FollowResponse(
    Long comicId,
    Boolean isFollowing,
    Long followCount
) {
}
